package com.windwatch.scada.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class TurbineDataDto {
    private String turbineId;
    private Double windSpeed;
    private Double rotorRpm;
    private Double powerOutput;
    private Double gearboxTemp;
    private Double vibration;
    private Double pitchAngle;
    private String status;
    private LocalDateTime recordedAt;
}
