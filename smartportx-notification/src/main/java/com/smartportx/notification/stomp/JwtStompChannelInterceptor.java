package com.smartportx.notification.stomp;

import org.springframework.core.Ordered;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Authenticates STOMP {@code CONNECT}/{@code STOMP} frames with a Bearer JWT and
 * authorizes {@code SUBSCRIBE} frames per {@link TopicAuthorizationService}.
 *
 * <p>The access token is accepted from the {@code Authorization: Bearer ...} header, or
 * the {@code token} / {@code access_token} native STOMP headers (browser clients). On
 * CONNECT the resolved principal is attached to {@link StompHeaderAccessor#setUser};
 * the same session principal is then available on every subsequent frame. A rejected
 * CONNECT/SUBSCRIBE raises {@link MessagingException}, which the STOMP sub-protocol
 * translates into an {@code ERROR} frame.</p>
 */
@Component
public class JwtStompChannelInterceptor implements ChannelInterceptor {

    private final JwtDecoder jwtDecoder;
    private final TopicAuthorizationService topicAuthorizationService;

    public JwtStompChannelInterceptor(JwtDecoder jwtDecoder, TopicAuthorizationService topicAuthorizationService) {
        this.jwtDecoder = jwtDecoder;
        this.topicAuthorizationService = topicAuthorizationService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        StompCommand command = accessor.getCommand();
        if (StompCommand.CONNECT.equals(command) || StompCommand.STOMP.equals(command)) {
            return authenticate(accessor, message);
        }
        if (StompCommand.SUBSCRIBE.equals(command)) {
            return authorizeSubscription(accessor, message);
        }
        return message;
    }

    private Message<?> authenticate(StompHeaderAccessor accessor, Message<?> message) {
        String token = resolveToken(accessor);
        if (token == null || token.isBlank()) {
            throw new MessagingException("Missing access token: provide 'Authorization: Bearer <jwt>' or a 'token' header on STOMP CONNECT");
        }
        Jwt jwt;
        try {
            jwt = jwtDecoder.decode(token);
        } catch (JwtException ex) {
            throw new MessagingException("Invalid access token: " + ex.getMessage());
        }
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                jwt.getSubject(), token, authorities(jwt));
        accessor.setUser(authentication);
        return message;
    }

    private Message<?> authorizeSubscription(StompHeaderAccessor accessor, Message<?> message) {
        Authentication authentication = (Authentication) accessor.getUser();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new MessagingException("SUBSCRIBE rejected: STOMP connection is not authenticated");
        }
        String destination = accessor.getDestination();
        if (!topicAuthorizationService.isAllowed(destination, authentication.getAuthorities())) {
            throw new MessagingException("SUBSCRIBE rejected: no permission for destination '" + destination + "'");
        }
        return message;
    }

    private Collection<GrantedAuthority> authorities(Jwt jwt) {
        Collection<GrantedAuthority> authorities = new ArrayList<>();
        List<String> roles = jwt.getClaimAsStringList("roles");
        if (roles != null) {
            for (String role : roles) {
                authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
            }
        }
        List<String> permissions = jwt.getClaimAsStringList("permissions");
        if (permissions != null) {
            for (String permission : permissions) {
                authorities.add(new SimpleGrantedAuthority(permission));
            }
        }
        return authorities;
    }

    private String resolveToken(StompHeaderAccessor accessor) {
        String authorization = accessor.getFirstNativeHeader("Authorization");
        if (authorization != null && authorization.toUpperCase(Locale.ROOT).startsWith("BEARER ")) {
            return authorization.substring("BEARER ".length()).trim();
        }
        String token = accessor.getFirstNativeHeader("token");
        if (token != null && !token.isBlank()) {
            return token;
        }
        return accessor.getFirstNativeHeader("access_token");
    }
}