// src/main/java/com/agent/controller/WebController.java
package com.agent.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class WebController {

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("title", "SQL Agent - 智能数据库查询助手");
        return "index";
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("title", "SQL Agent Dashboard");
        return "dashboard";
    }
}