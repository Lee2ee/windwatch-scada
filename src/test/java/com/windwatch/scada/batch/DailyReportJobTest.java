package com.windwatch.scada.batch;

import com.windwatch.scada.model.BatchReport;
import com.windwatch.scada.model.ScadaEvent;
import com.windwatch.scada.model.TurbineData;
import com.windwatch.scada.repository.BatchReportRepository;
import com.windwatch.scada.repository.ScadaEventRepository;
import com.windwatch.scada.repository.TurbineDataRepository;
import com.windwatch.scada.service.BatchReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spring Batch 일간 리포트 Job 통합 테스트
 *
 * 검증 항목:
 *  1. Job 완료 상태 (BatchStatus.COMPLETED)
 *  2. 터빈별 BatchReport 레코드 생성 확인
 *  3. 전체 요약 레코드 (turbineId = null) 생성 확인
 *  4. 집계 값 정확성 (avgPower, totalEnergy, availability)
 *  5. CRITICAL 이벤트 카운트 반영 확인
 *  6. 데이터 없는 날짜 — 리포트 미생성 확인
 *  7. 동일 날짜 재실행 — 기존 데이터 갱신 확인 (중복 없음)
 *  8. WEEKLY 리포트 — 7일치 데이터 집계
 */
@SpringBootTest
@ActiveProfiles("test")
class DailyReportJobTest {

    @Autowired
    private BatchReportService batchReportService;

    @Autowired
    private TurbineDataRepository turbineDataRepository;

    @Autowired
    private ScadaEventRepository scadaEventRepository;

    @Autowired
    private BatchReportRepository batchReportRepository;

    private static final LocalDate TEST_DATE = LocalDate.of(2026, 4, 9);
    private static final String TURBINE_A = "WT-001";
    private static final String TURBINE_B = "WT-002";

    @BeforeEach
    void setUp() {
        turbineDataRepository.deleteAll();
        scadaEventRepository.deleteAll();
        batchReportRepository.deleteAll();
    }

    // ------------------------------------------------------------------ //
    //  Test 1: 기본 동작 — 2개 터빈, 정상 데이터
    // ------------------------------------------------------------------ //
    @Test
    @DisplayName("정상 시나리오: 2개 터빈 데이터 집계 후 BatchReport 3건 생성")
    void testNormalScenario() {
        // Given
        seedTurbineData(TURBINE_A, TEST_DATE, 1000.0, 8.0, 60.0, "NORMAL", 6);
        seedTurbineData(TURBINE_B, TEST_DATE, 1500.0, 9.0, 65.0, "NORMAL", 6);

        // When
        JobExecution execution = batchReportService.run(TEST_DATE, "DAILY");

        // Then
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        List<BatchReport> all = batchReportRepository.findByReportDateAndReportTypeOrderByTurbineIdAsc(TEST_DATE, "DAILY");
        assertThat(all).hasSize(3); // WT-001, WT-002, OVERALL

        BatchReport wt001 = all.stream().filter(r -> TURBINE_A.equals(r.getTurbineId())).findFirst().orElseThrow();
        assertThat(wt001.getAvgPowerKw()).isEqualTo(1000.0);
        assertThat(wt001.getAvailabilityPct()).isEqualTo(100.0);

        BatchReport overall = all.stream().filter(r -> r.getTurbineId() == null).findFirst().orElseThrow();
        assertThat(overall.getTotalTurbines()).isEqualTo(2);
        assertThat(overall.getAvgPowerKw()).isEqualTo(1250.0); // (1000+1500)/2
    }

