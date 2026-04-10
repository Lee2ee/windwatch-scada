package com.windwatch.scada.service;

import com.windwatch.scada.dto.TurbineDataDto;
import com.windwatch.scada.model.ScadaEvent;
import com.windwatch.scada.model.Turbine;
import com.windwatch.scada.model.TurbineData;
import com.windwatch.scada.repository.ScadaEventRepository;
import com.windwatch.scada.repository.TurbineDataRepository;
import com.windwatch.scada.repository.TurbineRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 풍력 터빈 실시간 데이터 생성 서비스
 *
 * 시뮬레이션 모델:
 * - 풍속: Ornstein-Uhlenbeck 확률 과정 (평균 회귀형 랜덤 워크)
 * - 발전량: 실제 파워 커브 (컷인 3 m/s, 정격 12 m/s, 컷아웃 25 m/s)
 * - 기어박스 온도: 열 모델 (부하 비율에 따른 목표 온도 + 지수 수렴)
 * - 진동: RPM 비례 기저값 + 결함 진행에 따른 증가
 * - 결함: 상태 머신 (NORMAL → FAULT onset → 점진적 악화 → 자동 해소)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TurbineService {

    private final TurbineDataRepository turbineDataRepository;
    private final TurbineRepository turbineRepository;
    private final ScadaEventRepository scadaEventRepository;
    private final SimpMessagingTemplate messagingTemplate;

    private final Random rng = new Random();

    // 터빈별 시뮬레이션 상태 (메모리 내 유지)
    private static class SimState {
        double windSpeed;
        double meanWindSpeed;   // 사이트별 평균 풍속 (고정)
        double gearboxTemp = 42.0;
        double vibration = 1.3;
        boolean faultActive = false;
        String faultType = null; // "OVERTEMP" | "VIBRATION"
        int faultCycles = 0;
        int faultMaxCycles = 150;
    }

    private final Map<String, SimState> simStates = new ConcurrentHashMap<>();
    private final Map<String, String> prevStatusMap = new ConcurrentHashMap<>();

    // 활성 터빈 캐시 (10초 TTL — 관리자 변경 후 빠르게 반영)
    private volatile List<Turbine> activeTurbineCache = new ArrayList<>();
    private volatile long lastCacheRefreshMs = 0L;
    private static final long CACHE_TTL_MS = 10_000L;

    // ------------------------------------------------------------------ //
    //  Scheduled: 2초마다 데이터 생성 & WebSocket 발행
    // ------------------------------------------------------------------ //
    @Scheduled(fixedRate = 2000)
    public void generateAndPublish() {
        List<Turbine> turbines = getActiveTurbines();
        if (turbines.isEmpty()) return;

        List<TurbineDataDto> batch = new ArrayList<>();
        for (Turbine t : turbines) {
            TurbineDataDto dto = simulate(t);
            batch.add(dto);
            persistAndAlarm(dto);
        }
        messagingTemplate.convertAndSend("/topic/turbine-data", batch);
    }

    // ------------------------------------------------------------------ //
    //  현실적 시뮬레이션 로직
    // ------------------------------------------------------------------ //
    private TurbineDataDto simulate(Turbine turbine) {
        String id = turbine.getTurbineId();
        double rated = turbine.getRatedCapacityKw() != null ? turbine.getRatedCapacityKw() : 2000.0;

        SimState s = simStates.computeIfAbsent(id, k -> {
            SimState init = new SimState();
            init.meanWindSpeed = 7.0 + rng.nextDouble() * 4.0; // 7~11 m/s 사이트별 평균
            init.windSpeed = init.meanWindSpeed + rng.nextGaussian() * 1.5;
            init.windSpeed = clamp(init.windSpeed, 0, 28);
            init.gearboxTemp = 40.0 + rng.nextDouble() * 10.0;
            init.vibration = 1.1 + rng.nextDouble() * 0.4;
            return init;
        });

        // 1. 풍속 — Ornstein-Uhlenbeck 과정
        //    dW = θ(μ - W)dt + σ·dB   (θ=0.08, σ=0.9)
        s.windSpeed += 0.08 * (s.meanWindSpeed - s.windSpeed) + 0.9 * rng.nextGaussian();
        s.windSpeed = clamp(s.windSpeed, 0.0, 28.0);
        double wind = s.windSpeed;

        // 2. 운전 가능 여부 (컷인 3 m/s, 컷아웃 25 m/s)
        boolean operational = wind >= 3.0 && wind <= 25.0;

        // 3. 발전량 — 실제 파워 커브
        double power = 0;
        if (operational) {
            if (wind < 12.0) {
                // 컷인~정격: 3차 보간 P = P_rated * ((v³ - v_ci³) / (v_rated³ - v_ci³))
                power = rated * (Math.pow(wind, 3) - 27.0) / (1728.0 - 27.0);
            } else {
                // 정격 이상: 피치 제어로 정격 유지
                power = rated;
            }
            power += rng.nextGaussian() * rated * 0.015; // ±1.5% 노이즈
            power = clamp(power, 0, rated * 1.05);
        }

        // 4. 회전속도 — TSR 기반 (팁 속도비 ≈ 7, 블레이드 반경 ≈ 40m)
        double rpm = 0;
        if (operational) {
            if (wind < 12.0) {
                rpm = wind * 1.45; // 약 4~17 RPM
            } else {
                rpm = 17.5 + rng.nextGaussian() * 0.2; // 정격 RPM 유지
            }
            rpm = clamp(rpm, 0, 22);
        }

        // 5. 결함 상태 머신
        if (!s.faultActive) {
            // 결함 발생: 운전 중일 때만, 약 1/250 확률 (대략 8분에 한 번)
            if (operational && rng.nextInt(250) == 0) {
                s.faultActive = true;
                s.faultType = rng.nextBoolean() ? "OVERTEMP" : "VIBRATION";
                s.faultCycles = 0;
                s.faultMaxCycles = 120 + rng.nextInt(120); // 4~8분 지속
            }
        } else {
            s.faultCycles++;
            if (s.faultCycles >= s.faultMaxCycles) {
                s.faultActive = false;
                s.faultType = null;
                s.faultCycles = 0;
            }
        }

        // 6. 기어박스 온도 — 열 모델
        double loadRatio = rated > 0 ? power / rated : 0;
        double targetTemp = 38.0 + 52.0 * loadRatio; // 38°C 공회전, 90°C 정격 부하
        if (!operational) targetTemp = 35.0 + rng.nextDouble() * 3.0; // 정지 = 냉각
        if (s.faultActive && "OVERTEMP".equals(s.faultType)) {
            // 결함 진행에 따라 점진적으로 온도 상승 (최대 +45°C)
            targetTemp += Math.min(45.0, s.faultCycles * 0.35);
        }
        // 지수 수렴 (시상수 ≈ 17 사이클 = 34초) + 백색 노이즈
        s.gearboxTemp += 0.06 * (targetTemp - s.gearboxTemp) + 0.35 * rng.nextGaussian();
        s.gearboxTemp = clamp(s.gearboxTemp, 18.0, 130.0);

        // 7. 진동 — RPM 비례 + 결함 진행
        double baseVib = operational ? 0.4 + 0.075 * rpm : 0.2;
        if (s.faultActive && "VIBRATION".equals(s.faultType)) {
            baseVib += Math.min(6.0, s.faultCycles * 0.045);
        }
        s.vibration = baseVib + Math.abs(rng.nextGaussian() * 0.25);
        s.vibration = clamp(s.vibration, 0.05, 12.0);

        // 8. 피치 각도 — 정격 초과 구간에서 증가
        double pitch = 0;
        if (operational && wind > 12.0) {
            pitch = Math.min(30.0, (wind - 12.0) * 2.3);
        }

        // 9. 상태 판정
        String status;
        if (s.gearboxTemp > 85.0 || s.vibration > 5.0) {
            status = "CRITICAL";
        } else if (s.gearboxTemp > 75.0 || s.vibration > 3.5 || wind > 22.0) {
            status = "WARNING";
        } else if (!operational) {
            status = "STOPPED"; // 저풍속 또는 고풍속 정지
        } else {
            status = "NORMAL";
        }

        TurbineDataDto dto = new TurbineDataDto();
        dto.setTurbineId(id);
        dto.setWindSpeed(r1(wind));
        dto.setRotorRpm(r1(rpm));
        dto.setPowerOutput(r1(power));
        dto.setGearboxTemp(r1(s.gearboxTemp));
        dto.setVibration(r2(s.vibration));
        dto.setPitchAngle(r1(pitch));
        dto.setStatus(status);
        dto.setRecordedAt(LocalDateTime.now());
        return dto;
    }

    // ------------------------------------------------------------------ //
    //  DB 저장 & 알람 (상태 전이 시점에만 발생)
    // ------------------------------------------------------------------ //
    private void persistAndAlarm(TurbineDataDto dto) {
        // DB 저장
        TurbineData entity = new TurbineData();
        entity.setTurbineId(dto.getTurbineId());
        entity.setWindSpeed(dto.getWindSpeed());
        entity.setRotorRpm(dto.getRotorRpm());
        entity.setPowerOutput(dto.getPowerOutput());
        entity.setGearboxTemp(dto.getGearboxTemp());
        entity.setVibration(dto.getVibration());
        entity.setPitchAngle(dto.getPitchAngle());
        entity.setStatus(dto.getStatus());
        turbineDataRepository.save(entity);

        // 알람: CRITICAL 상태로 전이될 때만 이벤트 생성 (매 2초마다 생성 방지)
        String prev = prevStatusMap.getOrDefault(dto.getTurbineId(), "NORMAL");
        prevStatusMap.put(dto.getTurbineId(), dto.getStatus());

        if ("CRITICAL".equals(dto.getStatus()) && !"CRITICAL".equals(prev)) {
            boolean isTempFault = dto.getGearboxTemp() > 85.0;
            String param = isTempFault ? "gearboxTemp" : "vibration";
            double value = isTempFault ? dto.getGearboxTemp() : dto.getVibration();
            double threshold = isTempFault ? 85.0 : 5.0;
            String msg = isTempFault
                ? dto.getTurbineId() + " 기어박스 온도 임계치 초과 (" + value + "°C)"
                : dto.getTurbineId() + " 진동 임계치 초과 (" + value + " mm/s)";

            ScadaEvent event = new ScadaEvent();
            event.setTurbineId(dto.getTurbineId());
            event.setEventType("ALARM");
            event.setSeverity("CRITICAL");
            event.setMessage(msg);
            event.setParameter(param);
            event.setValue(value);
            event.setThreshold(threshold);
            scadaEventRepository.save(event);

            Map<String, Object> alert = new HashMap<>();
            alert.put("turbineId", dto.getTurbineId());
            alert.put("message", isTempFault ? "기어박스 온도 임계치 초과" : "진동 임계치 초과");
            alert.put("value", value);
            alert.put("severity", "CRITICAL");
            messagingTemplate.convertAndSend("/topic/alerts", alert);
        }
    }

    // ------------------------------------------------------------------ //
    //  활성 터빈 목록 (캐시 + TTL)
    // ------------------------------------------------------------------ //
    private List<Turbine> getActiveTurbines() {
        long now = System.currentTimeMillis();
        if (now - lastCacheRefreshMs > CACHE_TTL_MS || activeTurbineCache.isEmpty()) {
            activeTurbineCache = turbineRepository.findByActiveTrueOrderByTurbineId();
            lastCacheRefreshMs = now;
        }
        return activeTurbineCache;
    }

    /** 관리자 변경 후 캐시 즉시 무효화 */
    public void invalidateCache() {
        lastCacheRefreshMs = 0L;
    }

    /** 터빈 삭제 시 시뮬레이션 상태 정리 */
    public void removeSimState(String turbineId) {
        simStates.remove(turbineId);
        prevStatusMap.remove(turbineId);
    }

    // ------------------------------------------------------------------ //
    //  공개 조회 메서드
    // ------------------------------------------------------------------ //
    public List<String> getTurbineIds() {
        return getActiveTurbines().stream()
                .map(Turbine::getTurbineId)
                .toList();
    }

    public List<TurbineData> getLatestData(String turbineId) {
        return turbineDataRepository.findTop100ByTurbineIdOrderByRecordedAtDesc(turbineId);
    }

    public Map<String, Object> getDashboardSummary() {
        List<Turbine> active = getActiveTurbines();
        long activeAlarms = scadaEventRepository.countByStatus("ACTIVE");

        java.time.LocalDateTime startOfDay = java.time.LocalDate.now().atStartOfDay();
        Double sumPower = turbineDataRepository.sumPowerOutputSince(startOfDay);
        double todayEnergyKwh = (sumPower != null ? sumPower / 1800.0 : 0.0);

        Map<String, Object> summary = new HashMap<>();
        summary.put("totalTurbines", active.size());
        summary.put("activeAlarms", activeAlarms);
        summary.put("turbineIds", active.stream().map(Turbine::getTurbineId).toList());
        summary.put("todayEnergyKwh", Math.round(todayEnergyKwh * 10.0) / 10.0);
        return summary;
    }

    // ------------------------------------------------------------------ //
    //  유틸리티
    // ------------------------------------------------------------------ //
    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private static double r1(double v) { return Math.round(v * 10.0) / 10.0; }
    private static double r2(double v) { return Math.round(v * 100.0) / 100.0; }
}
