package com.smartportx.notification.config;

import com.smartportx.notification.stomp.JwtStompChannelInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.List;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtStompChannelInterceptor jwtStompChannelInterceptor;
    private final List<String> allowedOrigins;

    public WebSocketConfig(JwtStompChannelInterceptor jwtStompChannelInterceptor,
                           @Value("${smartportx.websocket.allowed-origins:http://localhost:3000,http://localhost:5173}")
                           List<String> allowedOrigins) {
        this.jwtStompChannelInterceptor = jwtStompChannelInterceptor;
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Two endpoints: primary gateway path /ws and the /ws/notifications alias from
        // API-CONTRACTS. SockJS fallback keeps the broker usable from browser clients
        // that cannot run a raw WebSocket handshake.
        //
        // Security: an explicit origin allow-list replaces the previous wildcard pattern.
        // A browser cannot open a cross-origin WebSocket to the broker from an untrusted
        // site. Non-browser clients (no Origin header) are unaffected. STOMP-level JWT
        // authentication/authorization remains enforced by JwtStompChannelInterceptor.
        registry.addEndpoint("/ws", "/ws/notifications")
                .setAllowedOrigins(allowedOrigins.toArray(String[]::new))
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(jwtStompChannelInterceptor);
    }
}
