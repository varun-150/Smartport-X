package com.smartportx.yard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class YardApplication {

    public static void main(String[] args) {
        SpringApplication.run(YardApplication.class, args);
    }
}
