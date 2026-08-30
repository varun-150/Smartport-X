package com.smartportx.identity;

import com.smartportx.identity.domain.RoleName;
import com.smartportx.identity.web.dto.CreateUserRequest;
import com.smartportx.identity.web.dto.LoginResponse;
import com.smartportx.identity.web.dto.UpdateUserRequest;
import com.smartportx.identity.web.dto.UserResponse;
import com.smartportx.identity.web.dto.Status;
import com.smartportx.identity.web.dto.UsersPage;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminUserManagementIntegrationTest extends BaseIdentityIntegrationTest {

    @Test
    void userManagementRequiresAdminRole() throws Exception {
        String viewer = unique("viewer");
        createUser(viewer, PASSWORD, RoleName.READONLY);
        LoginResponse viewerLogin = login(viewer, PASSWORD);
        String username = unique("someone");

        mockMvc.perform(post("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(viewerLogin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(sampleCreate(username))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("access_denied"));
    }

    @Test
    void adminCreatesUsersAndListsWithCursorPagination() throws Exception {
        String admin = unique("admin");
        createUser(admin, ADMIN_PASSWORD, RoleName.ADMIN);
        LoginResponse adminLogin = login(admin, ADMIN_PASSWORD);

        for (int i = 0; i < 12; i++) {
            mockMvc.perform(post("/api/v1/users")
                            .header(HttpHeaders.AUTHORIZATION, bearer(adminLogin))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(sampleCreate("c" + admin + "_" + i))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("ACTIVE"));
        }

        List<String> collectedIds = new ArrayList<>();
        String cursor = null;
        int pageCount = 0;
        do {
            mockMvc.perform(get("/api/v1/users?limit=5" + (cursor == null ? "" : "&cursor=" + cursor))
                            .header(HttpHeaders.AUTHORIZATION, bearer(adminLogin)))
                    .andExpect(status().isOk());
            MvcResult page = mockMvc.perform(get("/api/v1/users?limit=5" + (cursor == null ? "" : "&cursor=" + cursor))
                            .header(HttpHeaders.AUTHORIZATION, bearer(adminLogin)))
                    .andExpect(status().isOk())
                    .andReturn();
            UsersPage body = parse(page, UsersPage.class);
            assertThat(body.data()).hasSizeLessThanOrEqualTo(5);
            body.data().forEach(u -> collectedIds.add(u.id().toString()));
            assertThat(body.pagination().totalCount()).isGreaterThanOrEqualTo(12);
            cursor = body.pagination().hasMore() ? body.pagination().nextCursor() : null;
            pageCount++;
        } while (cursor != null);

        assertThat(collectedIds).doesNotHaveDuplicates();
        assertThat(pageCount).isGreaterThanOrEqualTo(3);
    }

    @Test
    void searchAndRoleFilter() throws Exception {
        String admin = unique("admin");
        createUser(admin, ADMIN_PASSWORD, RoleName.ADMIN);
        LoginResponse adminLogin = login(admin, ADMIN_PASSWORD);
        String viewer = unique("viewer");
        createUser(viewer, PASSWORD, RoleName.READONLY);

        mockMvc.perform(get("/api/v1/users?limit=10")
                        .queryParam("filter[role]", "READONLY")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminLogin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].role").value("READONLY"));

        MvcResult search = mockMvc.perform(get("/api/v1/users?limit=10")
                        .queryParam("filter[search]", viewer)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminLogin)))
                .andExpect(status().isOk())
                .andReturn();
        UsersPage page = parse(search, UsersPage.class);
        assertThat(page.data()).extracting(UserResponse::username).contains(viewer);
    }

    @Test
    void adminGetsUpdatesDisablesReenablesAndDeletes() throws Exception {
        String admin = unique("admin");
        createUser(admin, ADMIN_PASSWORD, RoleName.ADMIN);
        LoginResponse adminLogin = login(admin, ADMIN_PASSWORD);
        String target = unique("target");
        UserResponse created = createUser(target, PASSWORD, RoleName.GATE_OPERATOR);

        mockMvc.perform(get("/api/v1/users/" + created.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminLogin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(target));

        mockMvc.perform(put("/api/v1/users/" + created.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminLogin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new UpdateUserRequest(null, "Renamed", RoleName.YARD_MANAGER, Status.ACTIVE))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("YARD_MANAGER"))
                .andExpect(jsonPath("$.displayName").value("Renamed"));

        mockMvc.perform(patch("/api/v1/users/" + created.id() + "/disable")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminLogin)))
                .andExpect(status().isNoContent());
        assertThat(performLogin(target, PASSWORD).getResponse().getStatus()).isEqualTo(401);

        mockMvc.perform(put("/api/v1/users/" + created.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminLogin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new UpdateUserRequest(null, null, null, Status.ACTIVE))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        assertThat(performLogin(target, PASSWORD).getResponse().getStatus()).isEqualTo(200);

        mockMvc.perform(delete("/api/v1/users/" + created.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminLogin)))
                .andExpect(status().isNoContent());
        assertThat(performLogin(target, PASSWORD).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void duplicateUsernameAndEmailRejectedWithConflict() throws Exception {
        String admin = unique("admin");
        createUser(admin, ADMIN_PASSWORD, RoleName.ADMIN);
        LoginResponse adminLogin = login(admin, ADMIN_PASSWORD);
        String username = unique("dupe");

        mockMvc.perform(post("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminLogin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(sampleCreate(username))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminLogin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(sampleCreate(username))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("conflict"));

        mockMvc.perform(get("/api/v1/users/" + UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminLogin)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("not_found"));
    }

    private CreateUserRequest sampleCreate(String username) {
        return new CreateUserRequest(username, username + "@smartportx.local", "User " + username,
                PASSWORD, RoleName.READONLY, Status.ACTIVE);
    }
}