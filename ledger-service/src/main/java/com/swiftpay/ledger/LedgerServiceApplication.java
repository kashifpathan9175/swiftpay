package com.swiftpay.ledger;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan(basePackages = "com.swiftpay.ledger")
public class LedgerServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(LedgerServiceApplication.class, args);
    }
}
