package com.smartportx.gate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartportx.common.event.DomainEvent;
import com.smartportx.gate.domain.Appointment;
import com.smartportx.gate.domain.AppointmentStatus;
import com.smartportx.gate.domain.AppointmentType;
import com.smartportx.gate.event.EventTransport;
import com.smartportx.gate.repository.AppointmentQuotaRepository;
import com.smartportx.gate.repository.AppointmentRepository;
import com.smartportx.gate.repository.GateTransactionRepository;
import com.smartportx.gate.repository.QueueStatusRepository;
import com.smartportx.gate.service.ContainerClient;
import com.smartportx.gate.service.CustomsClient;
import com.smartportx.gate.service.GateClock;
import com.smartportx.gate.service.YardClient;
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
import java.time.ZoneOffset;
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

/**
 * Shared test harness for gate integrations. The {@code @ConditionalOnMissingBean}
 * HTTP provider clients, the {@link EventTransport} and the time authority are all
 * replaced with mocks so a test exercises only the gate service itself. Each test
 * class is {@code @Transactional} so the shared Postgres schema stays clean.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(classes = {GateTestSecurityConfig.class})
public abstract class BaseGateIntegrationTest {

    protected static final Instant FIXED_NOW = Instant.parse("2026-06-01T00:00:00Z");

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected ObjectMapper objectMapper;
    @Autowired
    protected AppointmentRepository appointments;
    @Autowired
    protected GateTransactionRepository transactions;
    @Autowired
    protected AppointmentQuotaRepository quotasRepo;
    @Autowired
    protected QueueStatusRepository queueSnapshots;

    @MockitoBean
    protected EventTransport transport;
    @MockitoBean
    protected ContainerClient containerClient;
    @MockitoBean
    protected YardClient yardClient;
    @MockitoBean
    protected CustomsClient customsClient;
    @MockitoBean
    protected GateClock clock;

    @BeforeEach
    void resetMocks() {
        Mockito.reset(transport, containerClient, yardClient, customsClient, clock);
        when(clock.now()).thenReturn(FIXED_NOW);
        when(clock.todayUtc()).thenReturn(FIXED_NOW.atZone(ZoneOffset.UTC).toLocalDate());
    }

    // ----- clock / provider stubs -----

    protected void freezeNow(Instant now) {
        when(clock.now()).thenReturn(now);
        when(clock.todayUtc()).thenReturn(now.atZone(ZoneOffset.UTC).toLocalDate());
    }

    protected void stubContainer(ContainerClient.ContainerInfo info) {
        when(containerClient.findByContainerNumber(anyString())).thenReturn(Optional.ofNullable(info));
    }

    protected void stubContainerAbsent() {
        when(containerClient.findByContainerNumber(anyString())).thenReturn(Optional.empty());
    }

    protected ContainerClient.ContainerInfo container(String status, Double weightKg) {
        return new ContainerClient.ContainerInfo("MSKU1234567", "DRY", weightKg, false, status);
    }

    protected void stubYard(boolean assigned) {
        when(yardClient.isPositionAssigned(anyString())).thenReturn(Optional.of(assigned));
    }

    protected void stubYardUnknown() {
        when(yardClient.isPositionAssigned(anyString())).thenReturn(Optional.empty());
    }

    protected void stubCustoms(String status) {
        when(customsClient.statusFor(anyString()))
                .thenReturn(Optional.of(new CustomsClient.CustomsControlStatus(status)));
    }

    protected void stubCustomsUnknown() {
        when(customsClient.statusFor(anyString())).thenReturn(Optional.empty());
    }

    // ----- helpers -----

    protected Appointment makeAppointment(AppointmentType type, Instant slot) {
        return makeAppointment(type, slot, AppointmentStatus.CONFIRMED);
    }

    protected Appointment makeAppointment(AppointmentType type, Instant slot, AppointmentStatus status) {
        Appointment a = new Appointment();
        a.setConfirmationNumber("APT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        a.setTruckingCompany("FreightWorks");
        a.setTruckLicensePlate("ABC-1234");
        a.setDriverName("John Doe");
        a.setDriverLicense("DL-1234567");
        a.setAppointmentType(type);
        a.setContainerNumber("MSKU1234567");
        a.setPreferredTimeSlot(slot);
        a.setDurationMinutes(30);
        a.setStatus(status);
        return appointments.save(a);
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
}