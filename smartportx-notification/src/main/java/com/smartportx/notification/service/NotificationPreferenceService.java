package com.smartportx.notification.service;

import com.smartportx.notification.domain.NotificationPreference;
import com.smartportx.notification.repository.NotificationPreferenceRepository;
import com.smartportx.notification.web.dto.NotificationPreferencesDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class NotificationPreferenceService {

    private final NotificationPreferenceRepository repository;

    public NotificationPreferenceService(NotificationPreferenceRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public NotificationPreference get(UUID userId) {
        return repository.findByUserId(userId).orElseGet(() -> createDefaults(userId));
    }

    @Transactional
    public NotificationPreference update(UUID userId, NotificationPreferencesDto dto) {
        NotificationPreference pref = repository.findByUserId(userId)
                .orElseGet(() -> createDefaults(userId));
        applyPatch(pref, dto);
        return repository.save(pref);
    }

    private NotificationPreference createDefaults(UUID userId) {
        NotificationPreference pref = new NotificationPreference();
        pref.setUserId(userId);
        return repository.save(pref);
    }

    private void applyPatch(NotificationPreference pref, NotificationPreferencesDto dto) {
        if (dto == null) {
            return;
        }
        if (dto.emailEnabled() != null) {
            pref.setEmailEnabled(dto.emailEnabled());
        }
        if (dto.emailAddress() != null) {
            pref.setEmailAddress(dto.emailAddress());
        }
        if (dto.smsEnabled() != null) {
            pref.setSmsEnabled(dto.smsEnabled());
        }
        if (dto.smsNumber() != null) {
            pref.setSmsNumber(dto.smsNumber());
        }
        if (dto.websocketEnabled() != null) {
            pref.setWebsocketEnabled(dto.websocketEnabled());
        }
        if (dto.pushEnabled() != null) {
            pref.setPushEnabled(dto.pushEnabled());
        }
        if (dto.categories() != null) {
            pref.setCategories(dto.categories());
        }
        if (dto.quietHours() != null) {
            pref.setQuietHours(dto.quietHours());
        }
        if (dto.digestEnabled() != null) {
            pref.setDigestEnabled(dto.digestEnabled());
        }
        if (dto.digestFrequency() != null) {
            pref.setDigestFrequency(dto.digestFrequency());
        }
    }
}