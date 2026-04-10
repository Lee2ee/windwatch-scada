package com.windwatch.scada.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "turbines")
@Data
@NoArgsConstructor
public class Turbine {
    @Id
    private String turbineId;

    @Column(nullable = false)
    private String turbineName;

    private String location;

    private Double ratedCapacityKw = 2000.0;

    private Boolean active = true;

    private LocalDateTime installedAt;

    @PrePersist
    protected void onCreate() {
        if (installedAt == null) installedAt = LocalDateTime.now();
    }
}
