package com.windwatch.scada.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class InspectionController {
    @GetMapping("/inspection")
    public String inspection(Model model) {
        model.addAttribute("pageTitle", "AI 블레이드 결함 탐지");
        model.addAttribute("activePage", "inspection");
        return "inspection";
    }
}
