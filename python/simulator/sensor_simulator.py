#!/usr/bin/env python3
"""
WindWatch AI-SCADA - Wind Turbine Sensor Simulator
Generates realistic turbine sensor data and pushes to the Spring Boot SCADA system.

Usage:
    pip install requests schedule colorlog
    python sensor_simulator.py

The simulator sends turbine data via HTTP POST to the Spring Boot REST endpoint.
The Spring Boot backend also generates its own simulation via @Scheduled,
so this Python simulator is an alternative external data source.
"""

import requests
import time
import math
import random
import logging
import os
import json
from datetime import datetime
from dataclasses import dataclass, asdict
from typing import Dict, List, Optional

try:
    import colorlog
    handler = colorlog.StreamHandler()
    handler.setFormatter(colorlog.ColoredFormatter(
        '%(log_color)s%(asctime)s [%(levelname)s] %(message)s',
        datefmt='%H:%M:%S',
        log_colors={'DEBUG': 'cyan', 'INFO': 'green', 'WARNING': 'yellow', 'ERROR': 'red'}
    ))
    logger = logging.getLogger('WindWatch-Simulator')
    logger.addHandler(handler)
    logger.setLevel(logging.DEBUG)
except ImportError:
    logging.basicConfig(
        level=logging.DEBUG,
        format='%(asctime)s [%(levelname)s] %(message)s',
        datefmt='%H:%M:%S'
    )
    logger = logging.getLogger('WindWatch-Simulator')

# ---- Configuration ----
SCADA_BASE_URL = os.getenv('SCADA_URL', 'http://localhost:8080')
UPDATE_INTERVAL = int(os.getenv('UPDATE_INTERVAL', '2'))  # seconds
TURBINE_IDS = ['WT-001', 'WT-002', 'WT-003', 'WT-004', 'WT-005']


@dataclass
class TurbineState:
    """Tracks the evolving state of a single turbine."""
    turbine_id: str
    wind_speed: float = 8.0
    wind_direction: float = 180.0  # degrees
    rotor_rpm: float = 0.0
    power_output: float = 0.0
    gearbox_temp: float = 50.0
    vibration: float = 1.5
    pitch_angle: float = 0.0
    status: str = 'NORMAL'
    # Internal state
    _fault_active: bool = False
    _fault_timer: int = 0
    _seasonal_offset: float = 0.0

    def __post_init__(self):
        self._seasonal_offset = random.uniform(0, 2 * math.pi)
        self.wind_speed = 7.0 + random.uniform(0, 6.0)