    // ------------------------------------------------------------------ //
    //  Test 2: 에너지 계산 검증
    // ------------------------------------------------------------------ //
    @Test
    @DisplayName("에너지 집계: 1800kW × 10건 × 2초/3600 = 10.0 kWh")
    void testEnergyCalculation() {
        // Given: 1800 kW 출력, 10건 (10 × 2sec = 20sec)
        seedTurbineData(TURBINE_A, TEST_DATE, 1800.0, 10.0, 70.0, "NORMAL", 10);

        // When
        batchReportService.run(TEST_DATE, "DAILY");

        // Then: energy = 1800 × 10 × 2 / 3600 = 10.0 kWh
        BatchReport report = batchReportRepository
                .findByReportDateAndReportTypeOrderByTurbineIdAsc(TEST_DATE, "DAILY")
                .stream().filter(r -> TURBINE_A.equals(r.getTurbineId())).findFirst().orElseThrow();

        assertThat(report.getTotalEnergyKwh()).isEqualTo(10.0);
    }

    // ------------------------------------------------------------------ //
    //  Test 3: 가동률 계산
    // ------------------------------------------------------------------ //
    @Test
    @DisplayName("가동률: 10건 중 STOPPED 5건 → 50%")
    void testAvailability() {
        // Given: 5건 NORMAL, 5건 STOPPED
        seedTurbineData(TURBINE_A, TEST_DATE, 1200.0, 8.0, 55.0, "NORMAL",  5);
        seedTurbineData(TURBINE_A, TEST_DATE, 0.0,    1.0, 35.0, "STOPPED", 5);

        // When
        batchReportService.run(TEST_DATE, "DAILY");

        // Then
        BatchReport report = batchReportRepository
                .findByReportDateAndReportTypeOrderByTurbineIdAsc(TEST_DATE, "DAILY")
                .stream().filter(r -> TURBINE_A.equals(r.getTurbineId())).findFirst().orElseThrow();

        assertThat(report.getAvailabilityPct()).isEqualTo(50.0);
    }

    // ------------------------------------------------------------------ //
    //  Test 4: CRITICAL 이벤트 카운트
    // ------------------------------------------------------------------ //
    @Test
    @DisplayName("CRITICAL 이벤트 2건, WARNING 1건이 리포트에 반영됨")
    void testCriticalEventCount() {
        // Given
        seedTurbineData(TURBINE_A, TEST_DATE, 1000.0, 8.0, 60.0, "NORMAL", 5);
        seedEvent(TURBINE_A, TEST_DATE, "CRITICAL");
        seedEvent(TURBINE_A, TEST_DATE, "CRITICAL");
        seedEvent(TURBINE_A, TEST_DATE, "WARNING");

        // When
        batchReportService.run(TEST_DATE, "DAILY");

        // Then
        BatchReport report = batchReportRepository
                .findByReportDateAndReportTypeOrderByTurbineIdAsc(TEST_DATE, "DAILY")
                .stream().filter(r -> TURBINE_A.equals(r.getTurbineId())).findFirst().orElseThrow();

        assertThat(report.getCriticalEvents()).isEqualTo(2);
        assertThat(report.getWarningEvents()).isEqualTo(1);
    }

    // ------------------------------------------------------------------ //
    //  Test 5: 데이터 없는 날짜 — 리포트 미생성
    // ------------------------------------------------------------------ //
    @Test
    @DisplayName("데이터가 없는 날짜는 리포트를 생성하지 않음")
    void testNoDataDate() {
        // Given: 데이터 없음
        LocalDate emptyDate = LocalDate.of(2026, 1, 1);

        // When
        JobExecution execution = batchReportService.run(emptyDate, "DAILY");

        // Then
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(batchReportRepository.findByReportDateAndReportTypeOrderByTurbineIdAsc(emptyDate, "DAILY")).isEmpty();
    }

    // ------------------------------------------------------------------ //
    //  Test 6: 동일 날짜 재실행 — 갱신 (중복 없음)
    // ------------------------------------------------------------------ //
    @Test
    @DisplayName("동일 날짜 재실행 시 기존 리포트 삭제 후 재생성 — 중복 없음")
    void testIdempotentRerun() {
        // Given
        seedTurbineData(TURBINE_A, TEST_DATE, 1000.0, 8.0, 60.0, "NORMAL", 4);

        // When: 두 번 실행
        batchReportService.run(TEST_DATE, "DAILY");
        batchReportService.run(TEST_DATE, "DAILY");

        // Then: 2건 (WT-001 + OVERALL) — 중복 없음
        List<BatchReport> all = batchReportRepository.findByReportDateAndReportTypeOrderByTurbineIdAsc(TEST_DATE, "DAILY");
        assertThat(all).hasSize(2);
    }

