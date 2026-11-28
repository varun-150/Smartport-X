# SmartPortX — Security Architecture

> SOA Container Port Logistics Platform
> Version: 1.0.0
> Last Updated: 2026-08-26

---

## Table of Contents

1. [Overview](#1-overview)
2. [Authentication Architecture](#2-authentication-architecture)
3. [Authorization Architecture (RBAC)](#3-authorization-architecture-rbac)
4. [Data Protection](#4-data-protection)
5. [API Security](#5-api-security)
6. [Input Validation](#6-input-validation)
7. [Audit Logging](#7-audit-logging)
8. [Service-to-Service Security](#8-service-to-service-security)
9. [Infrastructure Security](#9-infrastructure-security)
10. [Security Headers](#10-security-headers)
11. [Security Testing Strategy](#11-security-testing-strategy)
12. [Incident Response](#12-incident-response)
13. [Compliance Considerations](#13-compliance-considerations)

---

## 1. Overview

SmartPortX handles sensitive logistics data including customs declarations, container seal numbers, cargo manifests, and operational credentials. The security architecture follows a defense-in-depth model with multiple overlapping layers.

```
+-------------------------------------------------------------------------+
|                         SECURITY LAYERS                                 |
+-------------------------------------------------------------------------+
|                                                                         |
|  Layer 1: Network Security                                              |
|  +-------------------------------------------------------------------+  |
|  | TLS 1.3  |  Docker Network Isolation  |  Firewall Rules           |  |
|  +-------------------------------------------------------------------+  |
|                                                                         |
|  Layer 2: Gateway Security                                              |
|  +-------------------------------------------------------------------+  |
|  | JWT Validation  |  Rate Limiting  |  CORS  |  Security Headers    |  |
|  +-------------------------------------------------------------------+  |
|                                                                         |
|  Layer 3: Application Security                                          |
|  +-------------------------------------------------------------------+  |
|  | RBAC  |  Input Validation  |  Audit Logging  |  Encryption       |  |
|  +-------------------------------------------------------------------+  |
|                                                                         |
|  Layer 4: Data Security                                                 |
|  +-------------------------------------------------------------------+  |
|  | Encryption at Rest  |  Field-level Encryption  |  Data Masking    |  |
|  +-------------------------------------------------------------------+  |
|                                                                         |
|  Layer 5: Infrastructure Security                                       |
|  +-------------------------------------------------------------------+  |
|  | Secrets Management  |  Vulnerability Scanning  |  Least Privilege  |  |
|  +-------------------------------------------------------------------+  |
+-------------------------------------------------------------------------+
```

**Security Principles:**

- **Least Privilege**: Every service, user, and component has only the minimum access required
- **Defense in Depth**: Multiple security layers so no single failure is catastrophic
- **Zero Trust**: Never trust network location; always verify identity and authorization
- **Secure by Default**: All configurations default to the most restrictive setting
- **Separation of Duties**: No single user or service can perform all critical operations alone

---

## 2. Authentication Architecture

### 2.1 JWT-Based Authentication

SmartPortX uses JSON Web Tokens (JWT) with RSA-256 asymmetric signing for all authentication.

**Signing Algorithm:**

- Algorithm: RS256 (RSA Signature with SHA-256)
- Key size: 2048-bit RSA
- Key rotation: Every 90 days (overlap period: 7 days)
- Public key distributed to all services for verification
- Private key held exclusively by Identity Service

### 2.2 Token Structure

```
+--------------------+--------------------+--------------------+
|       Header       |      Payload       |     Signature      |
|   (RS256, JWT)     |  (claims, expiry)  |  (RSA-256 sign)    |
+--------------------+--------------------+--------------------+
```

**Header:**

```json
{
  "alg": "RS256",
  "typ": "JWT",
  "kid": "smartportx-key-2026-08"
}
```

**Payload (Access Token):**

```json
{
  "sub": "usr-0042",
  "role": "TERMINAL_OP",
  "permissions": ["yard:read", "yard:write", "gate:read", "gate:write", "container:read"],
  "iat": 1724678400,
  "exp": 1724679300,
  "iss": "smartportx-identity",
  "aud": "smartportx-gateway",
  "jti": "tok-a1b2c3d4-e5f6-7890-abcd-ef1234567890"
}
```

**Payload (Refresh Token):**

```json
{
  "sub": "usr-0042",
  "type": "refresh",
  "iat": 1724678400,
  "exp": 1725283200,
  "iss": "smartportx-identity",
  "jti": "ref-b2c3d4e5-f6a7-8901-bcde-f12345678901",
  "family": "token-family-xyz"
}
```

### 2.3 Token Expiry

| Token Type      | Expiry    | Storage                | Rotation          |
|-----------------|-----------|------------------------|-------------------|
| Access Token    | 15 minutes| Memory (frontend app)  | Silent via refresh|
| Refresh Token   | 7 days    | HttpOnly Secure Cookie | Rotated on use    |
| ID Token        | 1 hour    | Memory (frontend app)  | Via refresh       |

### 2.4 Refresh Token Rotation

On every use of a refresh token:

1. Identity Service validates the refresh token (signature, expiry, not revoked)
2. Identity Service checks the token family (prevents reuse after rotation)
3. Identity Service issues new access token + new refresh token
4. Old refresh token is immediately revoked and added to the blocklist
5. If a revoked refresh token is presented, ALL tokens in that family are revoked (compromise detection)

```
Initial:  Refresh Token RT-1 (family: FAM-A)
          |
          v  (used to get new access token)
Rotation: RT-1 revoked, RT-2 issued (family: FAM-A)
          |
          v  (used again)
Rotation: RT-2 revoked, RT-3 issued (family: FAM-A)

If RT-1 is used again after rotation -> ALARM: Token reuse detected
                                         -> Revoke entire family FAM-A
                                         -> Force user re-authentication
```

### 2.5 Gateway JWT Validation

The Gateway validates every incoming request:

```
Client Request
    |
    v
[Extract Bearer token from Authorization header]
    |
    v
[Check token not in revocation blocklist]
    |
    v
[Verify RS256 signature using Identity Service public key]
    |
    v
[Check expiration (exp claim)]
    |
    v
[Check issuer (iss claim = smartportx-identity)]
    |
    v
[Check audience (aud claim = smartportx-gateway)]
    |
    v
[Extract user claims: sub, role, permissions]
    |
    v
[Forward request with enriched headers]
    +---> X-User-Id: {sub}
    +---> X-User-Role: {role}
    +---> X-Correlation-Id: {generated or forwarded}
```

### 2.6 Gateway-to-Service Header Forwarding

Services receive validated identity claims via Gateway-injected headers. Services MUST NOT accept authentication headers directly from the client — only from the Gateway (via Docker internal network).

| Header          | Source         | Example Value                  |
|-----------------|----------------|--------------------------------|
| `X-User-Id`     | JWT `sub`      | `usr-0042`                     |
| `X-User-Role`   | JWT `role`     | `TERMINAL_OP`                  |
| `X-User-Perms`  | JWT `permissions` | `yard:read,yard:write,gate:read` |
| `X-Correlation-Id` | Gateway     | `req-abc123-def456`            |
| `X-Forwarded-For`  | Client      | `192.168.1.100`                |

### 2.7 Authentication Flow Diagram

```
+--------+     +---------+     +----------+     +---------+
| Client |     | Gateway |     | Identity |     | Service |
+---+----+     +----+----+     +----+-----+     +----+----+
    |               |               |               |
    |-- POST /auth/login --------->|               |
    |   (username, password)       |               |
    |               |          [Validate creds]     |
    |               |          [Generate JWT pair]  |
    |<-- 200 {access, refresh} --|               |
    |               |               |               |
    |-- GET /api/v1/yard/zones -->|               |
    |   Authorization: Bearer ___  |               |
    |               |               |               |
    |          [Validate JWT]       |               |
    |          [Check rate limit]   |               |
    |               |               |               |
    |               |-- GET /api/v1/yard/zones --->|
    |               |   X-User-Id: usr-0042        |
    |               |   X-User-Role: TERMINAL_OP   |
    |               |   X-Correlation-Id: req-...  |
    |               |               |          [Process]
    |               |<-- 200 {zones data} ---------|
    |<-- 200 {zones data} ---------|               |
    |               |               |               |
    |   (After 15 min, access token expires)       |
    |               |               |               |
    |-- POST /auth/refresh ------>|               |
    |   (refresh token in cookie)  |               |
    |               |          [Rotate refresh]     |
    |<-- 200 {new access, refresh} |               |
```

---

## 3. Authorization Architecture (RBAC)

### 3.1 Role Definitions

| Role             | Description                                          |
|------------------|------------------------------------------------------|
| `ADMIN`          | Full system access. Manages users, config, all ops.  |
| `TERMINAL_OP`    | Terminal operator. Views all, modifies yard and gate. |
| `YARD_MANAGER`   | Yard operations manager. Manages yard, runs optimiz. |
| `GATE_OPERATOR`  | Gate processor. Processes entries/exits, appointments.|
| `READONLY`       | Read-only access. Views data, no modifications.      |

### 3.2 Permissions Matrix

| Permission                  | ADMIN | TERMINAL_OP | YARD_MANAGER | GATE_OPERATOR | READONLY |
|-----------------------------|:-----:|:-----------:|:------------:|:-------------:|:--------:|
| **Identity**                |       |             |              |               |          |
| users:list                  |  yes  |     no      |     no       |      no       |    no    |
| users:create                |  yes  |     no      |     no       |      no       |    no    |
| users:update                |  yes  |     no      |     no       |      no       |    no    |
| users:delete                |  yes  |     no      |     no       |      no       |    no    |
| roles:assign                |  yes  |     no      |     no       |      no       |    no    |
| **Yard**                    |       |             |              |               |          |
| yard:read                   |  yes  |     yes     |     yes      |      yes      |   yes    |
| yard:modify                 |  yes  |     yes     |     yes      |      no       |    no    |
| yard:optimize               |  yes  |     no      |     yes      |      no       |    no    |
| yard:delete-position        |  yes  |     no      |     yes      |      no       |    no    |
| **Gate**                    |       |             |              |               |          |
| gate:read                   |  yes  |     yes     |     yes      |      yes      |   yes    |
| gate:process-entry          |  yes  |     yes     |     no       |      yes      |    no    |
| gate:process-exit           |  yes  |     yes     |     no       |      yes      |    no    |
| gate:appointments-manage    |  yes  |     no      |     no       |      yes      |    no    |
| **Container**               |       |             |              |               |          |
| container:read              |  yes  |     yes     |     yes      |      yes      |   yes    |
| container:move              |  yes  |     yes     |     yes      |      no       |    no    |
| container:update-status     |  yes  |     yes     |     no       |      yes      |    no    |
| container:delete            |  yes  |     no      |     no       |      no       |    no    |
| **Vessel**                  |       |             |              |               |          |
| vessel:read                 |  yes  |     yes     |     yes      |      yes      |   yes    |
| vessel:manage-schedule      |  yes  |     yes     |     no       |      no       |    no    |
| vessel:manage-stowage       |  yes  |     yes     |     no       |      no       |    no    |
| **Customs**                 |       |             |              |               |          |
| customs:read                |  yes  |     yes     |     no       |      no       |   yes    |
| customs:submit-declaration  |  yes  |     yes     |     no       |      no       |    no    |
| customs:view-sensitive      |  yes  |     yes     |     no       |      no       |    no    |
| **Integration**            |       |             |              |               |          |
| integration:configure      |  yes  |     no      |     no       |      no       |    no    |
| integration:trigger-sync   |  yes  |     no      |     no       |      no       |    no    |
| integration:view-logs      |  yes  |     no      |     no       |      no       |    no    |
| **Notifications**          |       |             |              |               |          |
| notifications:read         |  yes  |     yes     |     yes      |      yes      |   yes    |
| notifications:configure    |  yes  |     no      |     no       |      no       |    no    |
| **System**                 |       |             |              |               |          |
| system:audit-logs          |  yes  |     no      |     no       |      no       |    no    |
| system:health              |  yes  |     yes     |     yes      |      yes      |   yes    |
| system:config              |  yes  |     no      |     no       |      no       |    no    |

### 3.3 Dual-Level Enforcement

Authorization is enforced at two levels:

**Level 1: Gateway (Route-Level)**

Gateway checks if the user's role is permitted to access the route at all. This is a coarse-grained filter.

```yaml
smartportx:
  gateway:
    authorization:
      role-routes:
        - path: /api/v1/yard/**
          roles: [ADMIN, TERMINAL_OP, YARD_MANAGER, READONLY]
        - path: /api/v1/gate/**
          roles: [ADMIN, TERMINAL_OP, GATE_OPERATOR, READONLY]
        - path: /api/v1/integration/**
          roles: [ADMIN]
```

**Level 2: Service (Method-Level)**

Each service uses Spring Security `@PreAuthorize` annotations for fine-grained permission checks.

```java
@RestController
@RequestMapping("/api/v1/yard/zones")
public class YardZoneController {

    @GetMapping
    @PreAuthorize("hasAuthority('yard:read')")
    public List<YardZoneDto> getAllZones() {
        return yardZoneService.getAllZones();
    }

    @PutMapping("/{zoneId}/positions")
    @PreAuthorize("hasAuthority('yard:modify')")
    public YardZoneDto updatePosition(
            @PathVariable String zoneId,
            @RequestBody UpdatePositionRequest request) {
        return yardZoneService.updatePosition(zoneId, request);
    }

    @PostMapping("/optimize")
    @PreAuthorize("hasAuthority('yard:optimize')")
    public OptimizationResult runOptimization() {
        return yardOptimizationService.runOptimization();
    }
}
```

### 3.4 Role-Based Response Shaping

API responses are filtered based on the requesting user's role. Sensitive data is excluded for roles that should not see it.

```java
@Service
public class ContainerResponseShaper {

    public ContainerDto shape(Container container, String userRole) {
        ContainerDto dto = mapToDto(container);

        // Only TERMINAL_OP and ADMIN can see customs data
        if (!List.of("ADMIN", "TERMINAL_OP").contains(userRole)) {
            dto.setCustomsDeclaration(null);
            dto.setSealNumber(null);
            dto.setCargoManifest(null);
        }

        // READONLY cannot see operational details
        if ("READONLY".equals(userRole)) {
            dto.setInternalNotes(null);
            dto.setOperatorId(null);
        }

        return dto;
    }
}
```

---

## 4. Data Protection

### 4.1 TLS 1.3

All network communications use TLS 1.3.

| Connection                   | TLS Termination     | Certificate Source        |
|------------------------------|---------------------|---------------------------|
| Client -> Gateway            | Gateway terminates  | Let's Encrypt (prod)      |
| Gateway -> Services          | Not encrypted (internal Docker network) | — |
| Services -> RabbitMQ         | TLS mutual auth     | Self-signed CA            |
| Services -> Database         | TLS required        | Self-signed CA            |
| WebSocket (WSS)              | Gateway terminates  | Same as HTTPS             |

**TLS Configuration:**

```yaml
server:
  ssl:
    enabled: true
    enabled-protocols: TLSv1.3
    protocol: TLS
    ciphers: TLS_AES_256_GCM_SHA384,TLS_CHACHA20_POLY1305_SHA256,TLS_AES_128_GCM_SHA256
    key-store: classpath:keystore.p12
    key-store-password: ${SSL_KEYSTORE_PASSWORD}
    key-store-type: PKCS12
```

### 4.2 Encryption at Rest

Sensitive data fields are encrypted at the application level before storage using AES-256-GCM.

**Fields requiring encryption:**

| Entity            | Field              | Reason                          |
|-------------------|--------------------|---------------------------------|
| Container         | sealNumber         | Security-sensitive customs data |
| Container         | customsDeclaration | Regulatory data                 |
| Container         | cargoManifest      | Commercial sensitivity          |
| GateEntry         | driverLicenseNo    | Personal data (PII)             |
| GateEntry         | driverName         | Personal data (PII)             |
| CustomsDeclaration| declarationContent | Regulatory data                 |
| User              | passwordHash       | Credential protection           |

**Encryption Implementation:**

```java
@Converter
@jakarta.persistence.AttributeConverter
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private final EncryptionService encryptionService;

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null) return null;
        return encryptionService.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) return null;
        return encryptionService.decrypt(dbData);
    }
}

@Service
public class EncryptionService {

    private final SecretKey secretKey; // AES-256 key from environment variable

    public String encrypt(String plaintext) {
        byte[] iv = new byte[12]; // GCM recommended IV size
        secureRandom.nextBytes(iv);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(128, iv));
        byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

        // Prepend IV to ciphertext for storage
        byte[] combined = new byte[iv.length + ciphertext.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);

        return Base64.getEncoder().encodeToString(combined);
    }

    public String decrypt(String encrypted) {
        byte[] combined = Base64.getDecoder().decode(encrypted);
        byte[] iv = Arrays.copyOfRange(combined, 0, 12);
        byte[] ciphertext = Arrays.copyOfRange(combined, 12, combined.length);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(128, iv));
        byte[] plaintext = cipher.doFinal(ciphertext);

        return new String(plaintext, StandardCharsets.UTF_8);
    }
}
```

### 4.3 Data Protection Rules

| Rule                                         | Enforcement                            |
|----------------------------------------------|----------------------------------------|
| No sensitive data in URL parameters          | Gateway rejects requests with sensitive query params |
| No sensitive data in log output              | Custom log filter masks PII and secrets|
| Passwords never stored in plain text         | BCrypt hashing (cost factor 12)        |
| API keys not committed to source control     | Git hooks + pre-commit scanning        |
| No PII in error messages returned to client  | Generic error messages only            |
| Sensitive data masked in audit logs          | Last 4 chars only for seal numbers, etc|

**Log Masking Configuration:**

```yaml
logging:
  mask:
    patterns:
      - name: SEAL_NUMBER
        regex: "[A-Z0-9]{4}\\d{7}"
        replacement: "****\\d{4}"
      - name: DRIVER_LICENSE
        regex: "\\d{8,12}"
        replacement: "****"
      - name: PASSWORD
        regex: "(?i)(password|passwd|pwd)\\s*[:=]\\s*\\S+"
        replacement: "$1=****"
      - name: API_KEY
        regex: "(?i)(api[_-]?key)\\s*[:=]\\s*\\S+"
        replacement: "$1=****"
      - name: JWT_TOKEN
        regex: "Bearer\\s+[A-Za-z0-9-_=]+\\.[A-Za-z0-9-_=]+\\.?[A-Za-z0-9-_.+/=]*"
        replacement: "Bearer ****"
```

---

## 5. API Security

### 5.1 Rate Limiting

Rate limiting is enforced at the Gateway level using a token bucket algorithm.

```yaml
smartportx:
  gateway:
    rate-limit:
      enabled: true
      default-limit: 100        # requests per minute
      default-window: 60        # seconds
      burst-limit: 200          # max burst
      key-resolver: "#{request.headers['X-API-Key'] ?: request.remoteAddress}"
      routes:
        - path: /api/v1/identity/auth/login
          limit: 10             # Strict limit on login attempts
          window: 60
        - path: /api/v1/identity/auth/refresh
          limit: 20
          window: 60
        - path: /api/v1/integration/**
          limit: 50             # Lower limit for integration
          window: 60
        - path: /api/v1/yard/**
          limit: 150            # Higher limit for operational APIs
          window: 60
```

**Rate limit response:**

```http
HTTP/1.1 429 Too Many Requests
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 0
X-RateLimit-Reset: 1724678460
Retry-After: 30
Content-Type: application/json

{
  "error": "RATE_LIMIT_EXCEEDED",
  "message": "Rate limit exceeded. Maximum 100 requests per minute.",
  "retryAfter": 30
}
```

### 5.2 CORS Configuration

CORS is configured to restrict origins to known frontends:

```yaml
spring:
  cloud:
    gateway:
      globalcors:
        cors-configurations:
          '[/**]':
            allowedOrigins:
              - "https://smartportx.local"
              - "https://admin.smartportx.local"
              - "https://terminal.smartportx.local"
            allowedMethods:
              - GET
              - POST
              - PUT
              - DELETE
              - PATCH
              - OPTIONS
            allowedHeaders:
              - Authorization
              - Content-Type
              - X-Correlation-Id
              - X-API-Key
            exposedHeaders:
              - X-Correlation-Id
              - X-RateLimit-Remaining
            allowCredentials: true
            maxAge: 3600
```

### 5.3 Request Size Limits

| Request Type              | Size Limit | Enforcement        |
|---------------------------|------------|---------------------|
| JSON API requests         | 1 MB       | Gateway filter      |
| EDIFACT file uploads      | 10 MB      | Integration Service |
| Image uploads (avatars)   | 5 MB       | Identity Service    |
| Bulk container operations | 500 KB     | Container Service   |

### 5.4 Content Type Validation

Gateway rejects requests without proper Content-Type:

- `application/json` for JSON request bodies
- `multipart/form-data` for file uploads
- `text/plain` for EDIFACT submissions

Requests with missing or mismatched Content-Type receive `415 Unsupported Media Type`.

---

## 6. Input Validation

### 6.1 Server-Side Validation

All user inputs are validated server-side using Jakarta Bean Validation (Hibernate Validator). Client-side validation is never trusted.

```java
public class CreateContainerRequest {

    @NotBlank(message = "Container number is required")
    @Pattern(
        regexp = "[A-Z]{4}\\d{7}",
        message = "Container number must follow ISO 6346 format: 4 letters + 7 digits"
    )
    private String containerNumber;

    @NotBlank(message = "ISO code is required")
    @Pattern(
        regexp = "^[0-9]{2}[A-Z]{1}[1-9]$",
        message = "ISO code must be in XXXX format (e.g., 42G1)"
    )
    private String isoCode;

    @NotNull(message = "Weight is required")
    @DecimalMin(value = "2500", message = "Minimum weight is 2500 kg (tare)")
    @DecimalMax(value = "80000", message = "Maximum weight is 80000 kg")
    private BigDecimal weightKg;

    @Size(max = 11, message = "Seal number maximum 11 characters")
    private String sealNumber;

    @Valid
    private PositionRequest assignedPosition;
}
```

### 6.2 ISO 6346 Container Number Validation

Container numbers follow the international standard ISO 6346:

```
Format:  XXXX0000000
         |||| |||||||
         |||| +-- 7 digits (serial number + check digit)
         ||||
         +---- 4 letters: 3 owner code + 1 category (U = freight container)
```

**Regex validation:** `[A-Z]{4}\d{7}`

**Check digit validation:**

```java
public class ContainerNumberValidator {

    private static final Pattern FORMAT_PATTERN = Pattern.compile("[A-Z]{4}\\d{7}");

    public boolean isValid(String containerNumber) {
        if (containerNumber == null || !FORMAT_PATTERN.matcher(containerNumber).matches()) {
            return false;
        }
        // Validate check digit (ISO 6346 algorithm)
        String letters = containerNumber.substring(0, 4);
        String digits = containerNumber.substring(4);

        int sum = 0;
        for (int i = 0; i < 4; i++) {
            sum += letterValue(letters.charAt(i)) * (int) Math.pow(2, i);
        }
        for (int i = 0; i < 6; i++) {
            sum += Character.getNumericValue(digits.charAt(i)) * (int) Math.pow(2, i + 4);
        }

        int checkDigit = Character.getNumericValue(digits.charAt(6));
        int expectedCheck = sum % 11 % 10;

        return checkDigit == expectedCheck;
    }

    private int letterValue(char c) {
        int value = c - 'A' + 10;
        if (value > 30) value -= 3; // Skip letters I, O (ambiguous with 1, 0)
        return value;
    }
}
```

### 6.3 SQL Injection Prevention

SmartPortX uses Spring Data JPA with parameterized queries exclusively. SQL injection is prevented by:

- **JPA/Hibernate ORM**: All queries use parameterized binding
- **JPQL named parameters**: `@Query("SELECT c FROM Container c WHERE c.number = :number")`
- **No string concatenation** in SQL queries
- **Stored procedures** (if used) called via `@NamedStoredProcedureQuery` only
- **Database permissions**: Application accounts have minimum required privileges (no DDL)

```java
// CORRECT - parameterized
@Query("SELECT c FROM Container c WHERE c.containerNumber = :number AND c.status = :status")
Optional<Container> findByNumberAndStatus(@Param("number") String number, @Param("status") ContainerStatus status);

// FORBIDDEN - string concatenation (will fail code review / static analysis)
// @Query("SELECT c FROM Container c WHERE c.containerNumber = '" + number + "'")
```

### 6.4 JSON Schema Validation

Request bodies are validated against JSON schemas for complex payloads:

```yaml
# schema: /schemas/container-create.json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "required": ["containerNumber", "isoCode", "weightKg"],
  "properties": {
    "containerNumber": {
      "type": "string",
      "pattern": "^[A-Z]{4}\\d{7}$",
      "description": "ISO 6346 container number"
    },
    "isoCode": {
      "type": "string",
      "pattern": "^[0-9]{2}[A-Z]{1}[1-9]$"
    },
    "weightKg": {
      "type": "number",
      "minimum": 2500,
      "maximum": 80000
    }
  },
  "additionalProperties": false
}
```

### 6.5 Validation Error Response

All validation errors return a consistent format:

```json
{
  "error": "VALIDATION_FAILED",
  "message": "Request validation failed",
  "timestamp": "2026-08-26T14:30:00.000Z",
  "correlationId": "req-abc123-def456",
  "fieldErrors": [
    {
      "field": "containerNumber",
      "message": "Container number must follow ISO 6346 format: 4 letters + 7 digits",
      "rejectedValue": "CMAU123456"
    },
    {
      "field": "weightKg",
      "message": "Maximum weight is 80000 kg",
      "rejectedValue": 95000
    }
  ]
}
```

---

## 7. Audit Logging

### 7.1 Audit Event Structure

Every state-changing operation produces an audit log entry:

```json
{
  "auditId": "aud-8a3f2b1c-9d4e-4f5a-b6c7",
  "timestamp": "2026-08-26T14:30:00.000Z",
  "correlationId": "req-abc123-def456",
  "userId": "usr-0042",
  "userRole": "TERMINAL_OP",
  "action": "CONTAINER_MOVED",
  "entityType": "Container",
  "entityId": "CMAU1234567",
  "service": "smartportx-yard",
  "before": {
    "position": "A-03-02",
    "status": "YARDED"
  },
  "after": {
    "position": "B-07-01",
    "status": "YARDED"
  },
  "metadata": {
    "equipmentId": "RTG-003",
    "durationSeconds": 145,
    "clientIp": "192.168.1.100"
  }
}
```

### 7.2 Audit Log Properties

| Property        | Type     | Required | Description                        |
|-----------------|----------|----------|------------------------------------|
| `auditId`       | UUID     | Yes      | Unique audit entry identifier      |
| `timestamp`     | ISO 8601 | Yes      | When the action occurred           |
| `correlationId` | String   | Yes      | Request trace ID                   |
| `userId`        | String   | Yes      | Authenticated user performing action|
| `userRole`      | String   | Yes      | Role at time of action             |
| `action`        | Enum     | Yes      | Action type (see action catalog)   |
| `entityType`    | String   | Yes      | Entity type affected               |
| `entityId`      | String   | Yes      | Entity identifier                  |
| `service`       | String   | Yes      | Service that processed the action  |
| `before`        | JSON     | No       | State before the change            |
| `after`         | JSON     | No       | State after the change             |
| `metadata`      | JSON     | No       | Additional context                 |
| `clientIp`      | String   | Yes      | Client IP address                  |

### 7.3 Action Catalog

| Action Category    | Actions                                              |
|--------------------|------------------------------------------------------|
| Authentication     | LOGIN_SUCCESS, LOGIN_FAILED, LOGOUT, TOKEN_REFRESH, PASSWORD_CHANGE |
| User Management    | USER_CREATED, USER_UPDATED, USER_DELETED, ROLE_CHANGED |
| Container          | CONTAINER_CREATED, CONTAINER_MOVED, CONTAINER_STATUS_CHANGED, CONTAINER_DELETED |
| Yard               | ZONE_UPDATED, POSITION_ASSIGNED, POSITION_CLEARED, OPTIMIZATION_RUN |
| Gate               | GATE_ENTRY_PROCESSED, GATE_EXIT_PROCESSED, APPOINTMENT_CREATED, APPOINTMENT_CANCELLED |
| Vessel             | VESSEL_ARRIVAL_REGISTERED, VESSEL_DEPARTURE_REGISTERED, STOWAGE_PLAN_UPDATED |
| Customs            | DECLARATION_SUBMITTED, CLEARANCE_RECEIVED, HOLD_PLACED, HOLD_LIFTED |
| Integration        | EDIFACT_RECEIVED, EDIFACT_SENT, SYNC_TRIGGERED, EXTERNAL_API_CALL |
| System             | CONFIG_CHANGED, CACHE_CLEARED, SERVICE_RESTARTED |

### 7.4 Storage Requirements

```sql
CREATE TABLE audit_log (
    audit_id        UUID PRIMARY KEY,
    timestamp       TIMESTAMP NOT NULL,
    correlation_id  VARCHAR(64) NOT NULL,
    user_id         VARCHAR(64) NOT NULL,
    user_role       VARCHAR(32) NOT NULL,
    action          VARCHAR(64) NOT NULL,
    entity_type     VARCHAR(64) NOT NULL,
    entity_id       VARCHAR(128) NOT NULL,
    service_name    VARCHAR(64) NOT NULL,
    before_state    JSONB,
    after_state     JSONB,
    metadata        JSONB,
    client_ip       VARCHAR(45),

    -- Append-only enforcement
    -- No UPDATE or DELETE permissions granted to application role
    -- Application role only has INSERT and SELECT
);

-- Indexes for common queries
CREATE INDEX idx_audit_timestamp ON audit_log (timestamp);
CREATE INDEX idx_audit_user ON audit_log (user_id);
CREATE INDEX idx_audit_entity ON audit_log (entity_type, entity_id);
CREATE INDEX idx_audit_correlation ON audit_log (correlation_id);
CREATE INDEX idx_audit_action ON audit_log (action);
```

**Append-only enforcement:**

```sql
-- Database role permissions
CREATE ROLE audit_writer;
GRANT INSERT, SELECT ON audit_log TO audit_writer;

-- No UPDATE or DELETE granted
-- Application connects with audit_writer role for audit operations
-- Only DBA can modify audit schema (for migrations)
```

### 7.5 Correlation ID Tracing

The correlation ID flows through every component:

```
Client generates or receives correlation ID
    |
    +---> Gateway (generates if absent)
    |       |
    |       +---> Service A (logs with correlation ID)
    |       |       |
    |       |       +---> Service B (REST call, correlation ID forwarded)
    |       |       |       |
    |       |       |       +---> Audit Log (correlation ID stored)
    |       |       |
    |       |       +---> RabbitMQ (correlation ID in CloudEvent)
    |       |               |
    |       |               +---> Service C (processes event)
    |       |                       |
    |       |                       +---> Audit Log (correlation ID stored)
    |       |
    |       +---> Notification Service
    |               |
    |               +---> WebSocket broadcast (correlation ID in metadata)
    |
    +---> Client receives correlation ID in response header
```

### 7.6 Retention Policy

| Retention Period | Action                                    |
|------------------|-------------------------------------------|
| 0-24 months      | Full audit log retained (hot storage)     |
| 24-60 months     | Archived to cold storage (S3/archive)     |
| 60+ months       | Deleted (unless legal hold applies)       |

**Partitioning:**

Audit logs are partitioned by month for efficient querying and archival:

```sql
CREATE TABLE audit_log (
    ...
) PARTITION BY RANGE (timestamp);

CREATE TABLE audit_log_2026_08 PARTITION OF audit_log
    FOR VALUES FROM ('2026-08-01') TO ('2026-09-01');

CREATE TABLE audit_log_2026_09 PARTITION OF audit_log
    FOR VALUES FROM ('2026-09-01') TO ('2026-10-01');
```

---

## 8. Service-to-Service Security

### 8.1 Network Isolation

All internal services communicate over a private Docker bridge network. Only the Gateway is exposed to external traffic.

```
+-------------------------------------------------------------------+
| Docker: smartportx-external                                        |
|                                                                     |
|   +-----------------------------------------------------------+    |
|   | Docker: smartportx-internal                                |    |
|   |                                                             |    |
|   |   +----------+  +----------+  +----------+  +----------+  |    |
|   |   | Identity |  |   Yard   |  |   Gate   |  |Container |  |    |
|   |   +----------+  +----------+  +----------+  +----------+  |    |
|   |                                                             |    |
|   |   +----------+  +----------+  +------------+  +----------+|    |
|   |   |  Vessel  |  |Integration|  |Notification|  | RabbitMQ ||    |
|   |   +----------+  +----------+  +------------+  +----------+|    |
|   |                                                             |    |
|   |   +------------------+  +------------------+               |    |
|   |   | PostgreSQL Yard  |  | PostgreSQL Gate  |               |    |
|   |   +------------------+  +------------------+               |    |
|   +-----------------------------------------------------------+    |
|                                                                     |
|   +-----------------------------------------------------------+    |
|   | Gateway (exposed)                                          |    |
|   +-----------------------------------------------------------+    |
+-------------------------------------------------------------------+
```

### 8.2 Service-to-Service Authentication

| Communication Path         | Authentication Method                          |
|----------------------------|------------------------------------------------|
| Client -> Gateway          | JWT Bearer token in Authorization header       |
| Gateway -> Service         | Gateway-injected headers (trusted internal)    |
| Service -> Service (REST)  | JWT forwarded via Gateway headers              |
| Service -> RabbitMQ        | Username/password per service                  |
| Service -> Database        | Separate credentials per service               |
| Service -> Eureka          | Shared credentials (internal cluster)          |

### 8.3 RabbitMQ Security

Each service has dedicated RabbitMQ credentials with minimum required permissions:

```
# Virtual host: /smartportx
# Permissions per service:

smartportx-yard:
  configure: ^smartportx\.domain-events$
  write:     ^smartportx\.domain-events$
  read:      ^yard\..*$

smartportx-gate:
  configure: ^smartportx\.domain-events$
  write:     ^smartportx\.domain-events$
  read:      ^gate\..*$

smartportx-notification:
  configure: ^smartportx\.notifications$
  write:     ^smartportx\.notifications$
  read:      ^notification\..*$

smartportx-integration:
  configure: ^smartportx\.(domain-events|integration-events)$
  write:     ^smartportx\.integration-events$
  read:      ^integration\..*$
```

### 8.4 Database Access Control

Each microservice connects with its own database credentials and schema-level permissions:

```sql
-- Yard Service credentials
CREATE USER smartportx_yard WITH PASSWORD '${YARD_DB_PASSWORD}';
GRANT USAGE ON SCHEMA yard TO smartportx_yard;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA yard TO smartportx_yard;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA yard TO smartportx_yard;

-- Gate Service credentials (cannot access yard tables)
CREATE USER smartportx_gate WITH PASSWORD '${GATE_DB_PASSWORD}';
GRANT USAGE ON SCHEMA gate TO smartportx_gate;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA gate TO smartportx_gate;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA gate TO smartportx_gate;

-- Read-only user for reporting
CREATE USER smartportx_readonly WITH PASSWORD '${READONLY_DB_PASSWORD}';
GRANT USAGE ON SCHEMA yard, gate, container, vessel TO smartportx_readonly;
GRANT SELECT ON ALL TABLES IN SCHEMA yard, gate, container, vessel TO smartportx_readonly;

-- No service can DROP tables or modify schema
-- Schema migrations handled by dedicated migration user
CREATE USER smartportx_migrate WITH PASSWORD '${MIGRATE_DB_PASSWORD}';
GRANT USAGE, CREATE ON SCHEMA yard, gate, container, vessel, identity TO smartportx_migrate;
GRANT ALL ON ALL TABLES IN SCHEMA yard, gate, container, vessel, identity TO smartportx_migrate;
```

---

## 9. Infrastructure Security

### 9.1 Secrets Management

All secrets are managed via environment variables. No secrets are stored in source code, configuration files, or version control.

**Environment Variables (never in application.yml or git):**

| Variable                          | Description                    |
|-----------------------------------|--------------------------------|
| `DB_YARD_PASSWORD`               | Yard DB connection password    |
| `DB_GATE_PASSWORD`               | Gate DB connection password    |
| `DB_CONTAINER_PASSWORD`          | Container DB connection password|
| `DB_VESSEL_PASSWORD`             | Vessel DB connection password  |
| `RABBITMQ_PASSWORD`              | RabbitMQ connection password   |
| `JWT_PRIVATE_KEY`                | RSA private key for signing    |
| `JWT_PUBLIC_KEY`                 | RSA public key for verification|
| `ENCRYPTION_KEY`                 | AES-256 encryption key         |
| `SSL_KEYSTORE_PASSWORD`          | TLS keystore password          |
| `CUSTOMS_API_KEY`                | External customs API key       |
| `NOTIFICATION_API_KEY`           | Push notification service key  |

**.gitignore enforcement:**

```gitignore
# Secrets - NEVER commit
*.env
.env.*
secrets/
*.p12
*.pem
*.key
keystore.*
application-local.yml
application-dev.yml
```

### 9.2 Docker Security

```dockerfile
# Multi-stage build for minimal attack surface
FROM eclipse-temurin:25-jre-alpine AS runtime

# Non-root user
RUN addgroup -S smartportx && adduser -S smartportx -G smartportx

# Read-only filesystem where possible
USER smartportx

# No package manager in final image
# No shell access in production

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "/app/app.jar"]
```

**Docker image scanning:**

```yaml
# CI/CD pipeline security scanning
security-scan:
  stage: security
  script:
    - trivy image --severity HIGH,CRITICAL smartportx/${SERVICE_NAME}:${TAG}
    - grype smartportx/${SERVICE_NAME}:${TAG}
  allow_failure: false  # Block deployment on HIGH/CRITICAL vulnerabilities
```

### 9.3 Dependency Vulnerability Scanning

| Tool         | Frequency        | Scope               | Action on Finding          |
|--------------|------------------|----------------------|----------------------------|
| Dependabot   | Weekly (automated)| GitHub dependencies  | Auto-PR for patch updates  |
| Snyk         | Every PR         | Maven/npm deps       | Block PR on HIGH+ vuln     |
| OWASP Dep Check | Every build  | Java dependencies    | Fail build on CVSS >= 7    |
| Trivy        | Every image build| Docker images        | Block deploy on CRITICAL   |

### 9.4 Network Segmentation

```yaml
# Docker Compose network isolation
networks:
  smartportx-external:
    driver: bridge
    # Only Gateway is connected here

  smartportx-internal:
    driver: bridge
    internal: true  # No external access
    # All services connected here

  smartportx-data:
    driver: bridge
    internal: true
    # Only services and databases connected here

services:
  smartportx-gateway:
    networks:
      - smartportx-external
      - smartportx-internal

  smartportx-yard:
    networks:
      - smartportx-internal
      - smartportx-data

  postgres-yard:
    networks:
      - smartportx-data  # Cannot reach external network
```

### 9.5 SSH and Container Access

- No SSH access to production containers
- Debug access via `kubectl exec` only with audit logging
- Container runtime: read-only root filesystem where possible
- No `sudo` inside containers
- Resource limits enforced (CPU, memory, PID count)

---

## 10. Security Headers

### 10.1 Gateway Security Headers

All responses from the Gateway include security headers:

```yaml
smartportx:
  gateway:
    security-headers:
      X-Content-Type-Options: nosniff
      X-Frame-Options: DENY
      X-XSS-Protection: "1; mode=block"
      Referrer-Policy: strict-origin-when-cross-origin
      Strict-Transport-Security: "max-age=31536000; includeSubDomains; preload"
      Content-Security-Policy: "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self'"
      Permissions-Policy: "camera=(), microphone=(), geolocation=(), payment=()"
      Cache-Control: "no-store, no-cache, must-revalidate"
      Pragma: "no-cache"
```

### 10.2 Header Descriptions

| Header                          | Value                                         | Purpose                              |
|---------------------------------|-----------------------------------------------|--------------------------------------|
| `X-Content-Type-Options`        | `nosniff`                                     | Prevent MIME type sniffing           |
| `X-Frame-Options`              | `DENY`                                        | Prevent clickjacking (no iframes)    |
| `X-XSS-Protection`             | `1; mode=block`                               | Enable browser XSS filter           |
| `Strict-Transport-Security`    | `max-age=31536000; includeSubDomains; preload`| Force HTTPS for 1 year              |
| `Content-Security-Policy`      | `default-src 'self'; ...`                     | Restrict resource loading origins    |
| `Referrer-Policy`              | `strict-origin-when-cross-origin`             | Control referrer information         |
| `Permissions-Policy`           | `camera=(), microphone=(), ...`               | Disable unused browser features      |
| `Cache-Control`                | `no-store, no-cache, must-revalidate`         | Prevent sensitive data caching       |

### 10.3 HSTS Preload

For production deployment, SmartPortX should submit to the HSTS preload list:

```
max-age=31536000       (1 year)
includeSubDomains      (apply to all subdomains)
preload                (eligible for browser preload lists)
```

---

## 11. Security Testing Strategy

### 11.1 Testing Pyramid

```
           +-----------------+
           |  Penetration    |  Pre-launch
           |    Testing      |  Manual + automated
           +--------+--------+
                    |
           +--------+--------+
           |  Integration     |  Every PR
           |  Security Tests  |  Automated
           +--------+--------+
                    |
           +--------+--------+
           |  Unit Tests      |  Every commit
           |  (Validation)    |  Automated
           +-----------------+
```

### 11.2 Unit Tests — Input Validation

Every commit runs unit tests covering input validation rules:

```java
@DisplayName("Container Number Validation")
class ContainerNumberValidationTest {

    @ParameterizedTest
    @CsvSource({
        "CMAU1234567, true",   // Valid
        "MSKU7654321, true",   // Valid
        "CMAU123456,  false",  // Too short
        "CMAU12345678, false", // Too long
        "cmau1234567, false",  // Lowercase
        "CMA1234567,  false",  // Only 3 letters
        "CMAU0000000, false",  // All zeros
        "CMAU123456O, false",  // Letter O in digits
    })
    void testContainerNumberFormat(String input, boolean expectedValid) {
        ContainerNumberValidator validator = new ContainerNumberValidator();
        assertEquals(expectedValid, validator.isValid(input));
    }

    @Test
    void testSqlInjectionInContainerNumber() {
        CreateContainerRequest request = new CreateContainerRequest();
        request.setContainerNumber("'; DROP TABLE container; --");
        Set<ConstraintViolation<CreateContainerRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
    }

    @Test
    void testXssInContainerNumber() {
        CreateContainerRequest request = new CreateContainerRequest();
        request.setContainerNumber("<script>alert('xss')</script>");
        Set<ConstraintViolation<CreateContainerRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
    }
}
```

### 11.3 Integration Tests — API Security

Every PR runs integration tests covering:

```java
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Testcontainers
class SecurityIntegrationTest {

    @Test
    void requestWithoutToken_Returns401() {
        webTestClient.get()
            .uri("/api/v1/yard/zones")
            .exchange()
            .expectStatus().isUnauthorized();
    }

    @Test
    void requestWithExpiredToken_Returns401() {
        String expiredToken = tokenHelper.generateExpiredToken();
        webTestClient.get()
            .uri("/api/v1/yard/zones")
            .header("Authorization", "Bearer " + expiredToken)
            .exchange()
            .expectStatus().isUnauthorized();
    }

    @Test
    void readOnlyUserCannotModifyYard_Returns403() {
        String readonlyToken = tokenHelper.generateToken("READONLY");
        webTestClient.put()
            .uri("/api/v1/yard/zones/A/positions")
            .header("Authorization", "Bearer " + readonlyToken)
            .bodyValue(updatePositionRequest)
            .exchange()
            .expectStatus().isForbidden();
    }

    @Test
    void rateLimitExceeded_Returns429() {
        String token = tokenHelper.generateValidToken();
        for (int i = 0; i < 101; i++) {
            webTestClient.get()
                .uri("/api/v1/yard/zones")
                .header("Authorization", "Bearer " + token)
                .exchange();
        }
        // 101st request should be rate limited
        webTestClient.get()
            .uri("/api/v1/yard/zones")
            .header("Authorization", "Bearer " + token)
            .exchange()
            .expectStatus().isEqualTo(429);
    }

    @Test
    void containerNumberWithSqlInjection_Returns400() {
        String token = tokenHelper.generateToken("ADMIN");
        webTestClient.post()
            .uri("/api/v1/containers")
            .header("Authorization", "Bearer " + token)
            .bodyValue(Map.of(
                "containerNumber", "'; DROP TABLE container; --",
                "isoCode", "42G1",
                "weightKg", 15000
            ))
            .exchange()
            .expectStatus().isBadRequest();
    }

    @Test
    void securityHeadersPresent() {
        webTestClient.get()
            .uri("/api/v1/yard/zones")
            .header("Authorization", "Bearer " + validToken)
            .exchange()
            .expectHeader().valueEquals("X-Content-Type-Options", "nosniff")
            .expectHeader().valueEquals("X-Frame-Options", "DENY")
            .expectHeader().exists("Strict-Transport-Security")
            .expectHeader().exists("Content-Security-Policy");
    }

    @Test
    void sensitiveDataNotInUrl() {
        String token = tokenHelper.generateToken("ADMIN");
        webTestClient.get()
            .uri(uriBuilder -> uriBuilder
                .path("/api/v1/containers")
                .queryParam("sealNumber", "ML1234567")
                .build())
            .header("Authorization", "Bearer " + token)
            .exchange()
            .expectStatus().isBadRequest(); // Sensitive params in URL rejected
    }
}
```

### 11.4 Penetration Testing

| Scope                    | Frequency   | Scope                          |
|--------------------------|-------------|--------------------------------|
| Full penetration test    | Pre-launch  | All external-facing endpoints  |
| Auth bypass testing      | Pre-launch  | JWT validation, RBAC bypass    |
| API fuzzing              | Pre-launch  | All REST endpoints             |
| WebSocket security       | Pre-launch  | Auth, subscription authorization|
| IDOR testing             | Pre-launch  | Entity access across users     |
| Re-test after fixes      | Pre-launch  | Verify all remediations        |

### 11.5 Dependency Vulnerability Scan

```yaml
# .github/workflows/security-scan.yml
name: Security Scan

on:
  schedule:
    - cron: '0 6 * * 1'  # Weekly on Monday at 6 AM
  pull_request:
    branches: [main, develop]

jobs:
  dependency-scan:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: OWASP Dependency Check
        uses: dependency-check/Dependency-Check_Action@main
        with:
          path: '.'
          format: 'HTML'
          fail_build_on_cvss: 7

      - name: Snyk Security Scan
        uses: snyk/actions/maven@master
        env:
          SNYK_TOKEN: ${{ secrets.SNYK_TOKEN }}
        with:
          args: --severity-threshold=high

  container-scan:
    runs-on: ubuntu-latest
    strategy:
      matrix:
        service: [gateway, identity, yard, gate, container, vessel, integration, notification]
    steps:
      - uses: actions/checkout@v4

      - name: Build Image
        run: docker build -t smartportx/${{ matrix.service }}:scan .

      - name: Trivy Scan
        uses: aquasecurity/trivy-action@master
        with:
          image-ref: smartportx/${{ matrix.service }}:scan
          format: 'sarif'
          severity: 'HIGH,CRITICAL'
          exit-code: '1'  # Fail on HIGH/CRITICAL
```

### 11.6 Access Control Verification

Pre-launch checklist:

- [ ] Guest/unauthenticated user cannot access any `/api/v1/` endpoint
- [ ] READONLY user cannot perform any POST/PUT/DELETE operation
- [ ] GATE_OPERATOR cannot modify yard positions
- [ ] YARD_OPERATOR cannot process gate entries
- [ ] TERMINAL_OP cannot access integration admin endpoints
- [ ] ADMIN cannot access other users' refresh tokens
- [ ] Service A cannot access Service B's database tables
- [ ] RabbitMQ queue permissions prevent cross-service queue access
- [ ] WebSocket subscriptions enforce role-based topic access
- [ ] Audit logs cannot be modified or deleted by application role
- [ ] Encrypted fields cannot be read as plaintext from database
- [ ] JWT cannot be forged with altered claims (signature verification)
- [ ] Expired tokens are rejected by Gateway
- [ ] Revoked refresh tokens are rejected by Identity Service

---

## 12. Incident Response

### 12.1 Security Event Classification

| Severity | Description                               | Response Time | Escalation        |
|----------|-------------------------------------------|---------------|-------------------|
| P1       | Active data breach / unauthorized access  | Immediate     | CTO + Security    |
| P2       | Privilege escalation / auth bypass found  | 1 hour        | Security Lead     |
| P3       | Vulnerability discovered in production    | 4 hours       | Development Lead  |
| P4       | Failed security test / minor finding      | 24 hours      | Team Lead         |
| P5       | Informational / best practice improvement | Next sprint   | Backlog           |

### 12.2 Automated Alerting

| Metric                                      | Threshold      | Alert Channel  |
|---------------------------------------------|----------------|----------------|
| Failed login attempts per user              | > 5 in 5 min   | PagerDuty P2   |
| JWT validation failures per minute          | > 20           | Slack + PagerDuty |
| Circuit breaker OPEN state                  | Any occurrence  | Slack          |
| DLQ message count                           | > 0            | Slack          |
| Unauthorized access attempts (403)          | > 50 per minute| PagerDuty P1   |
| Audit log write failures                    | Any occurrence  | PagerDuty P1   |
| Database connection failures                | Any occurrence  | PagerDuty P1   |
| Container vulnerability (CRITICAL)          | Any in production| PagerDuty P3  |

### 12.3 Security Log Aggregation

All security-relevant logs are aggregated in a centralized logging system:

```
Application Logs ──────┐
Audit Logs ────────────┤
Gateway Access Logs ───┤──▶ Log Aggregator ──▶ Alerting
RabbitMQ Logs ─────────┤                     ──▶ Dashboard
Docker Container Logs ─┘                     ──▶ Retention (24 months)
```

---

## 13. Compliance Considerations

### 13.1 Data Classification

| Classification    | Examples                          | Handling Requirements                    |
|-------------------|-----------------------------------|------------------------------------------|
| **Restricted**    | Passwords, API keys, JWT private key | Encrypted at rest, access-logged       |
| **Confidential**  | Customs data, seal numbers, PII   | Encrypted at rest, role-based access     |
| **Internal**      | Container positions, vessel schedules | Standard access controls              |
| **Public**        | Port hours, public APIs           | No special handling required             |

### 13.2 Standards Alignment

| Standard    | Applicability                                | SmartPortX Coverage                |
|-------------|----------------------------------------------|------------------------------------|
| OWASP Top 10| Web application security                     | Sections 2-11 address each item    |
| ISO 27001   | Information security management              | Sections 7, 8, 9                   |
| GDPR        | Personal data protection (driver PII)        | Sections 4, 6, 7                   |
| ISPS Code   | International port facility security         | Sections 3, 5, 8                   |
| SOLAS       | Safety of life at sea (container weight)     | Sections 6, 7                      |

---

*End of Security Architecture Document*
