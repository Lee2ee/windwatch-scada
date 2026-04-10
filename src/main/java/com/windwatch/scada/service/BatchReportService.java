package com.windwatch.scada.service;

import com.windwatch.scada.model.BatchReport;
import com.windwatch.scada.repository.BatchReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.*;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.repository.JobRestartException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class BatchReportService {

    private final JobLauncher jobLauncher;
    private final Job dailyReportJob;
    private final BatchReportRepository batchReportRepository;

    /**
     * 지정한 날짜·타입으로 리포트 배치 실행 (동기)
     *
     * @param reportDate 집계 기준일 (WEEKLY/MONTHLY 는 해당 기간의 종료일)
     * @param reportType DAILY | WEEKLY | MONTHLY
     * @return JobExecution 결과
     */
    public JobExecution run(LocalDate reportDate, String reportType) {
        JobParameters params = new JobParametersBuilder()
                .addLocalDate("reportDate", reportDate)
                .addString("reportType", reportType)
                .addLocalDateTime("runAt", LocalDateTime.now())  // 매번 고유 파라미터로 중복 방지
                .toJobParameters();
        try {
            JobExecution execution = jobLauncher.run(dailyReportJob, params);
            log.info("[BatchReport] Job 실행: {} {} → {}", reportType, reportDate, execution.getStatus());
            return execution;
        } catch (JobExecutionAlreadyRunningException | JobRestartException |
                 JobInstanceAlreadyCompleteException | JobParametersInvalidException e) {
            log.error("[BatchReport] Job 실행 실패: {}", e.getMessage());
            throw new RuntimeException("배치 실행 실패: " + e.getMessage(), e);
        }
    }

    /** 최근 30건 리포트 (전체 요약 + 개별 터빈 포함) */
    public List<BatchReport> getRecentReports() {
        return batchReportRepository.findTop30ByOrderByGeneratedAtDesc();
    }

    /** 전체 요약 리포트만 최신순 */
    public List<BatchReport> getSummaryReports() {
        return batchReportRepository.findTop30ByOrderByGeneratedAtDesc()
                .stream()
                .filter(r -> r.getTurbineId() == null)
                .toList();
    }

    /** 특정 날짜·타입의 터빈별 상세 내역 */
    public List<BatchReport> getDetailReports(LocalDate reportDate, String reportType) {
        return batchReportRepository.findByReportDateAndReportTypeOrderByTurbineIdAsc(reportDate, reportType);
    }

    /** 리포트 통계 요약 (대시보드 표시용) */
    public Map<String, Object> getStats() {
        long totalReports = batchReportRepository.count();
        long recentDays = batchReportRepository.findRecentByFromDate(LocalDate.now().minusDays(7)).stream()
                .filter(r -> r.getTurbineId() == null)
                .count();
        return Map.of("totalReports", totalReports, "recentWeekCount", recentDays);
    }
}
