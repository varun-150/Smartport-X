package com.smartportx.notification.stomp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartportx.notification.NotificationTestSecurityConfig;
import com.smartportx.notification.NotificationTestTokens;
import com.smartportx.common.event.DomainEvent;
import com.smartportx.notification.service.NotificationEventRouter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@ContextConfiguration(classes = {NotificationTestSecurityConfig.class})
class StompWebSocketIntegrationTest {

    @Autowired
    private NotificationEventRouter eventRouter;

    @LocalServerPort
    private int port;

    private static WebSocketStompClient stompClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeAll
    static void setUpClient() {
        StandardWebSocketClient wsClient = new StandardWebSocketClient();
        SockJsClient sockJsClient = new SockJsClient(List.of(new WebSocketTransport(wsClient)));
        stompClient = new WebSocketStompClient(sockJsClient);
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());
    }

    private StompSession connect(String token) throws Exception {
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + token);
        return stompClient.connectAsync(
                "ws://localhost:" + port + "/ws",
                new WebSocketHttpHeaders(),
                connectHeaders,
                new StompSessionHandlerAdapter() {}
        ).get(5, TimeUnit.SECONDS);
    }

    @Test
    void connectWithAdminToken_andReceiveContainerEvent() throws Exception {
        BlockingQueue<Map<String, Object>> received = new LinkedBlockingQueue<>();
        StompSession session = connect(NotificationTestTokens.admin());

        StompHeaders subscribeHeaders = new StompHeaders();
        subscribeHeaders.setDestination("/topic/containers/MSKU123");
        session.subscribe(subscribeHeaders, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) { return Map.class; }
            @Override
            @SuppressWarnings("unchecked")
            public void handleFrame(StompHeaders headers, Object payload) {
                received.add((Map<String, Object>) payload);
            }
        });

        Thread.sleep(500);

        JsonNode payload = objectMapper.valueToTree(Map.of(
                "containerNumber", "MSKU123",
                "containerType", "40HC"
        ));
        eventRouter.handleEvent(DomainEvent.of("container.ContainerRegistered", "smartportx-container", payload));

        Map<String, Object> message = received.poll(5, TimeUnit.SECONDS);
        assertThat(message).isNotNull();
        assertThat(message.get("topic")).isEqualTo("/topic/containers/MSKU123");
        assertThat(message.get("title")).isEqualTo("Container Registered");
        assertThat(message.get("category")).isEqualTo("CONTAINER_EVENT");
        assertThat(message.get("severity")).isEqualTo("INFO");

        session.disconnect();
    }

    @Test
    void connectWithReaderToken_andReceiveAlert() throws Exception {
        BlockingQueue<Map<String, Object>> received = new LinkedBlockingQueue<>();
        StompSession session = connect(NotificationTestTokens.reader());

        StompHeaders subscribeHeaders = new StompHeaders();
        subscribeHeaders.setDestination("/topic/alerts");
        session.subscribe(subscribeHeaders, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) { return Map.class; }
            @Override
            @SuppressWarnings("unchecked")
            public void handleFrame(StompHeaders headers, Object payload) {
                received.add((Map<String, Object>) payload);
            }
        });

        Thread.sleep(500);

        JsonNode payload = objectMapper.valueToTree(Map.of(
                "code", "CG-01",
                "newStatus", "OUT_OF_SERVICE"
        ));
        eventRouter.handleEvent(DomainEvent.of("yard.EquipmentStatusChanged", "smartportx-yard", payload));

        Map<String, Object> message = received.poll(5, TimeUnit.SECONDS);
        assertThat(message).isNotNull();
        assertThat(message.get("topic")).isEqualTo("/topic/alerts");
        assertThat(message.get("category")).isEqualTo("ALERT");
        assertThat(message.get("severity")).isEqualTo("WARNING");

        session.disconnect();
    }

    @Test
    void connectWithInvalidToken_rejected() {
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer invalid-token");

        try {
            stompClient.connectAsync(
                    "ws://localhost:" + port + "/ws",
                    new WebSocketHttpHeaders(),
                    connectHeaders,
                    new StompSessionHandlerAdapter() {}
            ).get(5, TimeUnit.SECONDS);
            org.junit.jupiter.api.Assertions.fail("Connection should have been rejected");
        } catch (Exception e) {
            assertThat(e).isNotNull();
        }
    }

    @Test
    void connectWithNoToken_rejected() {
        StompHeaders connectHeaders = new StompHeaders();

        try {
            stompClient.connectAsync(
                    "ws://localhost:" + port + "/ws",
                    new WebSocketHttpHeaders(),
                    connectHeaders,
                    new StompSessionHandlerAdapter() {}
            ).get(5, TimeUnit.SECONDS);
            org.junit.jupiter.api.Assertions.fail("Connection should have been rejected");
        } catch (Exception e) {
            assertThat(e).isNotNull();
        }
    }

    @Test
    void subscribeToUnauthorizedTopic_rejected() throws Exception {
        StompSession session = connect(NotificationTestTokens.reader());

        StompHeaders subscribeHeaders = new StompHeaders();
        subscribeHeaders.setDestination("/topic/yard-zone/B1");

        try {
            session.subscribe(subscribeHeaders, new StompFrameHandler() {
                @Override
                public Type getPayloadType(StompHeaders headers) { return Map.class; }
                @Override
                public void handleFrame(StompHeaders headers, Object payload) {}
            });
            Thread.sleep(1000);
            assertThat(session.isConnected()).isFalse();
        } catch (Exception e) {
            assertThat(e).isNotNull();
        }

        if (session.isConnected()) {
            session.disconnect();
        }
    }

    @Test
    void yardManagerCanSubscribeToYardZone() throws Exception {
        BlockingQueue<Map<String, Object>> received = new LinkedBlockingQueue<>();
        StompSession session = connect(NotificationTestTokens.yardManager());

        StompHeaders subscribeHeaders = new StompHeaders();
        subscribeHeaders.setDestination("/topic/yard-zone/B1");
        session.subscribe(subscribeHeaders, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) { return Map.class; }
            @Override
            @SuppressWarnings("unchecked")
            public void handleFrame(StompHeaders headers, Object payload) {
                received.add((Map<String, Object>) payload);
            }
        });

        Thread.sleep(500);

        JsonNode payload = objectMapper.valueToTree(Map.of(
                "containerId", "MSKU456",
                "position", "B1-05-01",
                "blockCode", "B1"
        ));
        eventRouter.handleEvent(DomainEvent.of("yard.YardPositionAssigned", "smartportx-yard", payload));

        Map<String, Object> message = received.poll(5, TimeUnit.SECONDS);
        assertThat(message).isNotNull();
        assertThat(message.get("topic")).isEqualTo("/topic/yard-zone/B1");
        assertThat(message.get("category")).isEqualTo("YARD_UPDATE");
        assertThat(message.get("title")).isEqualTo("Yard Position Assigned");

        session.disconnect();
    }

    @Test
    void historyIsRecordedWhenEventBroadcast() throws Exception {
        BlockingQueue<Map<String, Object>> received = new LinkedBlockingQueue<>();
        StompSession session = connect(NotificationTestTokens.admin());

        StompHeaders subscribeHeaders = new StompHeaders();
        subscribeHeaders.setDestination("/topic/containers/MSKU789");
        session.subscribe(subscribeHeaders, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) { return Map.class; }
            @Override
            @SuppressWarnings("unchecked")
            public void handleFrame(StompHeaders headers, Object payload) {
                received.add((Map<String, Object>) payload);
            }
        });

        Thread.sleep(500);

        JsonNode payload = objectMapper.valueToTree(Map.of(
                "containerNumber", "MSKU789",
                "containerType", "20GP"
        ));
        eventRouter.handleEvent(DomainEvent.of("container.ContainerRegistered", "smartportx-container", payload));

        Map<String, Object> message = received.poll(5, TimeUnit.SECONDS);
        assertThat(message).isNotNull();
        assertThat((String) message.get("notificationId")).startsWith("ntf-");

        session.disconnect();
    }
}