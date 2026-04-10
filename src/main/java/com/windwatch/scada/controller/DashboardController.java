package com.windwatch.scada.controller;

import com.windwatch.scada.service.TurbineService;
import com.windwatch.scada.service.EventService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class DashboardController {
    private final TurbineService turbineService;
    private final EventService eventService;

    @GetMapping("/")
    public String index() {
        return "redirect:/dashboard";
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        Map<String, Object> summary = turbineService.getDashboardSummary();
        model.addAttribute("totalTurbines", summary.get("totalTurbines"));
        model.addAttribute("activeAlarms", summary.get("activeAlarms"));
        model.addAttribute("turbineIds", summary.get("turbineIds"));
        model.addAttribute("activeEvents", eventService.getActiveAlarms());
        model.addAttribute("pageTitle", "실시간 대시보드");
        model.addAttribute("activePage", "dashboard");
        return "dashboard";
    }
}
