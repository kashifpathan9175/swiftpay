package com.swiftpay.ledger.controller;

import com.swiftpay.ledger.config.ServiceMetadata;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class InfoController {

    private final ServiceMetadata serviceMetadata;

    public InfoController(ServiceMetadata serviceMetadata) {
        this.serviceMetadata = serviceMetadata;
    }

    @GetMapping("/info")
    public Map<String, String> info() {
        return Map.of(
                "service", serviceMetadata.getName(),
                "version", serviceMetadata.getVersion(),
                "environment", serviceMetadata.getEnvironment()
        );
    }
}
