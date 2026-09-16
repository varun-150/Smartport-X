package com.smartportx.eureka.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(EurekaSecurityProperties.class)
public class EurekaSecurityConfig {

    @Bean
    public UserDetailsService eurekaUsers(EurekaSecurityProperties properties) {
        return new InMemoryUserDetailsManager(
                User.withUsername(properties.username())
                        .password("{noop}" + properties.password())
                        .roles("EURKA_ADMIN")
                        .build());
    }

    @Bean
    public SecurityFilterChain eurekaFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/eureka/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info", "/actuator/metrics").permitAll()
                        .anyRequest().authenticated())
                .httpBasic(basic -> basic.realmName("smartportx-eureka"));
        return http.build();
    }
}