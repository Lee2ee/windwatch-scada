package com.windwatch.scada.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "batch_reports")
@Data
@NoArgsConstructor
public class BatchReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "report_date", nullable = false)
    private LocalDate reportDate;

    @Column(name = "report_type", nullable = false, length = 50)
    private String reportType;  // DAILY / WEEKLY / MONTHLY

    @Column(name = "turbine_id", length = 50)
    private String turbineId;   // NULL = 전체 요약

    @Column(name = "total_turbines")
    private Integer totalTurbines;

    @Column(name = "avg_power_kw")
    private Double avgPowerKw;

    @Column(name = "max_power_kw")
    private Double maxPowerKw;

    @Column(name = "total_energy_kwh")
    private Double totalEnergyKwh;

    @Column(name = "avg_wind_speed")
    private Double avgWindSpeed;

    @Column(name = "avg_gearbox_temp")
    private Double avgGearboxTemp;

    @Column(name = "critical_events")
    private Integer criticalEvents = 0;

    @Column(name = "warning_events")
    private Integer warningEvents = 0;

    @Column(name = "availability_pct")
    private Double availabilityPct;

    @Column(name = "file_path", length = 500)
    private String filePath;

    @Column(name = "generated_at")
    private LocalDateTime generatedAt = LocalDateTime.now();

    @Column(name = "status", length = 50)
    private String status = "COMPLETED";
}