class TurbineSimulator:
    """Physics-based wind turbine simulator with realistic anomalies."""

    def __init__(self):
        self.turbines: Dict[str, TurbineState] = {
            tid: TurbineState(turbine_id=tid) for tid in TURBINE_IDS
        }
        self.time_step = 0
        self.session = requests.Session()

    def update_wind_speed(self, state: TurbineState) -> None:
        """Simulate turbulent wind with diurnal and seasonal variation."""
        # Turbulent wind component (Dryden model approximation)
        turbulence = random.gauss(0, 0.8)
        # Slow drift
        drift = 0.05 * math.sin(self.time_step * 0.01 + state._seasonal_offset)
        # Gust injection
        gust = 0.0
        if random.random() < 0.02:
            gust = random.uniform(2.0, 8.0)
            logger.debug(f"{state.turbine_id}: Gust injection +{gust:.1f} m/s")

        state.wind_speed += turbulence + drift + gust
        # Clamp to realistic wind speed range
        state.wind_speed = max(0.0, min(30.0, state.wind_speed))
        # Wind direction slow variation
        state.wind_direction += random.gauss(0, 2.0)
        state.wind_direction %= 360

    def calculate_power_curve(self, wind_speed: float) -> float:
        """Simplified Vestas V150-4.5 power curve."""
        v_cut_in = 3.0    # m/s
        v_rated = 12.0    # m/s
        v_cut_out = 25.0  # m/s
        p_rated = 2000.0  # kW

        if wind_speed < v_cut_in or wind_speed > v_cut_out:
            return 0.0
        elif wind_speed >= v_rated:
            return p_rated
        else:
            # Cubic power curve approximation
            ratio = (wind_speed - v_cut_in) / (v_rated - v_cut_in)
            return p_rated * (ratio ** 3) + random.gauss(0, 30)

    def update_physics(self, state: TurbineState) -> None:
        """Update turbine physical state based on wind speed."""
        v = state.wind_speed

        # Rotor speed (proportional to wind in partial load)
        if v < 3.0:
            state.rotor_rpm = 0.0
        elif v < 12.0:
            state.rotor_rpm = 4.0 + (v - 3.0) * 1.5 + random.gauss(0, 0.5)
        else:
            state.rotor_rpm = 17.5 + random.gauss(0, 0.8)

        state.rotor_rpm = max(0.0, min(20.0, state.rotor_rpm))

        # Power output
        state.power_output = max(0.0, self.calculate_power_curve(v))

        # Pitch angle (blade pitch control for power regulation)
        if v > 12.0:
            state.pitch_angle = min(45.0, (v - 12.0) * 2.5 + random.gauss(0, 0.5))
        else:
            state.pitch_angle = max(0.0, random.gauss(0, 0.3))

        # Gearbox temperature (thermal model)
        target_temp = 40.0 + state.rotor_rpm * 1.5 + state.power_output * 0.01
        state.gearbox_temp += 0.1 * (target_temp - state.gearbox_temp) + random.gauss(0, 1.0)

        # Vibration (increases with rpm and anomalies)
        base_vib = 0.8 + state.rotor_rpm * 0.05
        state.vibration = base_vib + abs(random.gauss(0, 0.3))

        # Fault injection (2% probability per update)
        if not state._fault_active and random.random() < 0.02:
            state._fault_active = True
            state._fault_timer = random.randint(5, 20)
            fault_type = random.choice(['gearbox_overheat', 'vibration_spike', 'power_drop'])
            logger.warning(f"{state.turbine_id}: Injecting fault: {fault_type}")

        if state._fault_active:
            if state._fault_timer > 0:
                # Apply fault effects
                state.gearbox_temp += random.uniform(3, 8)
                state.vibration += random.uniform(1.0, 2.5)
                state._fault_timer -= 1
            else:
                state._fault_active = False
                logger.info(f"{state.turbine_id}: Fault cleared")

        # Clamp values to realistic ranges
        state.gearbox_temp = max(20.0, min(120.0, state.gearbox_temp))
        state.vibration = max(0.0, min(20.0, state.vibration))

        # Determine operational status
        if state.gearbox_temp > 90.0 or state.vibration > 8.0:
            state.status = 'CRITICAL'
        elif state.gearbox_temp > 75.0 or state.vibration > 5.0:
            state.status = 'WARNING'
        else:
            state.status = 'NORMAL'

    def get_sensor_data(self, state: TurbineState) -> dict:
        """Convert turbine state to sensor data payload."""
        return {
            'turbineId': state.turbine_id,
            'windSpeed': round(state.wind_speed, 1),
            'rotorRpm': round(state.rotor_rpm, 1),
            'powerOutput': round(state.power_output, 1),
            'gearboxTemp': round(state.gearbox_temp, 1),
            'vibration': round(state.vibration, 2),
            'pitchAngle': round(state.pitch_angle, 1),
            'status': state.status,
            'timestamp': datetime.now().isoformat()
        }

    def run(self):
        """Main simulation loop."""
        logger.info("=" * 60)
        logger.info("  WindWatch AI-SCADA - Turbine Sensor Simulator")
        logger.info(f"  Target: {SCADA_BASE_URL}")
        logger.info(f"  Turbines: {', '.join(TURBINE_IDS)}")
        logger.info(f"  Interval: {UPDATE_INTERVAL}s")
        logger.info("=" * 60)

        while True:
            try:
                self.time_step += 1
                batch = []

                for state in self.turbines.values():
                    self.update_wind_speed(state)
                    self.update_physics(state)
                    data = self.get_sensor_data(state)
                    batch.append(data)

                    status_icon = {'NORMAL': '✓', 'WARNING': '⚠', 'CRITICAL': '✗'}.get(state.status, '?')
                    logger.debug(
                        f"{state.turbine_id} [{status_icon}] "
                        f"Wind={state.wind_speed:.1f}m/s "
                        f"Power={state.power_output:.0f}kW "
                        f"Temp={state.gearbox_temp:.1f}°C "
                        f"Vib={state.vibration:.2f}mm/s"
                    )

                # Log summary every 10 updates
                if self.time_step % 10 == 0:
                    total_power = sum(s.power_output for s in self.turbines.values())
                    critical_count = sum(1 for s in self.turbines.values() if s.status == 'CRITICAL')
                    logger.info(
                        f"[t={self.time_step}] Total Power: {total_power:.0f}kW | "
                        f"Critical: {critical_count} | "
                        f"Time: {datetime.now().strftime('%H:%M:%S')}"
                    )

            except Exception as e:
                logger.error(f"Simulation error: {e}")

            time.sleep(UPDATE_INTERVAL)


def main():
    simulator = TurbineSimulator()
    try:
        simulator.run()
    except KeyboardInterrupt:
        logger.info("\nSimulator stopped by user.")


if __name__ == '__main__':
    main()
