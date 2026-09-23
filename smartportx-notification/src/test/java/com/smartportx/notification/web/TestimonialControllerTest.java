package com.smartportx.notification.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(classes = {NotificationTestSecurityConfig.class})
@Transactional
class TestimonialControllerTest {

    private static final String VALID_BODY = """
            {"name": "Aisha Rahman", "role": "VP Port Operations, Jebel Ali",
             "message": "Exception alerts reach the right controller in seconds.", "rating": 5}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // --- GET /api/v1/testimonials ---

    @Test
    void list_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/testimonials"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void list_authenticated_returnsArray() throws Exception {
        mockMvc.perform(get("/api/v1/testimonials")
                        .header("Authorization", "Bearer " + NotificationTestTokens.admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void list_readerRole_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/testimonials")
                        .header("Authorization", "Bearer " + NotificationTestTokens.reader()))
                .andExpect(status().isOk());
    }

    // --- GET /api/v1/testimonials/{id} ---

    @Test
    void get_byId_returnsCreatedTestimonial() throws Exception {
        String id = create(VALID_BODY);

        mockMvc.perform(get("/api/v1/testimonials/" + id)
                        .header("Authorization", "Bearer " + NotificationTestTokens.admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.name").value("Aisha Rahman"))
                .andExpect(jsonPath("$.role").value("VP Port Operations, Jebel Ali"))
                .andExpect(jsonPath("$.rating").value(5))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    @Test
    void get_notFound_returns404Problem() throws Exception {
        mockMvc.perform(get("/api/v1/testimonials/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + NotificationTestTokens.admin()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("not_found"));
    }

    @Test
    void get_malformedId_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/testimonials/not-a-uuid")
                        .header("Authorization", "Bearer " + NotificationTestTokens.admin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("malformed_request"));
    }

    // --- POST /api/v1/testimonials ---

    @Test
    void create_admin_returns201AndEchoes() throws Exception {
        mockMvc.perform(post("/api/v1/testimonials")
                        .header("Authorization", "Bearer " + NotificationTestTokens.admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.name").value("Aisha Rahman"))
                .andExpect(jsonPath("$.role").value("VP Port Operations, Jebel Ali"))
                .andExpect(jsonPath("$.message").value(
                        "Exception alerts reach the right controller in seconds."))
                .andExpect(jsonPath("$.rating").value(5));
    }

    @Test
    void create_nonAdmin_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/testimonials")
                        .header("Authorization", "Bearer " + NotificationTestTokens.reader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("access_denied"));
    }

    @Test
    void create_plainAuthenticated_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/testimonials")
                        .header("Authorization", "Bearer " + NotificationTestTokens.plain())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/testimonials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void create_validationFails_returns400Problem() throws Exception {
        String badBody = """
                {"name": "", "message": "", "rating": 6}
                """;
        mockMvc.perform(post("/api/v1/testimonials")
                        .header("Authorization", "Bearer " + NotificationTestTokens.admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"))
                .andExpect(jsonPath("$.fieldErrors").isNotEmpty());
    }

    @Test
    void create_ratingBelowOne_returns400() throws Exception {
        String badBody = """
                {"name": "Valid Name", "message": "Valid message.", "rating": 0}
                """;
        mockMvc.perform(post("/api/v1/testimonials")
                        .header("Authorization", "Bearer " + NotificationTestTokens.admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"));
    }

    // --- PUT /api/v1/testimonials/{id} ---

    @Test
    void update_admin_returnsUpdated() throws Exception {
        String id = create(VALID_BODY);

        String updateBody = """
                {"name": "Aisha Rahman", "role": "COO, Jebel Ali",
                 "message": "Updated message after platform refresh.", "rating": 4}
                """;
        mockMvc.perform(put("/api/v1/testimonials/" + id)
                        .header("Authorization", "Bearer " + NotificationTestTokens.admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.role").value("COO, Jebel Ali"))
                .andExpect(jsonPath("$.message").value("Updated message after platform refresh."))
                .andExpect(jsonPath("$.rating").value(4))
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());
    }

    @Test
    void update_notFound_returns404() throws Exception {
        mockMvc.perform(put("/api/v1/testimonials/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + NotificationTestTokens.admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isNotFound());
    }

    @Test
    void update_nonAdmin_returns403() throws Exception {
        String id = create(VALID_BODY);
        mockMvc.perform(put("/api/v1/testimonials/" + id)
                        .header("Authorization", "Bearer " + NotificationTestTokens.gateOp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isForbidden());
    }

    // --- DELETE /api/v1/testimonials/{id} ---

    @Test
    void delete_admin_returns204Then404() throws Exception {
        String id = create(VALID_BODY);

        mockMvc.perform(delete("/api/v1/testimonials/" + id)
                        .header("Authorization", "Bearer " + NotificationTestTokens.admin()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/testimonials/" + id)
                        .header("Authorization", "Bearer " + NotificationTestTokens.admin()))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_nonAdmin_returns403() throws Exception {
        String id = create(VALID_BODY);
        mockMvc.perform(delete("/api/v1/testimonials/" + id)
                        .header("Authorization", "Bearer " + NotificationTestTokens.yardManager()))
                .andExpect(status().isForbidden());
    }

    @Test
    void delete_notFound_returns404() throws Exception {
        mockMvc.perform(delete("/api/v1/testimonials/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + NotificationTestTokens.admin()))
                .andExpect(status().isNotFound());
    }

    // --- helpers ---

    private String create(String body) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/testimonials")
                        .header("Authorization", "Bearer " + NotificationTestTokens.admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.get("id").asText();
    }
}