    // ------------------------------------------------------------------ //
    //  Test 7: WEEKLY 리포트 — 7일치 데이터 집계
    // ------------------------------------------------------------------ //
    @Test
    @DisplayName("WEEKLY 리포트: 7일 범위 데이터가 하나의 리포트로 집계됨")
    void testWeeklyReport() {
        // Given: TEST_DATE 포함 7일 각 2건씩
        for (int i = 0; i < 7; i++) {
            seedTurbineData(TURBINE_A, TEST_DATE.minusDays(i), 1000.0, 8.0, 60.0, "NORMAL", 2);
        }

        // When
        JobExecution execution = batchReportService.run(TEST_DATE, "WEEKLY");

        // Then
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        List<BatchReport> all = batchReportRepository.findByReportDateAndReportTypeOrderByTurbineIdAsc(TEST_DATE, "WEEKLY");
        assertThat(all).isNotEmpty();
        assertThat(all.stream().anyMatch(r -> r.getTurbineId() == null)).isTrue(); // 전체 요약 존재
    }

    // ------------------------------------------------------------------ //
    //  Test 8: 최대 출력값 검증
    // ------------------------------------------------------------------ //
    @Test
    @DisplayName("maxPowerKw: 여러 출력값 중 최대값 정확히 집계")
    void testMaxPowerKw() {
        // Given: 500, 1000, 1500, 2000 kW 순서로 4건
        double[] powers = {500.0, 1000.0, 1500.0, 2000.0};
        for (int i = 0; i < powers.length; i++) {
            TurbineData d = new TurbineData();
            d.setTurbineId(TURBINE_A);
            d.setPowerOutput(powers[i]);
            d.setWindSpeed(8.0);
            d.setGearboxTemp(60.0);
            d.setRotorRpm(15.0);
            d.setVibration(1.5);
            d.setPitchAngle(0.0);
            d.setStatus("NORMAL");
            d.setRecordedAt(TEST_DATE.atTime(10, i, 0));
            turbineDataRepository.save(d);
        }

        // When
        batchReportService.run(TEST_DATE, "DAILY");

        // Then
        BatchReport report = batchReportRepository
                .findByReportDateAndReportTypeOrderByTurbineIdAsc(TEST_DATE, "DAILY")
                .stream().filter(r -> TURBINE_A.equals(r.getTurbineId())).findFirst().orElseThrow();

        assertThat(report.getMaxPowerKw()).isEqualTo(2000.0);
        assertThat(report.getAvgPowerKw()).isEqualTo(1250.0); // (500+1000+1500+2000)/4
    }

    // ------------------------------------------------------------------ //
    //  Helper methods
    // ------------------------------------------------------------------ //

    private void seedTurbineData(String turbineId, LocalDate date, double power,
                                  double wind, double temp, String status, int count) {
        for (int i = 0; i < count; i++) {
            TurbineData d = new TurbineData();
            d.setTurbineId(turbineId);
            d.setPowerOutput(power);
            d.setWindSpeed(wind);
            d.setGearboxTemp(temp);
            d.setRotorRpm(15.0);
            d.setVibration(1.5);
            d.setPitchAngle(0.0);
            d.setStatus(status);
            d.setRecordedAt(date.atTime(10, i % 60, i / 60));
            turbineDataRepository.save(d);
        }
    }

    private void seedEvent(String turbineId, LocalDate date, String severity) {
        ScadaEvent e = new ScadaEvent();
        e.setTurbineId(turbineId);
        e.setEventType("ALARM");
        e.setSeverity(severity);
        e.setMessage(turbineId + " 테스트 이벤트");
        e.setParameter("gearboxTemp");
        e.setValue(90.0);
        e.setThreshold(85.0);
        e.setOccurredAt(date.atTime(12, 0, 0));
        scadaEventRepository.save(e);
    }
}
