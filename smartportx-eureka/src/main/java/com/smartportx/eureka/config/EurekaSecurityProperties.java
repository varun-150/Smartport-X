package com.smartportx.eureka.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "smartportx.eureka.security")
public record EurekaSecurityProperties(String username, String password) {

    public EurekaSecurityProperties {
        if (username == null || username.isBlank()) {
            username = "admin";
        }
        if (password == null || password.isBlank()) {
            password = "admin";
        }
    }
}