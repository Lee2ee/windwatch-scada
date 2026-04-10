package com.windwatch.scada.repository;

import com.windwatch.scada.model.ScadaEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;

public interface ScadaEventRepository extends JpaRepository<ScadaEvent, Long> {
    List<ScadaEvent> findByStatusOrderByOccurredAtDesc(String status);

    @Query("SELECT e FROM ScadaEvent e WHERE " +
           "(:turbineId IS NULL OR e.turbineId = :turbineId) AND " +
           "(:severity IS NULL OR e.severity = :severity) AND " +
           "(:status IS NULL OR e.status = :status) AND " +
           "(:from IS NULL OR e.occurredAt >= :from) AND " +
           "(:to IS NULL OR e.occurredAt <= :to) " +
           "ORDER BY e.occurredAt DESC")
    Page<ScadaEvent> searchEvents(
        @Param("turbineId") String turbineId,
        @Param("severity") String severity,
        @Param("status") String status,
        @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to,
        Pageable pageable
    );

    long countByStatus(String status);

    long countByTurbineIdAndSeverityAndOccurredAtBetween(
        String turbineId, String severity, LocalDateTime from, LocalDateTime to);

    long countBySeverityAndOccurredAtBetween(String severity, LocalDateTime from, LocalDateTime to);
}
