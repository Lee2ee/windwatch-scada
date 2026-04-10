package com.windwatch.scada.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "turbine_data")
@Data
@NoArgsConstructor
public class TurbineData {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String turbineId;
    private Double windSpeed;       // m/s
    private Double rotorRpm;        // RPM
    private Double powerOutput;     // kW
    private Double gearboxTemp;     // Celsius
    private Double vibration;       // mm/s
    private Double pitchAngle;      // degrees
    private String status;          // NORMAL, WARNING, CRITICAL
    private LocalDateTime recordedAt;

    @PrePersist
    protected void onCreate() {
        if (recordedAt == null) recordedAt = LocalDateTime.now();
    }
}
