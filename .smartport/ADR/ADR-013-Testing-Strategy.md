# ADR-013: Testing Strategy — TDD with Integration Tests

**Status:** PROPOSED
**Date:** 2026-08-26
**Deciders:** Architect, Lead

---

## Context

The SmartPort platform requires a testing strategy that satisfies multiple needs:

1. **Evidence-based completion** — the SOA course requires demonstrated evidence that features work, not just assertions.
2. **Quality gates** — automated tests enforce code quality and prevent regressions.
3. **Design feedback** — writing tests first drives better API and service design.
4. **Integration validation** — services must work together correctly (API contracts, message flows, database operations).
5. **Team coordination** — tests provide a shared understanding of expected behavior.

The team must decide:
- What testing levels to implement.
- What tools and frameworks to use.
- How tests fit into the development workflow.
- How to test inter-service communication.
- How to test asynchronous message flows.

Key constraint: tests must run within the CI pipeline and complete in a reasonable time (<10 minutes).

## Decision

We adopt **Test-Driven Development (TDD) for business logic** combined with **integration tests for API and workflow validation**.

### Testing Pyramid

```
                    ╱╲
                   ╱  ╲
                  ╱ E2E╲          5% — Critical user flows
                 ╱──────╲
                ╱        ╲
               ╱Integration╲      25% — API contracts, DB, messaging
              ╱──────────────╲
             ╱                ╲
            ╱    Unit Tests    ╲   70% — Business logic, domain models
           ╱────────────────────╲
```

### Testing Levels

| Level | Scope | Tools | Execution Speed | Coverage Target |
|-------|-------|-------|----------------|----------------|
| Unit | Individual classes, methods | JUnit 5, Mockito, AssertJ | <1ms per test | 80%+ |
| Integration | API endpoints, database, messaging | Spring Boot Test, Testcontainers, MockMvc | <5s per test | 70%+ API coverage |
| E2E | Complete user flows | Docker Compose + HTTP client | <30s per test | Critical paths |

### Unit Testing (TDD Approach)

```java
// TDD Cycle: Red → Green → Refactor

// Step 1: Write failing test (Red)
@Test
@DisplayName("Should calculate berth utilization correctly")
void shouldCalculateBerthUtilization() {
    // Given
    Berth berth = BerthTestFixture.createBerth("BERTH-A1", 100.0);
    List<Reservation> reservations = List.of(
        ReservationTestFixture.create("R1", Duration.ofHours(8)),
        ReservationTestFixture.create("R2", Duration.ofHours(4))
    );

    // When
    double utilization = BerthService.calculateUtilization(berth, reservations, Duration.ofDays(1));

    // Then
    assertThat(utilization).isCloseTo(0.50, within(0.01)); // 12/24 hours
}

// Step 2: Implement minimum code to pass (Green)
public static double calculateUtilization(Berth berth, List<Reservation> reservations, Duration period) {
    long totalReservedMinutes = reservations.stream()
        .mapToLong(r -> r.getDuration().toMinutes())
        .sum();
    return (double) totalReservedMinutes / period.toMinutes();
}

// Step 3: Refactor without changing behavior
// Extract method, improve naming, etc.
```

