package com.smartportx.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartportx.identity.domain.RoleName;
import com.smartportx.identity.service.UserService;
import com.smartportx.identity.web.dto.CreateUserRequest;
import com.smartportx.identity.web.dto.LoginRequest;
import com.smartportx.identity.web.dto.LoginResponse;
import com.smartportx.identity.web.dto.Status;
import com.smartportx.identity.web.dto.UserResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class BaseIdentityIntegrationTest {

    protected static final String PASSWORD = "Str0ng!Pass";
    protected static final String ADMIN_PASSWORD = "Admin1!Pass";
    protected static final String WRONG_PASSWORD = "Wrong!Pass";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected UserService userService;

    protected String unique(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    protected UserResponse createUser(String username, String password, RoleName role) {
        return userService.create(
                new CreateUserRequest(username, username + "@smartportx.local", "User " + username,
                        password, role, Status.ACTIVE), "test");
    }

    protected String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    protected LoginResponse login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginRequest(username, password))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(StandardCharsets.UTF_8),
                LoginResponse.class);
    }

    protected MvcResult performLogin(String username, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginRequest(username, password))))
                .andReturn();
    }

    protected String bearer(LoginResponse login) {
        return bearer(login.accessToken());
    }

    protected String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }

    protected <T> T parse(MvcResult result, Class<T> type) throws Exception {
        return objectMapper.readValue(result.getResponse().getContentAsString(StandardCharsets.UTF_8), type);
    }
}