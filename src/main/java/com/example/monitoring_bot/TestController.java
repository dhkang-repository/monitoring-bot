package com.example.monitoring_bot;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {
    @Autowired
    private HealthCheckScheduler healthCheckScheduler;

    @GetMapping("/")
    public String root() {
        healthCheckScheduler.reportHealthToSlack();
        return "";
    }
}
