package com.windwatch.scada.controller;

import com.windwatch.scada.model.ScadaEvent;
import com.windwatch.scada.service.EventService;
import com.windwatch.scada.service.TurbineService;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class EventController {
    private final EventService eventService;
    private final TurbineService turbineService;

    @GetMapping("/events")
    public String events(
            @RequestParam(required = false) String turbineId,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "0") int page,
            Model model) {

        Page<ScadaEvent> eventPage = eventService.searchEvents(turbineId, severity, status, from, to, page, 15);
        model.addAttribute("eventPage", eventPage);
        model.addAttribute("events", eventPage.getContent());
        model.addAttribute("turbineIds", turbineService.getTurbineIds());
        model.addAttribute("currentTurbineId", turbineId);
        model.addAttribute("currentSeverity", severity);
        model.addAttribute("currentStatus", status);
        model.addAttribute("currentFrom", from);
        model.addAttribute("currentTo", to);
        model.addAttribute("pageTitle", "알람 & 운영 이력");
        model.addAttribute("activePage", "events");
        return "events";
    }

    @PostMapping("/events/{id}/acknowledge")
    @ResponseBody
    public ResponseEntity<String> acknowledge(@PathVariable Long id, @AuthenticationPrincipal UserDetails user) {
        eventService.acknowledgeEvent(id, user.getUsername());
        return ResponseEntity.ok("acknowledged");
    }

    @PostMapping("/events/{id}/resolve")
    @ResponseBody
    public ResponseEntity<String> resolve(@PathVariable Long id, @AuthenticationPrincipal UserDetails user) {
        eventService.resolveEvent(id, user.getUsername());
        return ResponseEntity.ok("resolved");
    }

    @GetMapping("/events/export")
    public void exportExcel(HttpServletResponse response) throws IOException {
        List<ScadaEvent> events = eventService.getAllForExport();
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=scada_events.xlsx");

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Events");
            Row header = sheet.createRow(0);
            String[] columns = {"ID", "터빈ID", "유형", "심각도", "메시지", "파라미터", "값", "임계치", "상태", "발생시간", "해결시간"};
            for (int i = 0; i < columns.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(columns[i]);
            }
            int rowNum = 1;
            for (ScadaEvent e : events) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(e.getId());
                row.createCell(1).setCellValue(e.getTurbineId());
                row.createCell(2).setCellValue(e.getEventType());
                row.createCell(3).setCellValue(e.getSeverity());
                row.createCell(4).setCellValue(e.getMessage());
                row.createCell(5).setCellValue(e.getParameter());
                row.createCell(6).setCellValue(e.getValue() != null ? e.getValue() : 0);
                row.createCell(7).setCellValue(e.getThreshold() != null ? e.getThreshold() : 0);
                row.createCell(8).setCellValue(e.getStatus());
                row.createCell(9).setCellValue(e.getOccurredAt() != null ? e.getOccurredAt().toString() : "");
                row.createCell(10).setCellValue(e.getResolvedAt() != null ? e.getResolvedAt().toString() : "");
            }
            workbook.write(response.getOutputStream());
        }
    }
}
