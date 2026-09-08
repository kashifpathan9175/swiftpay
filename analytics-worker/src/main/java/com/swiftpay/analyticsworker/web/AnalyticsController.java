package com.swiftpay.analyticsworker.web;

import com.swiftpay.analyticsworker.service.AnalyticsService;
import com.swiftpay.analyticsworker.service.AnalyticsSummary;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/analytics/summary")
    public AnalyticsSummary summary() {
        return analyticsService.getSummary();
    }
}
