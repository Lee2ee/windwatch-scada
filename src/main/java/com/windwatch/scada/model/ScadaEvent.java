package com.windwatch.scada.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "scada_events")
@Data
@NoArgsConstructor
public class ScadaEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String turbineId;
    private String eventType;   // ALARM, WARNING, INFO
    private String severity;    // CRITICAL, HIGH, MEDIUM, LOW
    private String message;
    private String parameter;
    private Double value;
    private Double threshold;
    private String status;      // ACTIVE, RESOLVED, ACKNOWLEDGED
    private String resolvedBy;
    private LocalDateTime occurredAt;
    private LocalDateTime resolvedAt;

    @PrePersist
    protected void onCreate() {
        if (occurredAt == null) occurredAt = LocalDateTime.now();
        if (status == null) status = "ACTIVE";
    }
}
