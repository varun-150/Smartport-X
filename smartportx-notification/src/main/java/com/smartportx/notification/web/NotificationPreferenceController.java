package com.smartportx.notification.web;

import com.smartportx.notification.domain.NotificationPreference;
import com.smartportx.notification.service.NotificationPreferenceService;
import com.smartportx.notification.web.dto.NotificationPreferencesDto;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationPreferenceController {

    private final NotificationPreferenceService service;

    public NotificationPreferenceController(NotificationPreferenceService service) {
        this.service = service;
    }

    @GetMapping("/preferences")
    public ResponseEntity<NotificationPreference> getPreferences(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(service.get(userId));
    }

    @PutMapping("/preferences")
    public ResponseEntity<NotificationPreference> updatePreferences(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody NotificationPreferencesDto dto) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(service.update(userId, dto));
    }
}