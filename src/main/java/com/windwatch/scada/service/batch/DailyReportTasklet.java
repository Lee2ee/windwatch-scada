package com.windwatch.scada.service.batch;

import com.windwatch.scada.model.BatchReport;
import com.windwatch.scada.model.TurbineData;
import com.windwatch.scada.repository.BatchReportRepository;
import com.windwatch.scada.repository.ScadaEventRepository;
import com.windwatch.scada.repository.TurbineDataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Spring Batch Tasklet: 지정된 기간의 터빈 데이터를 집계하여 BatchReport 생성 + Excel 파일 저장
 */
@Component
@StepScope
@RequiredArgsConstructor
@Slf4j
public class DailyReportTasklet implements Tasklet {

    private final TurbineDataRepository turbineDataRepository;
    private final ScadaEventRepository scadaEventRepository;
    private final BatchReportRepository batchReportRepository;

    @Value("#{jobParameters['reportDate']}")
    private LocalDate reportDate;

    @Value("#{jobParameters['reportType']}")
    private String reportType;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        log.info("[Batch] {} 리포트 생성 시작: {}", reportType, reportDate);

        LocalDateTime from = computeFrom();
        LocalDateTime to = reportDate.atTime(23, 59, 59);

        List<String> turbineIds = turbineDataRepository.findDistinctTurbineIdsByRecordedAtBetween(from, to);
        if (turbineIds.isEmpty()) {
            log.warn("[Batch] {} ~ {} 기간에 터빈 데이터가 없습니다. 리포트를 생략합니다.", from.toLocalDate(), to.toLocalDate());
            return RepeatStatus.FINISHED;
        }

        // 기간 중복 체크 — 이미 생성된 전체 요약이 있으면 덮어쓰기 위해 기존 레코드 삭제
        List<BatchReport> existing = batchReportRepository.findByReportDateAndReportTypeOrderByTurbineIdAsc(reportDate, reportType);
        if (!existing.isEmpty()) {
            log.info("[Batch] 기존 리포트 {} 건 삭제 후 재생성", existing.size());
            batchReportRepository.deleteAll(existing);
        }

        List<BatchReport> reports = new ArrayList<>();

        for (String turbineId : turbineIds) {
            List<TurbineData> data = turbineDataRepository.findByTurbineIdAndRecordedAtBetween(turbineId, from, to);
            if (data.isEmpty()) continue;

            double avgPower     = avg(data.stream().mapToDouble(d -> orZero(d.getPowerOutput())));
            double maxPower     = data.stream().mapToDouble(d -> orZero(d.getPowerOutput())).max().orElse(0);
            double avgWind      = avg(data.stream().mapToDouble(d -> orZero(d.getWindSpeed())));
            double avgTemp      = avg(data.stream().mapToDouble(d -> orZero(d.getGearboxTemp())));
            // kWh = power(kW) × 2sec / 3600
            double totalEnergy  = data.stream().mapToDouble(d -> orZero(d.getPowerOutput()) * 2.0 / 3600.0).sum();
            long operational    = data.stream().filter(d -> !"STOPPED".equals(d.getStatus())).count();
            double availability = r2((double) operational / data.size() * 100.0);

            long criticalEvents = scadaEventRepository.countByTurbineIdAndSeverityAndOccurredAtBetween(turbineId, "CRITICAL", from, to);
            long warningEvents  = scadaEventRepository.countByTurbineIdAndSeverityAndOccurredAtBetween(turbineId, "WARNING",  from, to);

            BatchReport report = new BatchReport();
            report.setReportDate(reportDate);
            report.setReportType(reportType);
            report.setTurbineId(turbineId);
            report.setAvgPowerKw(r2(avgPower));
            report.setMaxPowerKw(r2(maxPower));
            report.setTotalEnergyKwh(r2(totalEnergy));
            report.setAvgWindSpeed(r2(avgWind));
            report.setAvgGearboxTemp(r2(avgTemp));
            report.setCriticalEvents((int) criticalEvents);
            report.setWarningEvents((int) warningEvents);
            report.setAvailabilityPct(availability);
            report.setStatus("COMPLETED");
            reports.add(report);
        }

        // 전체 요약 (turbineId = null)
        if (!reports.isEmpty()) {
            BatchReport overall = buildOverallReport(reports, turbineIds.size());
            String excelPath = generateExcel(reportDate, reportType, reports, overall);
            overall.setFilePath(excelPath);
            reports.add(overall);
        }

        batchReportRepository.saveAll(reports);
        log.info("[Batch] {} 리포트 생성 완료: 터빈 {}기 + 전체 요약 1건", reportType, turbineIds.size());
        contribution.incrementWriteCount(reports.size());

