package com.smartportx.yard;

import com.smartportx.yard.service.ContainerCatalog;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class YardTestSupport {

    @Bean
    @Primary
    public ContainerCatalog containerCatalog() {
        return TestContainerCatalog.instance();
    }
}