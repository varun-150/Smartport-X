package com.smartportx.container;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartportx.container.web.dto.ContainerResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(classes = ContainerTestSecurityConfig.class)
public abstract class BaseContainerIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    protected ContainerResponse registerContainer() throws Exception {
        return registerContainer(TestContainerNumbers.next());
    }

    protected ContainerResponse registerContainer(String containerNumber) throws Exception {
        return registerContainer(containerNumber, "Maersk");
    }

    protected ContainerResponse registerContainer(String containerNumber, String owner) throws Exception {
        MvcResult result = performPost("/api/v1/containers", registerPayload(containerNumber, owner),
                TestTokens.admin()).andExpect(status().isCreated()).andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), ContainerResponse.class);
    }

    protected MvcResult changeStatus(UUID id, String status) throws Exception {
        return changeStatus(id, status, TestTokens.admin()).andReturn();
    }

    protected ResultActions changeStatus(UUID id, String status, String token) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("status", status);
        String json = objectMapper.writeValueAsString(body);
        return mockMvc.perform(put("/api/v1/containers/" + id + "/status")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    protected ResultActions performPost(String url, Map<String, Object> body, String token) throws Exception {
        return performPost(url, objectMapper.writeValueAsString(body), token);
    }

    protected ResultActions performPost(String url, String body, String token) throws Exception {
        return mockMvc.perform(post(url)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    protected ResultActions performGet(String url, String token) throws Exception {
        return mockMvc.perform(get(url)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    protected ResultActions performDelete(String url, String token) throws Exception {
        return mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(url)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    protected ResultActions performPut(String url, String body, String token) throws Exception {
        return mockMvc.perform(put(url)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    protected Map<String, Object> registerPayload(String containerNumber) {
        return registerPayload(containerNumber, "Maersk");
    }

    protected Map<String, Object> registerPayload(String containerNumber, String owner) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("containerNumber", containerNumber);
        payload.put("containerType", "40HC");
        payload.put("owner", owner);
        payload.put("weight", 25000.0);
        return payload;
    }
}