        return RepeatStatus.FINISHED;
    }

    // ------------------------------------------------------------------ //
    //  Helper methods
    // ------------------------------------------------------------------ //

    private LocalDateTime computeFrom() {
        return switch (reportType) {
            case "WEEKLY"  -> reportDate.minusDays(6).atStartOfDay();
            case "MONTHLY" -> reportDate.withDayOfMonth(1).atStartOfDay();
            default        -> reportDate.atStartOfDay(); // DAILY
        };
    }

    private BatchReport buildOverallReport(List<BatchReport> perTurbine, int totalTurbines) {
        BatchReport overall = new BatchReport();
        overall.setReportDate(reportDate);
        overall.setReportType(reportType);
        overall.setTurbineId(null);
        overall.setTotalTurbines(totalTurbines);
        overall.setAvgPowerKw(r2(perTurbine.stream().mapToDouble(r -> orZero(r.getAvgPowerKw())).average().orElse(0)));
        overall.setMaxPowerKw(r2(perTurbine.stream().mapToDouble(r -> orZero(r.getMaxPowerKw())).max().orElse(0)));
        overall.setTotalEnergyKwh(r2(perTurbine.stream().mapToDouble(r -> orZero(r.getTotalEnergyKwh())).sum()));
        overall.setAvgWindSpeed(r2(perTurbine.stream().mapToDouble(r -> orZero(r.getAvgWindSpeed())).average().orElse(0)));
        overall.setAvgGearboxTemp(r2(perTurbine.stream().mapToDouble(r -> orZero(r.getAvgGearboxTemp())).average().orElse(0)));
        overall.setCriticalEvents(perTurbine.stream().mapToInt(r -> r.getCriticalEvents() != null ? r.getCriticalEvents() : 0).sum());
        overall.setWarningEvents(perTurbine.stream().mapToInt(r -> r.getWarningEvents() != null ? r.getWarningEvents() : 0).sum());
        overall.setAvailabilityPct(r2(perTurbine.stream().mapToDouble(r -> orZero(r.getAvailabilityPct())).average().orElse(0)));
        overall.setStatus("COMPLETED");
        return overall;
    }

    private String generateExcel(LocalDate date, String type, List<BatchReport> perTurbine, BatchReport overall) {
        String dir = System.getProperty("java.io.tmpdir") + File.separator + "windwatch_reports";
        new File(dir).mkdirs();
        String filename = String.format("%s_%s_%s.xlsx", type, date.format(DateTimeFormatter.ofPattern("yyyyMMdd")),
                System.currentTimeMillis());
        String path = dir + File.separator + filename;

        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet(type + " Report");
            CellStyle headerStyle = wb.createCellStyle();
            Font font = wb.createFont();
            font.setBold(true);
            headerStyle.setFont(font);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            // Header row
            String[] headers = {"Turbine ID", "Avg Power (kW)", "Max Power (kW)", "Total Energy (kWh)",
                    "Avg Wind (m/s)", "Avg Gearbox Temp (°C)", "Critical Events", "Warning Events", "Availability (%)"};
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
                sheet.autoSizeColumn(i);
            }

            // Per-turbine rows
            int rowIdx = 1;
            for (BatchReport r : perTurbine) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(r.getTurbineId() != null ? r.getTurbineId() : "OVERALL");
                row.createCell(1).setCellValue(orZero(r.getAvgPowerKw()));
                row.createCell(2).setCellValue(orZero(r.getMaxPowerKw()));
                row.createCell(3).setCellValue(orZero(r.getTotalEnergyKwh()));
                row.createCell(4).setCellValue(orZero(r.getAvgWindSpeed()));
                row.createCell(5).setCellValue(orZero(r.getAvgGearboxTemp()));
                row.createCell(6).setCellValue(r.getCriticalEvents() != null ? r.getCriticalEvents() : 0);
                row.createCell(7).setCellValue(r.getWarningEvents() != null ? r.getWarningEvents() : 0);
                row.createCell(8).setCellValue(orZero(r.getAvailabilityPct()));
            }

            // Overall summary row
            Row summaryRow = sheet.createRow(rowIdx);
            summaryRow.createCell(0).setCellValue("OVERALL");
            summaryRow.createCell(1).setCellValue(orZero(overall.getAvgPowerKw()));
            summaryRow.createCell(2).setCellValue(orZero(overall.getMaxPowerKw()));
            summaryRow.createCell(3).setCellValue(orZero(overall.getTotalEnergyKwh()));
            summaryRow.createCell(4).setCellValue(orZero(overall.getAvgWindSpeed()));
            summaryRow.createCell(5).setCellValue(orZero(overall.getAvgGearboxTemp()));
            summaryRow.createCell(6).setCellValue(overall.getCriticalEvents() != null ? overall.getCriticalEvents() : 0);
            summaryRow.createCell(7).setCellValue(overall.getWarningEvents() != null ? overall.getWarningEvents() : 0);
            summaryRow.createCell(8).setCellValue(orZero(overall.getAvailabilityPct()));

            for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);

            try (FileOutputStream fos = new FileOutputStream(path)) {
                wb.write(fos);
            }
            log.info("[Batch] Excel 리포트 저장: {}", path);
            return path;
        } catch (IOException e) {
            log.warn("[Batch] Excel 생성 실패: {}", e.getMessage());
            return null;
        }
    }

    private static double orZero(Double v) { return v != null ? v : 0.0; }
    private static double avg(java.util.stream.DoubleStream s) { return s.average().orElse(0); }
    private static double r2(double v) { return Math.round(v * 100.0) / 100.0; }
}
