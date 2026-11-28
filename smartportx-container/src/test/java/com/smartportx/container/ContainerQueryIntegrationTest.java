package com.smartportx.container;

import com.smartportx.container.web.dto.ContainerResponse;
import com.smartportx.container.web.dto.ContainersPage;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ContainerQueryIntegrationTest extends BaseContainerIntegrationTest {

    @Test
    void get_returnsContainer_200() throws Exception {
        ContainerResponse registered = registerContainer();

        ContainerResponse fetched = objectMapper.readValue(
                performGet("/api/v1/containers/" + registered.id(), TestTokens.reader())
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString(),
                ContainerResponse.class);

        assertThat(fetched.containerNumber()).isEqualTo(registered.containerNumber());
        assertThat(fetched.status()).hasToString("REGISTERED");
    }

    @Test
    void get_unknownContainer_404() throws Exception {
        performGet("/api/v1/containers/" + UUID.randomUUID(), TestTokens.reader())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("not_found"));
    }

    @Test
    void getByNumber_extension_200() throws Exception {
        ContainerResponse registered = registerContainer();

        ContainerResponse fetched = objectMapper.readValue(
                performGet("/api/v1/containers/number/" + registered.containerNumber(), TestTokens.reader())
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString(),
                ContainerResponse.class);

        assertThat(fetched.id()).isEqualTo(registered.id());
    }

    @Test
    void list_excludesRemovedContainers() throws Exception {
        ContainerResponse kept = registerContainer();
        ContainerResponse removed = registerContainer();
        performDelete("/api/v1/containers/" + removed.id(), TestTokens.admin())
                .andExpect(status().isNoContent());

        ContainersPage page = objectMapper.readValue(
                performGet("/api/v1/containers", TestTokens.reader())
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString(),
                ContainersPage.class);

        assertThat(page.data()).anyMatch(c -> c.id().equals(kept.id()));
        assertThat(page.data()).noneMatch(c -> c.id().equals(removed.id()));
    }

    @Test
    void list_cursorPagination_isDeterministicPerOwner() throws Exception {
        String owner = "Owner-" + UUID.randomUUID();
        ContainerResponse first = registerContainer(TestContainerNumbers.next(), owner);
        ContainerResponse second = registerContainer(TestContainerNumbers.next(), owner);

        ContainersPage page = objectMapper.readValue(
                performGet("/api/v1/containers?filter[owner]=" + owner + "&limit=1&sort=createdAt,asc",
                        TestTokens.reader())
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString(),
                ContainersPage.class);

        assertThat(page.pagination().hasMore()).isTrue();
        assertThat(page.pagination().nextCursor()).isNotBlank();
        assertThat(page.data()).hasSize(1);
        assertThat(page.data().get(0).id()).isIn(first.id(), second.id());

        ContainersPage page2 = objectMapper.readValue(
                performGet("/api/v1/containers?filter[owner]=" + owner
                                + "&limit=1&sort=createdAt,asc&cursor=" + page.pagination().nextCursor(),
                        TestTokens.reader())
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString(),
                ContainersPage.class);

        assertThat(page2.data()).hasSize(1);
        assertThat(page2.data().get(0).id()).isNotEqualTo(page.data().get(0).id());
    }

    @Test
    void list_invalidSortField_400() throws Exception {
        performGet("/api/v1/containers?sort=weight,desc", TestTokens.reader())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_query"));
    }

    @Test
    void search_byContainerNumberWildcard() throws Exception {
        ContainerResponse registered = registerContainer();
        Map<String, Object> query = new HashMap<>();
        query.put("containerNumber", registered.containerNumber().substring(0, 4) + "*");

        ContainersPage page = objectMapper.readValue(
                performPost("/api/v1/containers/search",
                        objectMapper.writeValueAsString(query), TestTokens.reader())
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString(),
                ContainersPage.class);

        assertThat(page.data()).extracting(ContainerResponse::containerNumber)
                .contains(registered.containerNumber());
    }

    @Test
    void search_sortByWeightDesc() throws Exception {
        Map<String, Object> query = new HashMap<>();
        query.put("sort", "weight,desc");
        ContainersPage page = objectMapper.readValue(
                performPost("/api/v1/containers/search",
                        objectMapper.writeValueAsString(query), TestTokens.reader())
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString(),
                ContainersPage.class);

        for (int i = 1; i < page.data().size(); i++) {
            assertThat(page.data().get(i - 1).weight() >= page.data().get(i).weight()).isTrue();
        }
    }

    @Test
    void search_rejectsCustomsStatus_400() throws Exception {
        Map<String, Object> query = new HashMap<>();
        query.put("customsStatus", "CLEARED");

        performPost("/api/v1/containers/search",
                objectMapper.writeValueAsString(query), TestTokens.reader())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_query"));
    }

    @Test
    void search_whenDangerousGoodsFilterMatches() throws Exception {
        Map<String, Object> payload = registerPayload(TestContainerNumbers.next(), "DG-Owner-" + UUID.randomUUID());
        Map<String, Object> dg = new HashMap<>();
        dg.put("unNumber", "UN1203");
        dg.put("properShippingName", "Gasoline");
        dg.put("hazardClass", "3");
        payload.put("dangerousGoods", dg);
        ContainerResponse registered = objectMapper.readValue(
                performPost("/api/v1/containers", objectMapper.writeValueAsString(payload), TestTokens.admin())
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString(),
                ContainerResponse.class);

        Map<String, Object> query = new HashMap<>();
        query.put("dangerousGoods", true);
        ContainersPage page = objectMapper.readValue(
                performPost("/api/v1/containers/search",
                        objectMapper.writeValueAsString(query), TestTokens.reader())
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString(),
                ContainersPage.class);

        assertThat(page.data()).anyMatch(c -> c.id().equals(registered.id()));
    }

    @Test
    void list_filterByStatus_isolation() throws Exception {
        ContainerResponse moved = registerContainer();
        changeStatus(moved.id(), "AT_GATE");

        ContainersPage page = objectMapper.readValue(
                performGet("/api/v1/containers?filter[status]=AT_GATE", TestTokens.reader())
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString(),
                ContainersPage.class);

        assertThat(page.data()).anyMatch(c -> c.id().equals(moved.id()));
        assertThat(page.data()).allMatch(c -> c.status().name().equals("AT_GATE"));
    }
}