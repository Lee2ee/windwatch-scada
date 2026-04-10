package com.windwatch.scada.repository;

import com.windwatch.scada.model.BatchReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BatchReportRepository extends JpaRepository<BatchReport, Long> {

    List<BatchReport> findByReportTypeOrderByReportDateDesc(String reportType);

    List<BatchReport> findByReportDateAndReportTypeOrderByTurbineIdAsc(LocalDate reportDate, String reportType);

    Optional<BatchReport> findByReportDateAndReportTypeAndTurbineIdIsNull(LocalDate reportDate, String reportType);

    List<BatchReport> findTop30ByOrderByGeneratedAtDesc();

    @Query("SELECT r FROM BatchReport r WHERE r.reportDate >= :from ORDER BY r.reportDate DESC, r.reportType")
    List<BatchReport> findRecentByFromDate(@Param("from") LocalDate from);

    boolean existsByReportDateAndReportTypeAndTurbineId(LocalDate reportDate, String reportType, String turbineId);
}
