package com.smartportx.notification.web.dto;

import com.smartportx.notification.domain.DigestFrequency;
import com.smartportx.notification.domain.QuietHours;

import java.util.Map;

public record NotificationPreferencesDto(
        Boolean emailEnabled,
        String emailAddress,
        Boolean smsEnabled,
        String smsNumber,
        Boolean websocketEnabled,
        Boolean pushEnabled,
        Map<String, Boolean> categories,
        QuietHours quietHours,
        Boolean digestEnabled,
        DigestFrequency digestFrequency
) {}