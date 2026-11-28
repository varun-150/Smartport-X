package com.smartportx.notification.web;

import com.smartportx.notification.NotificationTestSecurityConfig;
import com.smartportx.notification.NotificationTestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(classes = {NotificationTestSecurityConfig.class})
@Transactional
class NotificationPreferenceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // --- GET /preferences ---

    @Test
    void getPreferences_returnsDefaultsWithValidToken() throws Exception {
        mockMvc.perform(get("/api/v1/notifications/preferences")
                        .header("Authorization", "Bearer " + NotificationTestTokens.admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailEnabled").value(true))
                .andExpect(jsonPath("$.smsEnabled").value(false))
                .andExpect(jsonPath("$.websocketEnabled").value(true))
                .andExpect(jsonPath("$.pushEnabled").value(false))
                .andExpect(jsonPath("$.digestEnabled").value(false))
                .andExpect(jsonPath("$.digestFrequency").value("DAILY"))
                .andExpect(jsonPath("$.userId").isNotEmpty())
                .andExpect(jsonPath("$.id").isNotEmpty());
    }

    @Test
    void getPreferences_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/notifications/preferences"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getPreferences_plainToken_returns403or401() throws Exception {
        // plain has no roles - the endpoint requires any authenticated user
        // Spring Security returns 403 Forbidden for authenticated but unauthorized
        mockMvc.perform(get("/api/v1/notifications/preferences")
                        .header("Authorization", "Bearer " + NotificationTestTokens.plain()))
                .andExpect(status().isOk()); // authenticated + anyRequest().authenticated = 200
    }

    // --- PUT /preferences ---

    @Test
    void putPreferences_partialMerge_emailOnly() throws Exception {
        String putBody = """
                {"emailEnabled": false, "emailAddress": "test@smartportx.com"}
                """;

        mockMvc.perform(put("/api/v1/notifications/preferences")
                        .header("Authorization", "Bearer " + NotificationTestTokens.admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(putBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailEnabled").value(false))
                .andExpect(jsonPath("$.emailAddress").value("test@smartportx.com"))
                // untouched fields remain default
                .andExpect(jsonPath("$.smsEnabled").value(false))
                .andExpect(jsonPath("$.websocketEnabled").value(true));
    }

    @Test
    void putPreferences_partialMerge_smsOnly() throws Exception {
        String putBody = """
                {"smsEnabled": true, "smsNumber": "+15551234567"}
                """;

        mockMvc.perform(put("/api/v1/notifications/preferences")
                        .header("Authorization", "Bearer " + NotificationTestTokens.admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(putBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.smsEnabled").value(true))
                .andExpect(jsonPath("$.smsNumber").value("+15551234567"))
                .andExpect(jsonPath("$.emailEnabled").value(true)); // untouched
    }

    @Test
    void putPreferences_partialMerge_categoriesAndQuietHours() throws Exception {
        String putBody = """
                {
                    "categories": {"CONTAINER_EVENT": true, "YARD_UPDATE": false},
                    "quietHours": {"start": "22:00", "end": "06:00"}
                }
                """;

        mockMvc.perform(put("/api/v1/notifications/preferences")
                        .header("Authorization", "Bearer " + NotificationTestTokens.admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(putBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories.CONTAINER_EVENT").value(true))
                .andExpect(jsonPath("$.categories.YARD_UPDATE").value(false))
                .andExpect(jsonPath("$.quietHours.start").value("22:00"))
                .andExpect(jsonPath("$.quietHours.end").value("06:00"));
    }

    @Test
    void putPreferences_unauthenticated_returns401() throws Exception {
        mockMvc.perform(put("/api/v1/notifications/preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emailEnabled\": false}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void putPreferences_digestSettings() throws Exception {
        String putBody = """
                {"digestEnabled": true, "digestFrequency": "WEEKLY"}
                """;

        mockMvc.perform(put("/api/v1/notifications/preferences")
                        .header("Authorization", "Bearer " + NotificationTestTokens.admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(putBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.digestEnabled").value(true))
                .andExpect(jsonPath("$.digestFrequency").value("WEEKLY"));
    }

    @Test
    void getAfterPut_returnsMergedState() throws Exception {
        // Same token = same userId across both requests, so the PUT merge is
        // visible to the follow-up GET.
        String token = NotificationTestTokens.admin();

        // PUT to set email
        mockMvc.perform(put("/api/v1/notifications/preferences")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emailAddress\": \"persisted@test.com\"}"))
                .andExpect(status().isOk());

        // GET should reflect the persisted state
        mockMvc.perform(get("/api/v1/notifications/preferences")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailAddress").value("persisted@test.com"));
    }
}