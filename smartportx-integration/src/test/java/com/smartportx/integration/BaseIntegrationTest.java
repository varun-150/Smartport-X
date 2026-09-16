package com.smartportx.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartportx.common.event.DomainEvent;
import com.smartportx.integration.event.EventTransport;
import com.smartportx.integration.repository.CustomsStatusRepository;
import com.smartportx.integration.repository.EdifactMessageRepository;
import com.smartportx.integration.repository.IntegrationLogRepository;
import com.smartportx.integration.repository.IotReadingRepository;
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

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Shared harness for integration-service HTTP tests. The {@link EventTransport} is replaced
 * with a mock so tests assert on emitted domain events without RabbitMQ. Test classes are
 * {@code @Transactional} so the shared Postgres schema stays clean between tests.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(classes = {IntegrationTestSecurityConfig.class})
public abstract class BaseIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected ObjectMapper objectMapper;
    @Autowired
    protected EdifactMessageRepository edifactMessages;
    @Autowired
    protected IotReadingRepository iotReadings;
    @Autowired
    protected CustomsStatusRepository customs;
    @Autowired
    protected IntegrationLogRepository integrationLogs;

    @MockitoBean
    protected EventTransport transport;

    @BeforeEach
    void resetMocks() {
        Mockito.reset(transport);
    }

    // ----- sample EDIFACT interchange -----

    protected static String coprar(String interchangeRef, String messageRef, String containerNumber) {
        return "UNB+UNOC:3+ZIM+NYKANG+260101:0000+" + interchangeRef + "+1'"
                + "UNH+" + messageRef + "+COPRAR:D:95B:UN:1.0'"
                + "BGM+115+" + messageRef + "+9'"
                + "NAD+CA+ZIMU+ZIM+NL:311'"
                + "TDT+20+++MAERSK+HONGKONG+9V5951'"
                + "LOC+11+USNYC'"
                + "LOC+88+TERM-NY'"
                + "DTM+132:20260115:1015+203'"
                + "EQD+CN+45G1+" + containerNumber + "+5'"
                + "UNT+10+" + messageRef + "'"
                + "UNZ+1+" + interchangeRef + "'";
    }

    protected static String coparn(String interchangeRef, String messageRef, String containerNumber) {
        return "UNB+UNOC:3+CNGP+PORTX+260102:0000+" + interchangeRef + "+1'"
                + "UNH+" + messageRef + "+COPARN:D:95B:UN:1.0'"
                + "BGM+180+" + messageRef + "+7'"
                + "NAD+CA+CNGP+CHINA SHIPPING+CN:156'"
                + "TDT+20+++EVER GIVEN+EGIVEN+9825963'"
                + "LOC+11+CNSHA'"
                + "LOC+88+TERM-NY'"
                + "DTM+132:20260120:1015+203'"
                + "EQD+CN+42G1+" + containerNumber + "+5'"
                + "UNT+9+" + messageRef + "'"
                + "UNZ+1+" + interchangeRef + "'";
    }

    // ----- helpers -----

    protected ResultActions doPost(String url, String body, String token) throws Exception {
        return mockMvc.perform(withAuth(post(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body == null ? "" : body), token));
    }

    protected ResultActions doGet(String url, String token) throws Exception {
        return mockMvc.perform(withAuth(get(url), token));
    }

    protected ResultActions perform(RequestBuilder request) throws Exception {
        return mockMvc.perform(request);
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
                .collect(Collectors.toList());
    }

    protected boolean hasEvent(String eventType) {
        return capturedEvents().stream().anyMatch(e -> e.eventType().equals(eventType));
    }

    @SuppressWarnings("unchecked")
    protected <T> Optional<T> payloadOfType(String eventType, Class<T> type) {
        return capturedEvents().stream()
                .filter(e -> e.eventType().equals(eventType))
                .map(e -> (T) e.payload())
                .filter(type::isInstance)
                .findFirst();
    }
}
