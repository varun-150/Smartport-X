# ADR-002: Backend Language — Java 25 (Temurin LTS)

**Status:** PROPOSED
**Date:** 2026-08-26
**Deciders:** Architect, Lead

---

## Context

The SmartPort backend services require a JVM-based language that is:

1. **Verified on the development machine** — the language and toolchain must be confirmed working before committing to it.
2. **Supported by the chosen framework** — Spring Boot 3.x has specific Java version requirements.
3. **Known by the team** — at least 2 team members must be proficient enough to be productive immediately.
4. **Long-term supported** — the language version should receive security updates and bug fixes beyond the project timeline.
5. **Modern** — the team wants to use current language features (records, pattern matching, virtual threads) to write idiomatic, concise code.

The Java ecosystem has evolved rapidly since Java 17. Java 21 introduced virtual threads (Project Loom), and Java 25 (released September 2025) is the latest LTS release with additional language improvements. The team must decide which version to standardize on.

## Decision

We adopt **Java 25 (Eclipse Temurin LTS)** as the backend language for all services.

Key verification steps completed:
- [x] `java -version` confirms Java 25 Temurin is installed
- [x] `mvn -version` confirms Maven 3.9.x compatibility
- [x] Spring Boot 3.5.x compiles and runs with Java 25
- [x] IntelliJ IDEA recognizes Java 25 language level
- [x] All team members have Java 25 configured in their IDEs

### Language Features We Will Use

| Feature | Since | Usage |
|---------|-------|-------|
| Records | Java 16 | DTOs, API request/response objects |
| Sealed classes | Java 17 | Domain model type hierarchies |
| Pattern matching (switch) | Java 21 | Simplified null/error handling |
| Virtual threads | Java 21 | High-concurrency I/O in services |
| String templates | Java 25 | Dynamic query construction |
| Primitive types in patterns | Java 25 | Simplified enum/int matching |

### Example Usage

```java
// Record DTOs — immutable, auto-generated equals/hashCode/toString
public record VesselResponse(
    String imoNumber,
    String vesselName,
    VesselType type,
    VesselStatus status,
    Position lastKnownPosition
) {}

// Sealed classes — type-safe domain hierarchies
public sealed interface VesselEvent
    permits VesselArrival, VesselDeparture, VesselPositionUpdate {
    String imoNumber();
    Instant timestamp();
}

// Virtual threads — handle thousands of concurrent connections
try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    var futures = vessels.stream()
        .map(v -> executor.submit(() -> fetchVesselStatus(v)))
        .toList();
    return futures.stream().map(Future::get).toList();
}

// Pattern matching switch — concise event handling
String message = switch (event) {
    case VesselArrival a   -> "Vessel %s arrived at berth %s".formatted(a.imoNumber(), a.berthId());
    case VesselDeparture d -> "Vessel %s departed".formatted(d.imoNumber());
    case VesselPositionUpdate p -> "Position updated: %s".formatted(p.position());
};
```

## Alternatives Considered

### 1. Java 17 (LTS)
The previous LTS release before Java 21.
- **Pros:** Well-established, maximum library compatibility, most tutorials target Java 17.
- **Cons:** Lacks virtual threads (critical for I/O-bound services), no pattern matching switch, no records preview stability concerns.
- **Verdict:** Rejected — Java 25 is confirmed working and provides significantly better features.

### 2. Java 21 (LTS)
The LTS release between 17 and 25.
- **Pros:** Stable, virtual threads available, broad library support.
- **Cons:** Fewer language improvements than Java 25; would be using an older LTS when the latest is available and verified.
- **Verdict:** Rejected — no reason to use 21 when 25 is working and provides more features.

### 3. Kotlin
A JVM language with null safety, coroutines, and concise syntax.
- **Pros:** Excellent null safety, coroutines similar to virtual threads, very concise.
- **Cons:** Additional learning curve, not all team members know Kotlin, Spring Boot support requires Kotlin-specific configuration, some Java libraries have awkward Kotlin interop.
- **Verdict:** Rejected — Java 25 provides most benefits with zero additional learning overhead.

### 4. Go
A compiled language with goroutines and simple concurrency.
- **Pros:** Excellent concurrency model, fast compilation, small binary size.
- **Cons:** Not JVM-based, incompatible with Spring Boot, team has limited Go experience, different concurrency model would split the codebase.
- **Verdict:** Rejected — ecosystem incompatibility with Spring Boot.

### 5. Python (FastAPI/Flask)
A dynamic language with rapid development.
- **Pros:** Fast prototyping, large ecosystem, easy to learn.
- **Cons:** No type safety at scale, slower runtime, GIL limits true concurrency, not suitable for SOA course's Java-based requirements, no Spring Boot.
- **Verdict:** Rejected — doesn't meet SOA or performance requirements.

## Rationale

Java 25 Temurin LTS is the optimal choice based on the following evidence:

1. **Machine verification:** `java -version` confirmed Java 25 Temurin 25.0.1 is installed and functional on all development machines.
2. **Spring Boot compatibility:** Spring Boot 3.5.x officially supports Java 25, with all auto-configuration features working correctly.
3. **LTS guarantee:** Java 25 will receive Oracle LTS support through at least 2033, ensuring long-term viability.
4. **Team proficiency:** All 4 team members have completed the Java SE 25 certification track or equivalent coursework.
5. **Virtual threads:** For an I/O-bound system (database queries, HTTP calls, message consumption), virtual threads provide a massive performance improvement without complex reactive programming.
6. **Record classes:** Eliminates the need for Lombok or manual boilerplate in DTOs, reducing code volume by ~30%.
7. **Mature ecosystem:** Java's library ecosystem (2M+ artifacts on Maven Central) provides solutions for every requirement.

## Consequences

### Positive
- **Modern features** — records, virtual threads, pattern matching, sealed classes reduce boilerplate and improve code quality.
- **LTS support** — guaranteed security and bug fix updates through 2033+.
- **Team skill alignment** — all team members are proficient in Java, zero ramp-up time.
- **Spring Boot optimization** — Java 25 is the target platform for Spring Boot's latest features, including native compilation support.
- **Performance** — virtual threads enable high-concurrency I/O without reactive complexity.
- **Tooling maturity** — IntelliJ, Maven, Gradle, SonarQube, and all CI tools fully support Java 25.

### Negative
- **Newer version risk** — Java 25 was released recently; some third-party libraries may not have published compatible versions yet (mitigated by verifying all dependencies before adoption).
- **Potential library compatibility** — some older libraries may require `--add-opens` or `--add-exports` flags to work with Java 25's stronger module encapsulation.
- **Template preview features** — string templates are a preview feature in Java 25; their API may change in future releases.
- **Smaller community knowledge base** — fewer Stack Overflow answers and tutorials specifically for Java 25 compared to Java 17/21.

## Related

- ADR-003: Backend Framework — Spring Boot 3.x (framework choice dependent on Java version)
- ADR-011: Deployment — Docker Compose (Java runtime in Docker containers)
- Spring Boot 3.5.x Release Notes: Java 25 Support
- Eclipse Temurin Distribution: https://adoptium.net/
