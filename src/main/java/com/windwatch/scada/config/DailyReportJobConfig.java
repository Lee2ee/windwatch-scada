package com.windwatch.scada.config;

import com.windwatch.scada.service.batch.DailyReportTasklet;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Spring Batch Job 설정 — dailyReportJob
 *
 * 트리거 방식:
 *  - 자동: BatchReportScheduler 가 자정(00:00)에 전일 DAILY 리포트 생성
 *  - 수동: /admin/reports 페이지 "즉시 실행" 버튼
 *
 * 파라미터:
 *  - reportDate  (LocalDate)  집계 기준일 (WEEKLY/MONTHLY 는 해당 주/월의 마지막 날)
 *  - reportType  (String)     DAILY | WEEKLY | MONTHLY
 *  - runAt       (LocalDateTime) 중복 실행 방지용 고유 키
 */
@Configuration
public class DailyReportJobConfig {

    @Bean
    public Job dailyReportJob(JobRepository jobRepository,
                              Step dailyReportStep) {
        return new JobBuilder("dailyReportJob", jobRepository)
                .start(dailyReportStep)
                .build();
    }

    @Bean
    public Step dailyReportStep(JobRepository jobRepository,
                                PlatformTransactionManager transactionManager,
                                DailyReportTasklet dailyReportTasklet) {
        return new StepBuilder("dailyReportStep", jobRepository)
                .tasklet(dailyReportTasklet, transactionManager)
                .build();
    }
}
