package com.swiftpay.analyticsworker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan(basePackages = "com.swiftpay.analyticsworker")
public class AnalyticsWorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(AnalyticsWorkerApplication.class, args);
    }
}
