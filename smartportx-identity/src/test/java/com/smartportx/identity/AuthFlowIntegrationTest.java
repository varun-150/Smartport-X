package com.smartportx.identity;

import com.smartportx.identity.domain.RoleName;
import com.smartportx.identity.web.dto.ChangePasswordRequest;
import com.smartportx.identity.web.dto.LoginResponse;
import com.smartportx.identity.web.dto.RefreshRequest;
import com.smartportx.identity.web.dto.RefreshResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthFlowIntegrationTest extends BaseIdentityIntegrationTest {

    private static final String PASSWORD2 = "N3w!Strong05";

    @Test
    void loginReturnsTokensAndUserProfile() throws Exception {
        String username = unique("loginu");
        createUser(username, PASSWORD, RoleName.TERMINAL_OP);

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new com.smartportx.identity.web.dto.LoginRequest(username, PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Correlation-Id"))
                .andReturn();

        LoginResponse login = parse(result, LoginResponse.class);
        assertThat(login.accessToken()).isNotBlank();
        assertThat(login.refreshToken()).isNotBlank();
        assertThat(login.expiresIn()).isEqualTo(900L);
        assertThat(login.tokenType()).isEqualTo("Bearer");
        assertThat(login.user().username()).isEqualTo(username);
        assertThat(login.user().role()).isEqualTo("TERMINAL_OP");
        assertThat(login.user().status()).isEqualTo("ACTIVE");
        assertThat(login.user().permissions()).contains("vessels.read", "vessels.write");
        assertThat(login.user().lastLoginAt()).isNotNull();
    }

    @Test
    void refreshRotatesTokensAndLogoutRevokesAll() throws Exception {
        String username = unique("rotat");
        createUser(username, PASSWORD, RoleName.READONLY);
        LoginResponse first = login(username, PASSWORD);

        MvcResult rotated = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RefreshRequest(first.refreshToken()))))
                .andExpect(status().isOk())
                .andReturn();
        RefreshResponse refresh = parse(rotated, RefreshResponse.class);
        assertThat(refresh.refreshToken()).isNotEqualTo(first.refreshToken());
        assertThat(refresh.accessToken()).isNotBlank();

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, bearer(refresh.accessToken())))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RefreshRequest(refresh.refreshToken()))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("credentials_invalid"));
    }

    @Test
    void reuseOfRotatedRefreshTokenRevokesFamilyAndLocks() throws Exception {
        String username = unique("reuse");
        createUser(username, PASSWORD, RoleName.READONLY);
        LoginResponse first = login(username, PASSWORD);

        MvcResult rotated = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RefreshRequest(first.refreshToken()))))
                .andExpect(status().isOk())
                .andReturn();
        RefreshResponse refresh = parse(rotated, RefreshResponse.class);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RefreshRequest(first.refreshToken()))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("credentials_invalid"));

        assertThat(performLogin(username, PASSWORD).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void changePasswordValidatesPolicyAndRevokesSessions() throws Exception {
        String username = unique("passw");
        createUser(username, PASSWORD, RoleName.GATE_OPERATOR);
        LoginResponse login = login(username, PASSWORD);

        mockMvc.perform(put("/api/v1/auth/password")
                        .header(HttpHeaders.AUTHORIZATION, bearer(login))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ChangePasswordRequest(PASSWORD, "weak"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"));

        mockMvc.perform(put("/api/v1/auth/password")
                        .header(HttpHeaders.AUTHORIZATION, bearer(login))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ChangePasswordRequest(WRONG_PASSWORD, PASSWORD2))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("credentials_invalid"));

        mockMvc.perform(put("/api/v1/auth/password")
                        .header(HttpHeaders.AUTHORIZATION, bearer(login))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ChangePasswordRequest(PASSWORD, PASSWORD2))))
                .andExpect(status().isNoContent());

        assertThat(performLogin(username, PASSWORD).getResponse().getStatus()).isEqualTo(401);
        LoginResponse relogin = login(username, PASSWORD2);
        assertThat(relogin.accessToken()).isNotBlank();

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RefreshRequest(login.refreshToken()))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("credentials_invalid"));

        mockMvc.perform(put("/api/v1/auth/password")
                        .header(HttpHeaders.AUTHORIZATION, bearer(relogin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ChangePasswordRequest(PASSWORD2, PASSWORD))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("password_policy"));
    }

    @Test
    void jwksExposesPublicKeyForVerification() throws Exception {
        mockMvc.perform(get("/api/v1/auth/jwks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys").isArray())
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
                .andExpect(jsonPath("$.keys[0].kid").isNotEmpty());
    }
}