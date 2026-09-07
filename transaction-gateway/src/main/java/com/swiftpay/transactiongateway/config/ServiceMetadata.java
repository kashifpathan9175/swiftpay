package com.swiftpay.transactiongateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "swiftpay.app")
public class ServiceMetadata {

    private String name = "transaction-gateway";
    private String version = "0.1.0-SNAPSHOT";
    private String environment = "local";

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }
}
