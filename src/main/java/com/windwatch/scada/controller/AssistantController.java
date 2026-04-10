package com.windwatch.scada.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AssistantController {
    @GetMapping("/assistant")
    public String assistant(Model model) {
        model.addAttribute("pageTitle", "AI 장애 분석 어시스턴트");
        model.addAttribute("activePage", "assistant");
        return "assistant";
    }
}
