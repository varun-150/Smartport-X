package com.smartportx.vessel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartportx.common.event.DomainEvent;
import com.smartportx.vessel.event.EventTransport;
import com.smartportx.vessel.repository.DischargeContainerRepository;
import com.smartportx.vessel.repository.DischargePlanRepository;
import com.smartportx.vessel.repository.VesselRepository;
import com.smartportx.vessel.repository.VesselScheduleRepository;
import com.smartportx.vessel.service.ContainerApiClient;
import com.smartportx.vessel.service.VesselClock;
import com.smartportx.vessel.service.YardApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(classes = {VesselTestSecurityConfig.class})
public abstract class BaseVesselIntegrationTest {

    protected static final Instant FIXED_NOW = Instant.parse("2026-06-01T00:00:00Z");

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected ObjectMapper objectMapper;
    @Autowired
    protected VesselRepository vessels;
    @Autowired
    protected VesselScheduleRepository schedules;
    @Autowired
    protected DischargePlanRepository plans;
    @Autowired
    protected DischargeContainerRepository planContainers;

    @MockitoBean
    protected EventTransport transport;
    @MockitoBean
    protected ContainerApiClient containerClient;
    @MockitoBean
    protected YardApiClient yardClient;
    @MockitoBean
    protected VesselClock clock;

    @BeforeEach
    void resetMocks() {
        Mockito.reset(transport, containerClient, yardClient, clock);
        when(clock.now()).thenReturn(FIXED_NOW);
    }

    protected ResultActions perform(RequestBuilder request) throws Exception {
        return mockMvc.perform(request);
    }

    protected ResultActions doPost(String url, String body, String token) throws Exception {
        return mockMvc.perform(withAuth(post(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body == null ? "" : body), token));
    }

    protected ResultActions doPut(String url, String body, String token) throws Exception {
        return mockMvc.perform(withAuth(put(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body == null ? "" : body), token));
    }

    protected ResultActions doPatch(String url, String body, String token) throws Exception {
        return mockMvc.perform(withAuth(patch(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body == null ? "" : body), token));
    }

    protected ResultActions doDelete(String url, String token) throws Exception {
        return mockMvc.perform(withAuth(delete(url), token));
    }

    protected ResultActions doGet(String url, String token) throws Exception {
        return mockMvc.perform(withAuth(get(url), token));
    }

    private MockHttpServletRequestBuilder withAuth(MockHttpServletRequestBuilder builder, String token) {
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return builder;
    }

    protected String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    protected JsonNode parse(String body) throws Exception {
        return objectMapper.readTree(body);
    }

    protected JsonNode callJson(ResultActions actions) throws Exception {
        return parse(actions.andReturn().getResponse().getContentAsString());
    }

    @SuppressWarnings("unchecked")
    protected List<DomainEvent<?>> capturedEvents() {
        return Mockito.mockingDetails(transport).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("publish"))
                .map(invocation -> (DomainEvent<?>) invocation.getArguments()[0])
                .collect(java.util.stream.Collectors.toList());
    }

    protected boolean hasEvent(String eventType) {
        return capturedEvents().stream().anyMatch(e -> e.eventType().equals(eventType));
    }

    protected <T> Optional<T> payloadOfType(String eventType, Class<T> type) {
        return capturedEvents().stream()
                .filter(e -> e.eventType().equals(eventType))
                .map(e -> e.payload())
                .filter(type::isInstance)
                .map(type::cast)
                .findFirst();
    }

    protected void stubContainerFound(String containerNumber, String status) {
        when(containerClient.findByContainerNumber(anyString())).thenReturn(Optional.of(
                new ContainerApiClient.ContainerInfo(
                        UUID.randomUUID(), containerNumber, "DRY", status, 2000.0, false)));
    }

    protected void stubContainerAbsent() {
        when(containerClient.findByContainerNumber(anyString())).thenReturn(Optional.empty());
    }
}