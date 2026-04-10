package com.windwatch.scada.repository;

import com.windwatch.scada.model.TurbineData;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;

public interface TurbineDataRepository extends JpaRepository<TurbineData, Long> {
    List<TurbineData> findTop100ByTurbineIdOrderByRecordedAtDesc(String turbineId);
    List<TurbineData> findByRecordedAtAfter(LocalDateTime since);
    TurbineData findTopByTurbineIdOrderByRecordedAtDesc(String turbineId);

    List<TurbineData> findByTurbineIdAndRecordedAtBetween(String turbineId, LocalDateTime from, LocalDateTime to);

    @org.springframework.data.jpa.repository.Query(
        "SELECT DISTINCT d.turbineId FROM TurbineData d WHERE d.recordedAt BETWEEN :from AND :to")
    List<String> findDistinctTurbineIdsByRecordedAtBetween(
        @org.springframework.data.repository.query.Param("from") LocalDateTime from,
        @org.springframework.data.repository.query.Param("to") LocalDateTime to);
}
