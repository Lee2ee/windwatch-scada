package com.windwatch.scada.controller;

import com.windwatch.scada.model.BatchReport;
import com.windwatch.scada.service.AdminTurbineService;
import com.windwatch.scada.service.AdminUserService;
import com.windwatch.scada.service.BatchReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.JobExecution;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    private final AdminUserService adminUserService;
    private final AdminTurbineService adminTurbineService;
    private final BatchReportService batchReportService;

    // ------------------------------------------------------------------ //
    //  사용자 관리
    // ------------------------------------------------------------------ //
    @GetMapping("/users")
    public String users(Model model) {
        model.addAttribute("users", adminUserService.getAllUsers());
        model.addAttribute("pageTitle", "사용자 관리");
        model.addAttribute("activePage", "admin-users");
        return "admin/users";
    }

    @PostMapping("/users/create")
    public String createUser(@RequestParam String username,
                             @RequestParam String password,
                             @RequestParam String role,
                             @RequestParam(required = false) String email,
                             RedirectAttributes ra) {
        try {
            adminUserService.createUser(username, password, role, email);
            ra.addFlashAttribute("successMsg", "사용자 '" + username + "'이(가) 생성되었습니다.");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/users/{id}/change-password")
    @ResponseBody
    public ResponseEntity<String> changePassword(@PathVariable Long id,
                                                  @RequestBody Map<String, String> body) {
        String newPassword = body.get("password");
        if (newPassword == null || newPassword.length() < 4) {
            return ResponseEntity.badRequest().body("비밀번호는 4자 이상이어야 합니다.");
        }
        adminUserService.changePassword(id, newPassword);
        return ResponseEntity.ok("비밀번호가 변경되었습니다.");
    }

    @PostMapping("/users/{id}/delete")
    @ResponseBody
    public ResponseEntity<String> deleteUser(@PathVariable Long id) {
        adminUserService.deleteUser(id);
        return ResponseEntity.ok("삭제되었습니다.");
    }

    // ------------------------------------------------------------------ //
    //  터빈 관리
    // ------------------------------------------------------------------ //
    @GetMapping("/turbines")
    public String turbines(Model model) {
        model.addAttribute("turbines", adminTurbineService.getAllTurbines());
        model.addAttribute("pageTitle", "터빈 관리");
        model.addAttribute("activePage", "admin-turbines");
        return "admin/turbines";
    }

    @PostMapping("/turbines/create")
    public String createTurbine(@RequestParam String turbineId,
                                @RequestParam String turbineName,
                                @RequestParam(required = false, defaultValue = "") String location,
                                @RequestParam double ratedCapacityKw,
                                RedirectAttributes ra) {
        try {
            adminTurbineService.createTurbine(turbineId, turbineName, location, ratedCapacityKw);
            ra.addFlashAttribute("successMsg", "터빈 '" + turbineId + "'이(가) 등록되었습니다.");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/admin/turbines";
    }

    @PostMapping("/turbines/{id}/toggle")
    @ResponseBody
    public ResponseEntity<String> toggleTurbine(@PathVariable String id) {
        adminTurbineService.toggleActive(id);
        return ResponseEntity.ok("상태가 변경되었습니다.");
    }

    @PostMapping("/turbines/{id}/update")
    @ResponseBody
    public ResponseEntity<String> updateTurbine(@PathVariable String id,
                                                @RequestBody Map<String, String> body) {
        try {
            String name = body.get("turbineName");
            String location = body.getOrDefault("location", "");
            double capacity = Double.parseDouble(body.getOrDefault("ratedCapacityKw", "2000"));
            if (name == null || name.isBlank()) {
                return ResponseEntity.badRequest().body("터빈 이름은 필수입니다.");
            }
            adminTurbineService.updateTurbine(id, name, location, capacity);
            return ResponseEntity.ok("수정되었습니다.");
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body("정격 용량은 숫자여야 합니다.");
        }
    }

    @PostMapping("/turbines/{id}/delete")
    @ResponseBody
    public ResponseEntity<String> deleteTurbine(@PathVariable String id) {
        adminTurbineService.deleteTurbine(id);
        return ResponseEntity.ok("삭제되었습니다.");
    }

    // ------------------------------------------------------------------ //
    //  배치 리포트 관리
    // ------------------------------------------------------------------ //

    @GetMapping("/reports")
    public String reports(Model model) {
        List<BatchReport> summaries = batchReportService.getSummaryReports();
        model.addAttribute("reports", summaries);
        model.addAttribute("pageTitle", "운영 리포트");
        model.addAttribute("activePage", "admin-reports");
        return "admin/reports";
    }

    @GetMapping("/reports/{id}/detail")
    @ResponseBody
    public ResponseEntity<List<BatchReport>> reportDetail(@PathVariable Long id) {
        // id 로부터 date+type 을 찾아 상세 목록 반환
        return batchReportRepository(id);
    }

    @PostMapping("/reports/run")
    public String runReport(@RequestParam String reportDate,
                            @RequestParam String reportType,
                            RedirectAttributes ra) {
        try {
            LocalDate date = LocalDate.parse(reportDate);
            JobExecution exec = batchReportService.run(date, reportType);
            String status = exec.getStatus().name();
            long writeCount = exec.getStepExecutions().stream()
                    .mapToLong(se -> se.getWriteCount()).sum();
            if ("COMPLETED".equals(status) && writeCount > 0) {
                ra.addFlashAttribute("successMsg",
                        reportType + " 리포트 생성 완료 (" + reportDate + ")");
            } else if ("COMPLETED".equals(status)) {
                ra.addFlashAttribute("errorMsg",
                        "해당 기간(" + reportDate + ")에 터빈 데이터가 없습니다. " +
                        "앱 실행 중 데이터가 쌓인 날짜(오늘)를 선택하세요.");
            } else {
                ra.addFlashAttribute("errorMsg",
                        "리포트 생성 중 문제가 발생했습니다. 상태: " + status);
            }
        } catch (Exception e) {
            ra.addFlashAttribute("errorMsg", "실행 실패: " + e.getMessage());
        }
        return "redirect:/admin/reports";
    }

    /** 특정 리포트의 터빈별 상세 데이터 API */
    @GetMapping("/reports/detail")
    @ResponseBody
    public ResponseEntity<List<BatchReport>> getReportDetail(
            @RequestParam String reportDate,
            @RequestParam String reportType) {
        try {
            LocalDate date = LocalDate.parse(reportDate);
            List<BatchReport> details = batchReportService.getDetailReports(date, reportType);
            return ResponseEntity.ok(details);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // 내부 helper (인라인 처리)
    private ResponseEntity<List<BatchReport>> batchReportRepository(Long summaryId) {
        return batchReportService.getRecentReports().stream()
                .filter(r -> r.getId().equals(summaryId))
                .findFirst()
                .map(summary -> ResponseEntity.ok(
                        batchReportService.getDetailReports(summary.getReportDate(), summary.getReportType())))
                .orElse(ResponseEntity.notFound().build());
    }
}