### Integration Testing

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class VesselControllerIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine")
        .withDatabaseName("smartport_test")
        .withUsername("test")
        .withPassword("test");

    @Container
    static GenericContainer<?> rabbitmq = new GenericContainer<>("rabbitmq:3.13-management-alpine")
        .withExposedPorts(5672);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.rabbitmq.host", rabbitmq::getHost);
        registry.add("spring.rabbitmq.port", () -> rabbitmq.getMappedPort(5672));
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("POST /api/vessels should create vessel and return 201")
    void shouldCreateVessel() throws Exception {
        // Given
        CreateVesselRequest request = new CreateVesselRequest(
            "IMO1234567", "MV SmartPort", "CONTAINER_SHIP"
        );

        // When & Then
        mockMvc.perform(post("/api/vessels")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.imoNumber").value("IMO1234567"))
            .andExpect(jsonPath("$.name").value("MV SmartPort"))
            .andExpect(jsonPath("$.id").isNotEmpty());
    }

    @Test
    @DisplayName("POST /api/vessels with duplicate IMO should return 409")
    void shouldRejectDuplicateVessel() throws Exception {
        // Given
        CreateVesselRequest request = new CreateVesselRequest(
            "IMO1234567", "MV SmartPort", "CONTAINER_SHIP"
        );
        vesselService.createVessel(request); // Pre-create

        // When & Then
        mockMvc.perform(post("/api/vessels")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error").value("Vessel with IMO number already exists"));
    }

    @Test
    @DisplayName("GET /api/vessels should return paginated results")
    void shouldReturnPaginatedVessels() throws Exception {
        // Given — create 15 vessels
        for (int i = 0; i < 15; i++) {
            vesselService.createVessel(new CreateVesselRequest(
                "IMO" + String.format("%07d", i), "Vessel " + i, "BULK_CARRIER"
            ));
        }

        // When & Then
        mockMvc.perform(get("/api/vessels")
                .param("page", "0")
                .param("size", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray())
            .andExpect(jsonPath("$.content.length()").value(10))
            .andExpect(jsonPath("$.totalElements").value(15))
            .andExpect(jsonPath("$.totalPages").value(2));
    }
}
```

### Message Flow Testing

```java
@SpringBootTest
@Testcontainers
class VesselArrivalEventTest {

    @Container
    static GenericContainer<?> rabbitmq = new GenericContainer<>("rabbitmq:3.13-management-alpine")
        .withExposedPorts(5672);

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private vesselEventHandler vesselEventHandler;

    @Test
    @DisplayName("Vessel arrival event should trigger berth reservation check")
    void shouldHandleVesselArrivalEvent() throws Exception {
        // Given
        VesselArrivalEvent event = new VesselArrivalEvent(
            "IMO1234567", "BERTH-A1", Instant.now()
        );

        // When
        rabbitTemplate.convertAndSend("smartport.events", "vessel.arrival", event);

        // Then — verify consumer received and processed the event
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            verify(vesselEventHandler).handleArrival(event);
        });
    }
}
```

### Test Fixtures

```java
public class VesselTestFixture {

    public static Vessel createDefault() {
        return Vessel.builder()
            .id(UUID.randomUUID())
            .imoNumber("IMO" + RandomStringUtils.randomNumeric(7))
            .name("MV Test Vessel")
            .type(VesselType.CONTAINER_SHIP)
            .status(VesselStatus.ACTIVE)
            .build();
    }

    public static CreateVesselRequest createRequest(String imoNumber) {
        return new CreateVesselRequest(imoNumber, "Test Vessel", "CONTAINER_SHIP");
    }
}
```

### Test Configuration

```yaml
# application-test.yml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/smartport_test
    username: test
    password: test
  rabbitmq:
    host: localhost
    port: 5672
  flyway:
    enabled: true
    schemas: vessel

logging:
  level:
    org.springframework.test: DEBUG
    org.testcontainers: INFO
```

### CI Pipeline Integration

```yaml
# .github/workflows/test.yml
name: Test Suite

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    services:
      postgres:
        image: postgres:17-alpine
        env:
          POSTGRES_DB: smartport_test
          POSTGRES_USER: test
          POSTGRES_PASSWORD: test
        ports:
          - 5432:5432
      rabbitmq:
        image: rabbitmq:3.13-management-alpine
        ports:
          - 5672:5672

    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '25'
          distribution: 'temurin'

      - name: Run Unit Tests
        run: ./mvnw test -Punit

      - name: Run Integration Tests
        run: ./mvnw verify -Pintegration

      - name: Generate Test Report
        uses: dorny/test-reporter@v1
        with:
          name: Test Results
          path: '**/target/surefire-reports/*.xml'
          reporter: java-junit
