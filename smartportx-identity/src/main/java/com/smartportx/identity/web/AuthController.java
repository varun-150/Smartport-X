package com.smartportx.identity.web;

import com.smartportx.identity.service.AuthService;
import com.smartportx.identity.web.dto.ChangePasswordRequest;
import com.smartportx.identity.web.dto.LoginRequest;
import com.smartportx.identity.web.dto.LoginResponse;
import com.smartportx.identity.web.dto.RefreshRequest;
import com.smartportx.identity.web.dto.RefreshResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService auth;
    private final com.smartportx.identity.security.JwtTokenService jwtTokenService;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    public AuthController(AuthService auth,
                          com.smartportx.identity.security.JwtTokenService jwtTokenService,
                          com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        this.auth = auth;
        this.jwtTokenService = jwtTokenService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return auth.login(request, clientIp(http));
    }

    @PostMapping("/refresh")
    public RefreshResponse refresh(@Valid @RequestBody RefreshRequest request, HttpServletRequest http) {
        return auth.refresh(request.refreshToken().trim(), clientIp(http));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@AuthenticationPrincipal Jwt jwt) {
        auth.logout(UUID.fromString(jwt.getSubject()));
    }

    @PutMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody ChangePasswordRequest request, @AuthenticationPrincipal Jwt jwt) {
        auth.changePassword(UUID.fromString(jwt.getSubject()), request, jwt.getClaimAsString("username"));
    }

    @GetMapping("/jwks")
    public Map<String, Object> jwks() {
        try {
            return objectMapper.readValue(jwtTokenService.jwks(),
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
                    });
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Failed to serialize JWK set", e);
        }
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}