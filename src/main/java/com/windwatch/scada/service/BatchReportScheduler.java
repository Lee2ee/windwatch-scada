package com.windwatch.scada.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Spring Batch 리포트 자동 스케줄러
 *
 * - 매일 자정 00:00 → 전일 DAILY 리포트
 * - 매주 월요일 00:05 → 지난 주 WEEKLY 리포트
 * - 매월 1일 00:10 → 전월 MONTHLY 리포트
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BatchReportScheduler {

    private final BatchReportService batchReportService;

    /** 매일 자정 — 전일 DAILY 리포트 */
    @Scheduled(cron = "0 0 0 * * *")
    public void runDailyReport() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        log.info("[Scheduler] DAILY 리포트 자동 실행: {}", yesterday);
        try {
            batchReportService.run(yesterday, "DAILY");
        } catch (Exception e) {
            log.error("[Scheduler] DAILY 리포트 실패: {}", e.getMessage());
        }
    }

    /** 매주 월요일 00:05 — 지난 주(일요일 기준) WEEKLY 리포트 */
    @Scheduled(cron = "0 5 0 * * MON")
    public void runWeeklyReport() {
        LocalDate lastSunday = LocalDate.now().minusDays(1);
        log.info("[Scheduler] WEEKLY 리포트 자동 실행: {}", lastSunday);
        try {
            batchReportService.run(lastSunday, "WEEKLY");
        } catch (Exception e) {
            log.error("[Scheduler] WEEKLY 리포트 실패: {}", e.getMessage());
        }
    }

    /** 매월 1일 00:10 — 전월 MONTHLY 리포트 */
    @Scheduled(cron = "0 10 0 1 * *")
    public void runMonthlyReport() {
        LocalDate lastDayOfPrevMonth = LocalDate.now().minusDays(1);
        log.info("[Scheduler] MONTHLY 리포트 자동 실행: {}", lastDayOfPrevMonth);
        try {
            batchReportService.run(lastDayOfPrevMonth, "MONTHLY");
        } catch (Exception e) {
            log.error("[Scheduler] MONTHLY 리포트 실패: {}", e.getMessage());
        }
    }
}