```

### Test Categories and Coverage

| Service | Unit Tests | Integration Tests | Key Scenarios |
|---------|-----------|-------------------|---------------|
| auth-service | JWT generation, password hashing, RBAC | Login/register API, token refresh | Auth flow, role enforcement |
| vessel-service | Vessel validation, position calculations | CRUD API, WebSocket events | Vessel lifecycle, real-time updates |
| berth-service | Utilization calculation, scheduling logic | Reservation API, conflict detection | Berth booking, availability |
| cargo-service | Manifest validation, weight calculations | CRUD API, status transitions | Cargo lifecycle, manifest management |
| compliance-service | Inspection scoring, certificate validation | Inspection API, violation tracking | Compliance workflow |
| notification-service | Template rendering, priority routing | Send API, WebSocket delivery | Notification lifecycle |
| analytics-service | Aggregation logic, report generation | Report API, dashboard data | Analytics calculations |

## Alternatives Considered

### 1. Manual Testing Only
Testing features manually through the UI or API.
- **Pros:** No test code to maintain, intuitive for exploratory testing.
- **Cons:** No regression detection, time-consuming, error-prone, doesn't scale, no evidence of automated verification.
- **Verdict:** Rejected — doesn't meet the evidence-based completion requirement.

### 2. BDD with Cucumber
Behavior-driven development with Gherkin syntax.
- **Pros:** Business-readable tests, shared understanding between developers and stakeholders.
- **Cons:** Slower test execution, verbose syntax, additional learning curve, Cucumber dependency, less precise than JUnit for technical tests.
- **Verdict:** Rejected — adds complexity without proportional benefit for a student project.

### 3. Property-Based Testing (jqwik/QuickTheories)
Generating random inputs to test invariants.
- **Pros:** Finds edge cases that example-based tests miss, high coverage with few test cases.
- **Cons:** Steep learning curve, non-deterministic failures, harder to debug, not suitable for integration tests.
- **Verdict:** Rejected — useful as a complement but not a primary strategy.

### 4.契约测试 (Pact)
Consumer-driven contract testing between services.
- **Pros:** Ensures API compatibility between services, catches contract drift.
- **Cons:** Additional tooling (Pact Broker), complex setup, overkill for a team that controls both consumer and provider.
- **Verdict:** Rejected — useful in large organizations with separate teams; overkill for a student project.

### 5. Full E2E Testing (Cypress/Playwright)
End-to-end testing of the entire stack through the UI.
- **Pros:** Tests the complete user flow, catches UI bugs.
- **Cons:** Slow execution, fragile (breaks on UI changes), difficult to debug, maintenance overhead, doesn't provide design feedback like TDD.
- **Verdict:** Rejected as primary — used sparingly for critical user flows only.

## Rationale

TDD + integration testing is the optimal strategy based on:

1. **Design feedback:** TDD drives better API design — writing tests first forces you to think about the interface before implementation.
2. **Evidence-based completion:** Automated tests provide concrete evidence that features work correctly.
3. **Regression safety:** Tests prevent breaking existing functionality when adding new features.
4. **Speed:** Unit tests execute in milliseconds; integration tests in seconds — fast feedback loop.
5. **Documentation:** Tests serve as executable documentation of expected behavior.
6. **Team coordination:** Tests define the contract between services and between team members.
7. **Spring Boot Test:** Comprehensive testing support with `@SpringBootTest`, `@WebMvcTest`, `@DataJpaTest`, and Testcontainers.
8. **Course alignment:** TDD is a recognized software engineering practice that demonstrates professional development skills.

### TDD Workflow

```
┌─────────────┐
│  1. RED      │  Write a failing test
│  (1 min)     │  that describes desired behavior
└──────┬──────┘
       │
       ▼
┌─────────────┐
│  2. GREEN    │  Write minimum code to pass
│  (5 min)     │  the test
└──────┬──────┘
       │
       ▼
┌─────────────┐
│  3. REFACTOR │  Improve code without
│  (5 min)     │  changing behavior
└──────┬──────┘
       │
       ▼
┌─────────────┐
│  4. COMMIT   │  Git commit with test
│  (1 min)     │  and implementation
└──────┴──────┘
```

## Consequences

### Positive
- **Design feedback** — TDD forces thoughtful API design before implementation.
- **Regression safety** — automated tests catch breaking changes immediately.
- **Evidence-based completion** — test results serve as proof of feature functionality.
- **Code quality** — tested code is more maintainable and reliable.
- **Fast feedback** — unit tests run in <10 seconds, providing instant confirmation.
- **Living documentation** — tests describe expected behavior better than comments.
- **Refactoring confidence** — comprehensive tests enable safe code refactoring.

### Negative
- **Time investment** — TDD adds ~20-30% upfront development time (recouped through fewer bugs).
- **Test maintenance** — tests must be updated when requirements change.
- **Learning curve** — TDD requires discipline and practice to do effectively.
- **False confidence** — tests can pass while the system has integration issues (mitigated by integration tests).
- **Test code complexity** — complex test setups (Testcontainers, mocking) can be hard to understand.
- **Flaky tests** — integration tests with external dependencies can be non-deterministic (mitigated by proper container management).

## Related

- ADR-001: Architecture Style — SBA (each service has its own test suite)
- ADR-003: Backend Framework — Spring Boot 3.x (Spring Boot Test framework)
- ADR-010: Database Migration — Flyway (test schema management)
- ADR-011: Deployment — Docker Compose (Testcontainers uses Docker)
- Spring Boot Testing: https://docs.spring.io/spring-boot/reference/testing.html
- Testcontainers: https://testcontainers.com/
- JUnit 5 User Guide: https://junit.org/junit5/docs/current/user-guide/
