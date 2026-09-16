package com.smartportx.vessel;

import com.smartportx.vessel.config.VesselProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
@EnableConfigurationProperties(VesselProperties.class)
public class VesselApplication {

    public static void main(String[] args) {
        SpringApplication.run(VesselApplication.class, args);
    }
}
