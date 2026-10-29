package com.smartportx.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartportx.identity.domain.RoleName;
import com.smartportx.identity.web.dto.LoginRequest;
import com.smartportx.identity.web.dto.LoginResponse;
import com.smartportx.identity.web.dto.RefreshRequest;
import com.smartportx.identity.web.dto.UserResponse;
import com.smartportx.identity.web.dto.UsersPage;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ContractConformanceIntegrationTest extends BaseIdentityIntegrationTest {

    @Test
    void loginResponseMatchesAuthContract() throws Exception {
        String user = unique("ct");
        createUser(user, PASSWORD, RoleName.TERMINAL_OP);

        LoginResponse resp = login(user, PASSWORD);
        assertThat(resp.accessToken()).isNotBlank();
        assertThat(resp.refreshToken()).isNotBlank();
        assertThat(resp.expiresIn()).isPositive();
        assertThat(resp.tokenType()).isEqualTo("Bearer");
        assertThat(resp.user().id()).isNotNull();
        assertThat(resp.user().username()).isEqualTo(user);
        assertThat(resp.user().displayName()).isNotBlank();
        assertThat(resp.user().email()).isNotBlank();
        assertThat(resp.user().role()).isEqualTo("TERMINAL_OP");
        assertThat(resp.user().permissions()).isNotNull();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginRequest(user, WRONG_PASSWORD))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshResponseMatchesAuthContract() throws Exception {
        String user = unique("ct");
        createUser(user, PASSWORD, RoleName.READONLY);
        LoginResponse login = login(user, PASSWORD);

        MvcResult result = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RefreshRequest(login.refreshToken()))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.has("accessToken")).isTrue();
        assertThat(body.has("refreshToken")).isTrue();
        assertThat(body.has("expiresIn")).isTrue();
        assertThat(body.has("tokenType")).isTrue();
        assertThat(body.get("tokenType").asText()).isEqualTo("Bearer");
    }

    @Test
    void logoutReturnsNoContent() throws Exception {
        String user = unique("ct");
        createUser(user, PASSWORD, RoleName.READONLY);
        LoginResponse login = login(user, PASSWORD);

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, bearer(login)))
                .andExpect(status().isNoContent());
    }

    @Test
    void usersListEnvelopeMatchesContract() throws Exception {
        String admin = unique("ctadmin");
        createUser(admin, ADMIN_PASSWORD, RoleName.ADMIN);
        LoginResponse adminLogin = login(admin, ADMIN_PASSWORD);

        MvcResult result = mockMvc.perform(get("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminLogin)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.has("data")).isTrue();
        assertThat(body.has("pagination")).isTrue();
        JsonNode pagination = body.get("pagination");
        assertThat(pagination.has("nextCursor")).isTrue();
        assertThat(pagination.get("nextCursor").isNull() || pagination.get("nextCursor").isTextual()).isTrue();
        assertThat(pagination.has("hasMore")).isTrue();
        assertThat(pagination.has("totalCount")).isTrue();
        assertThat(pagination.get("limit").asInt()).isEqualTo(25);
        JsonNode item = body.get("data").get(0);
        assertThat(item.has("id")).isTrue();
        assertThat(item.has("username")).isTrue();
        assertThat(item.has("displayName")).isTrue();
        assertThat(item.has("email")).isTrue();
        assertThat(item.has("role")).isTrue();
        assertThat(item.has("status")).isTrue();
        assertThat(item.has("lastLoginAt")).isTrue();
        assertThat(item.has("createdAt")).isTrue();
        assertThat(item.has("updatedAt")).isTrue();
    }

    @Test
    void filterParamsUseContractNames() throws Exception {
        String admin = unique("ctadmin");
        createUser(admin, ADMIN_PASSWORD, RoleName.ADMIN);
        LoginResponse adminLogin = login(admin, ADMIN_PASSWORD);
        String ro = unique("ctro");
        createUser(ro, PASSWORD, RoleName.READONLY);
        String op = unique("ctop");
        createUser(op, PASSWORD, RoleName.TERMINAL_OP);

        MvcResult role = mockMvc.perform(get("/api/v1/users")
                        .queryParam("filter[role]", "READONLY")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminLogin)))
                .andExpect(status().isOk())
                .andReturn();
        UsersPage rolePage = parse(role, UsersPage.class);
        assertThat(rolePage.data()).isNotEmpty();
        assertThat(rolePage.data()).allSatisfy(u -> assertThat(u.role()).isEqualTo("READONLY"));

        MvcResult search = mockMvc.perform(get("/api/v1/users")
                        .queryParam("filter[search]", ro)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminLogin)))
                .andExpect(status().isOk())
                .andReturn();
        UsersPage searchPage = parse(search, UsersPage.class);
        assertThat(searchPage.data()).extracting(UserResponse::username).contains(ro);
    }

    @Test
    void sortDefaultsToCreatedAtDesc() throws Exception {
        String admin = unique("ctadmin");
        createUser(admin, ADMIN_PASSWORD, RoleName.ADMIN);
        LoginResponse adminLogin = login(admin, ADMIN_PASSWORD);
        String a = unique("cta");
        String b = unique("ctb");
        createUser(a, PASSWORD, RoleName.READONLY);
        createUser(b, PASSWORD, RoleName.READONLY);

        MvcResult result = mockMvc.perform(get("/api/v1/users")
                        .queryParam("filter[search]", "ct")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminLogin)))
                .andExpect(status().isOk())
                .andReturn();
        UsersPage page = parse(result, UsersPage.class);
        List<Instant> createdAt = page.data().stream().map(UserResponse::createdAt).toList();
        assertThat(createdAt).isSortedAccordingTo((x, y) -> y.compareTo(x));
        assertThat(page.data().getFirst().username()).isEqualTo(b);
    }

    @Test
    void sortCreatedAtAscReturnsOldestFirst() throws Exception {
        String admin = unique("ctadmin");
        createUser(admin, ADMIN_PASSWORD, RoleName.ADMIN);
        LoginResponse adminLogin = login(admin, ADMIN_PASSWORD);
        String a = unique("cta");
        String b = unique("ctb");
        createUser(a, PASSWORD, RoleName.READONLY);
        createUser(b, PASSWORD, RoleName.READONLY);

        MvcResult result = mockMvc.perform(get("/api/v1/users")
                        .queryParam("filter[search]", "ct")
                        .queryParam("sort", "createdAt,asc")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminLogin)))
                .andExpect(status().isOk())
                .andReturn();
        UsersPage page = parse(result, UsersPage.class);
        List<Instant> createdAt = page.data().stream().map(UserResponse::createdAt).toList();
        assertThat(createdAt).isSorted();
    }

    @Test
    void unsupportedSortFieldIsBadRequest() throws Exception {
        String admin = unique("ctadmin");
        createUser(admin, ADMIN_PASSWORD, RoleName.ADMIN);
        LoginResponse adminLogin = login(admin, ADMIN_PASSWORD);

        mockMvc.perform(get("/api/v1/users")
                        .queryParam("sort", "username,asc")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminLogin)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_query"));
    }
}