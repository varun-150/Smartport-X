package com.smartportx.eureka.config;

import com.smartportx.common.monitoring.MonitoringNetworkAccess;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.web.util.matcher.RequestMatcher;
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

    @Value("${smartportx.monitoring.allowed-networks:127.0.0.1/32,::1/128,172.16.0.0/12}")
    private List<String> monitoringNetworks;
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
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers(prometheusFromMonitoringNetwork()).permitAll()
                        .requestMatchers("/actuator/prometheus").denyAll()
                        .requestMatchers("/actuator/metrics").authenticated()
                        .anyRequest().authenticated())
                .httpBasic(basic -> basic.realmName("smartportx-eureka"));
        return http.build();
    }

    private RequestMatcher prometheusFromMonitoringNetwork() {
        return request -> {
            String uri = request.getRequestURI();
            String context = request.getContextPath();
            if (context != null && !context.isEmpty() && uri.startsWith(context)) {
                uri = uri.substring(context.length());
            }
            return "/actuator/prometheus".equals(uri)
                    && MonitoringNetworkAccess.isAllowed(request.getRemoteAddr(), monitoringNetworks);
        };
    }
}
