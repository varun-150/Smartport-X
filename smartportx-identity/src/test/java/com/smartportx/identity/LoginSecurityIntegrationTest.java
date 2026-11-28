package com.smartportx.identity;

import com.smartportx.identity.domain.RoleName;
import com.smartportx.identity.repository.UserRepository;
import com.smartportx.identity.web.dto.LoginResponse;
import com.smartportx.identity.web.dto.UserResponse;
import com.smartportx.identity.web.dto.Status;
import com.smartportx.identity.web.dto.UpdateUserRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "smartportx.security.login-rate-limit=1000")
class LoginSecurityIntegrationTest extends BaseIdentityIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void progressiveLockoutAfterFiveFailuresAndAdminUnlock() throws Exception {
        String username = unique("locka");
        createUser(username, PASSWORD, RoleName.READONLY);

        for (int i = 0; i < 5; i++) {
            postLogin(username, WRONG_PASSWORD)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("credentials_invalid"))
                    .andExpect(jsonPath("$.instance").exists());
        }

        postLogin(username, PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("too_many_attempts"));

        String admin = unique("adminu");
        createUser(admin, ADMIN_PASSWORD, RoleName.ADMIN);
        LoginResponse adminLogin = login(admin, ADMIN_PASSWORD);

        UUID userId = userRepository.findByUsernameIgnoreCase(username).orElseThrow().getId();
        mockMvc.perform(put("/api/v1/users/" + userId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminLogin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new UpdateUserRequest(null, null, null, Status.ACTIVE))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        assertThat(performLogin(username, PASSWORD).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void disabledUserCannotLogIn() throws Exception {
        String admin = unique("adminu");
        createUser(admin, ADMIN_PASSWORD, RoleName.ADMIN);
        LoginResponse adminLogin = login(admin, ADMIN_PASSWORD);
        UserResponse target = createUser(unique("offli"), PASSWORD, RoleName.READONLY);

        mockMvc.perform(patch("/api/v1/users/" + target.id() + "/disable")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminLogin)))
                .andExpect(status().isNoContent());

        postLogin(target.username(), PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("credentials_invalid"));
    }

    @Test
    void unknownUserAndTamperedTokenRejected() throws Exception {
        postLogin(unique("nobody"), PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("credentials_invalid"));

        mockMvc.perform(get("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer eyJhbGciOiJSUzI1NiJ9.tampered.signature"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("unauthorized"));
    }

    private org.springframework.test.web.servlet.ResultActions postLogin(String username, String password) throws Exception {
        return mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new com.smartportx.identity.web.dto.LoginRequest(username, password))));
    }
}