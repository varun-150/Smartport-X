# SmartPortX API Contract Documentation

> **Version:** 1.0.0  
> **Date:** 2026-08-26  
> **Status:** Draft  
> **Specification:** OpenAPI 3.1 compatible  
> **Architecture:** Service-Oriented Architecture (SOA)

---

## Table of Contents

1. [Overview](#1-overview)
2. [API Standards](#2-api-standards)
   - [Base URL & Versioning](#21-base-url--versioning)
   - [Authentication & Authorization](#22-authentication--authorization)
   - [Content Negotiation](#23-content-negotiation)
   - [Pagination](#24-pagination)
   - [Sorting & Filtering](#25-sorting--filtering)
   - [Error Handling](#26-error-handling)
   - [Rate Limiting](#27-rate-limiting)
   - [Idempotency](#28-idempotency)
3. [Identity Service](#3-identity-service)
   - [Authentication Endpoints](#31-authentication-endpoints)
   - [User Management Endpoints](#32-user-management-endpoints)
   - [Role Management Endpoints](#33-role-management-endpoints)
4. [Yard Service](#4-yard-service)
   - [Yard Layout Endpoints](#41-yard-layout-endpoints)
   - [Optimization Endpoints](#42-optimization-endpoints)
   - [Yard Metrics Endpoints](#43-yard-metrics-endpoints)
5. [Container Service](#5-container-service)
   - [Container CRUD Endpoints](#51-container-crud-endpoints)
   - [Container Lifecycle Endpoints](#52-container-lifecycle-endpoints)
   - [Container History Endpoints](#53-container-history-endpoints)
6. [Gate Service](#6-gate-service)
   - [Appointment Endpoints](#61-appointment-endpoints)
   - [Gate Processing Endpoints](#62-gate-processing-endpoints)
   - [Queue & Quota Endpoints](#63-queue--quota-endpoints)
7. [Vessel Service](#7-vessel-service)
   - [Vessel Management Endpoints](#71-vessel-management-endpoints)
   - [Schedule Endpoints](#72-schedule-endpoints)
   - [Discharge Operation Endpoints](#73-discharge-operation-endpoints)
8. [Integration Service](#8-integration-service)
   - [EDIFACT Endpoints](#81-edifact-endpoints)
   - [IoT Endpoints](#82-iot-endpoints)
   - [Customs Endpoints](#83-customs-endpoints)
9. [Notification Service](#9-notification-service)
   - [WebSocket Endpoints](#91-websocket-endpoints)
   - [Notification Preferences Endpoints](#92-notification-preferences-endpoints)
10. [Data Models](#10-data-models)
11. [Event Schema](#11-event-schema)
12. [Security Considerations](#12-security-considerations)
13. [Change Log](#13-change-log)

---

## 1. Overview

SmartPortX is a Service-Oriented Architecture (SOA) container port logistics platform composed of 8 microservices. This document defines the complete REST/JSON API contracts for inter-service and client-facing communication.

### Service Registry

| Service | Artifact | Port (Direct) | Gateway Route | Description |
|---------|----------|---------------|---------------|-------------|
| API Gateway | smartportx-gateway | 8080 | — | Central entry point, routing, auth |
| Identity | smartportx-identity | 8081 | /api/v1/auth/*, /api/v1/users/*, /api/v1/roles/* | Authentication, authorization, user management |
| Yard | smartportx-yard | 8082 | /api/v1/yard/* | Yard layout, stacking, optimization |
| Container | smartportx-container | 8084 | /api/v1/containers/* | Container registry, lifecycle, history |
| Gate | smartportx-gate | 8084 | /api/v1/appointments/*, /api/v1/gate/* | Appointments, gate-in/out processing |
| Vessel | smartportx-vessel | 8085 | /api/v1/vessels/* | Vessel registry, schedules, discharge ops |
| Integration | smartportx-integration | 8086 | /api/v1/integration/* | EDIFACT, IoT, customs integration |
| Notification | smartportx-notification | 8087 | /api/v1/notifications/*, /ws/* | Real-time alerts, preferences |

### Technology Stack

- **Runtime:** Java 25 (Virtual Threads)
- **Framework:** Spring Boot 3.x (Spring WebFlux / Spring MVC)
- **API Gateway:** Spring Cloud Gateway
- **Messaging:** Apache Kafka (async events)
- **Database:** PostgreSQL 16 (per-service schema)
- **Cache:** Redis 7.x
- **Service Discovery:** Eureka or Consul

---

## 2. API Standards

### 2.1 Base URL & Versioning

All client-facing traffic enters through the **API Gateway**:

```
http://localhost:8080
```

All REST endpoints use **URL-based versioning** under the prefix:

```
/api/v1/{resource}
```

- **Version 1** (`/api/v1/`) is the current and only supported version.
- Breaking changes will introduce `/api/v2/` endpoints.
- Non-breaking additions (new fields, new endpoints) do not require a version bump.
- Deprecated endpoints include a `Sunset` header with the removal date.

**Direct Service Access (internal/inter-service):**

Each microservice is reachable directly for internal traffic (service mesh / internal network):

| Service | Direct Base URL |
|---------|-----------------|
| Identity | `http://smartportx-identity:8081/api/v1/` |
| Yard | `http://smartportx-yard:8082/api/v1/` |
| Container | `http://smartportx-container:8084/api/v1/` |
| Gate | `http://smartportx-gate:8084/api/v1/` |
| Vessel | `http://smartportx-vessel:8085/api/v1/` |
| Integration | `http://smartportx-integration:8086/api/v1/` |
| Notification | `http://smartportx-notification:8087/api/v1/` |

### 2.2 Authentication & Authorization

**Mechanism:** Stateless JWT (JSON Web Token) with Bearer token scheme.

**Flow:**

1. Client authenticates via `POST /api/v1/auth/login` with credentials.
2. Server returns `accessToken` (short-lived, 15 minutes) and `refreshToken` (long-lived, 7 days).
3. Client includes the access token in subsequent requests:

```
Authorization: Bearer eyJhbGciOiJSUzI1NiIs...
```

4. When the access token expires, client calls `POST /api/v1/auth/refresh`.
5. Refresh tokens are single-use; each refresh issues a new refresh token.

**Token Payload (JWT Claims):**

```json
{
  "sub": "uuid-of-user",
  "iss": "smartportx-identity",
  "aud": "smartportx",
  "iat": 1724668200,
  "exp": 1724669100,
  "roles": ["TERMINAL_OP"],
  "permissions": ["container:read", "yard:write", "gate:read"]
}
```

**Role Hierarchy:**

| Role | Description | Key Permissions |
|------|-------------|-----------------|
| `ADMIN` | System administrator | Full access to all services |
| `TERMINAL_OP` | Terminal operator | Container, yard, vessel, gate operations |
| `YARD_MANAGER` | Yard manager | Yard layout, optimization, container positioning |
| `GATE_OPERATOR` | Gate operator | Gate processing, appointments, queue management |
| `READONLY` | Read-only viewer | Read access to all dashboards |

**Protected Endpoints:**

- All endpoints require authentication unless explicitly marked as `PUBLIC`.
- Role-based access control (RBAC) is enforced per endpoint.
- Inter-service calls use a service-to-service JWT (different issuer, higher privileges).

**Security Headers:**

```
X-Content-Type-Options: nosniff
X-Frame-Options: DENY
Strict-Transport-Security: max-age=31536000; includeSubDomains
Cache-Control: no-store
```

### 2.3 Content Negotiation

- **Request Content-Type:** `application/json`
- **Response Content-Type:** `application/json`
- **Charset:** UTF-8
- **Date/Time Format:** ISO 8601 / RFC 3339 (`2026-08-26T10:30:00Z`)
- **Duration Format:** ISO 8601 (e.g., `PT15M` for 15 minutes)
- **UUID Format:** RFC 4122 v4 (`550e8400-e29b-41d4-a716-446655440000`)
- **Decimal Precision:** Currency values use 2 decimal places; percentages use 4 decimal places (0.0000–1.0000)

**Request Headers (Required):**

| Header | Value | Notes |
|--------|-------|-------|
| `Content-Type` | `application/json` | Required for request bodies |
| `Authorization` | `Bearer {token}` | Required for authenticated endpoints |
| `Accept` | `application/json` | Recommended |

**Request Headers (Optional):**

| Header | Value | Notes |
|--------|-------|-------|
| `X-Request-ID` | UUID | Client-generated request correlation ID |
| `X-Idempotency-Key` | UUID | Ensures idempotent processing (see §2.8) |
| `Accept-Language` | `en-US` | Locale for error messages (future) |

### 2.4 Pagination

Large result sets use **cursor-based pagination** to ensure consistent results across concurrent modifications.

**Request Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `cursor` | string | (none) | Opaque cursor from previous response; omit for first page |
| `limit` | integer | 25 | Page size (1–100) |

**Response Envelope:**

```json
{
  "data": [ ... ],
  "pagination": {
    "nextCursor": "eyJpZCI6MTAwfQ==",
    "hasMore": true,
    "totalCount": 1247,
    "limit": 25
  }
}
```

- `nextCursor`: Pass as `?cursor={nextCursor}` to get the next page. `null` when `hasMore` is `false`.
- `totalCount`: Total number of matching records (omitted for performance-heavy queries; use `X-Total-Count` header).
- `hasMore`: Boolean indicating additional pages exist.

**First Page:**

```
GET /api/v1/containers?limit=25
```

**Subsequent Page:**

```
GET /api/v1/containers?cursor=eyJpZCI6MTAwfQ==&limit=25
```

**Keyset Pagination Alternative:**

For specific endpoints, keyset pagination is supported as an alternative:

```
GET /api/v1/containers?createdAfter=2026-08-20T00:00:00Z&limit=25
```

### 2.5 Sorting & Filtering

**Sorting:**

```
GET /api/v1/containers?sort=createdAt,desc
GET /api/v1/containers?sort=owner,asc&sort=weight,desc
```

- Format: `?sort={field},{direction}` where direction is `asc` or `desc`.
- Multiple sort fields: repeat the `sort` parameter.
- Default sort varies by endpoint (documented per endpoint).

**Filtering:**

```
GET /api/v1/containers?filter[status]=IN_YARD
GET /api/v1/containers?filter[owner]=MSC&filter[type]=40DC
GET /api/v1/containers?filter[weight][min]=10000&filter[weight][max]=30000
```

- Simple equality: `?filter[field]=value`
- Range operators: `?filter[field][min]=X&filter[field][max]=Y`
- List operators: `?filter[field]=A&filter[field]=B` (OR logic)
- Date range: `?filter[createdAt][after]=2026-08-01T00:00:00Z&filter[createdAt][before]=2026-08-31T23:59:59Z`

### 2.6 Error Handling

All errors follow **RFC 9457 (Problem Details for HTTP APIs)**.

**Error Response Body:**

```json
{
  "type": "https://api.smartportx.com/errors/{error-code}",
  "title": "Human-readable error summary",
  "status": 422,
  "detail": "Specific description of what went wrong",
  "instance": "/api/v1/containers/MSKU1234567",
  "timestamp": "2026-08-26T10:30:00Z",
  "traceId": "abc-123-def-456",
  "errors": [
    {
      "field": "containerNumber",
      "message": "Container number must match pattern [A-Z]{4}[0-9]{7}",
      "rejectedValue": "msku1234567"
    }
  ]
}
```

**Standard Error Codes:**

| HTTP Status | Error Type URI | Title | When |
|-------------|----------------|-------|------|
| 400 | `/errors/validation-failed` | Validation Failed | Request body fails Bean Validation |
| 401 | `/errors/unauthorized` | Unauthorized | Missing, expired, or invalid JWT |
| 403 | `/errors/forbidden` | Forbidden | Valid token but insufficient permissions |
| 404 | `/errors/resource-not-found` | Resource Not Found | Requested entity does not exist |
| 409 | `/errors/conflict` | Conflict | Duplicate resource or state conflict |
| 422 | `/errors/business-rule-violation` | Business Rule Violation | Valid syntax but violates business logic |
| 429 | `/errors/rate-limit-exceeded` | Rate Limit Exceeded | Too many requests |
| 500 | `/errors/internal-error` | Internal Server Error | Unexpected server failure |
| 503 | `/errors/service-unavailable` | Service Unavailable | Upstream dependency failure |

**Validation Error Example (400):**

```json
{
  "type": "https://api.smartportx.com/errors/validation-failed",
  "title": "Validation Failed",
  "status": 400,
  "detail": "Request body contains invalid fields",
  "instance": "/api/v1/containers",
  "timestamp": "2026-08-26T10:30:00Z",
  "traceId": "xyz-789-uvw-012",
  "errors": [
    {
      "field": "containerNumber",
      "message": "must match pattern [A-Z]{4}[0-9]{7}",
      "rejectedValue": "msku123"
    },
    {
      "field": "weight",
      "message": "must be greater than 0",
      "rejectedValue": -500
    }
  ]
}
```

**Business Rule Violation Example (422):**

```json
{
  "type": "https://api.smartportx.com/errors/invalid-status-transition",
  "title": "Invalid Status Transition",
  "status": 422,
  "detail": "Cannot transition container from LOADED to RECEIVED",
  "instance": "/api/v1/containers/550e8400-e29b-41d4-a716-446655440000/status",
  "timestamp": "2026-08-26T10:30:00Z",
  "traceId": "def-456-ghi-789"
}
```

### 2.7 Rate Limiting

Rate limits are applied per API key (JWT `sub` claim):

| Endpoint Category | Rate Limit | Window |
|-------------------|------------|--------|
| Authentication (`/auth/*`) | 10 requests | 1 minute |
| Read operations (GET) | 300 requests | 1 minute |
| Write operations (POST/PUT/DELETE) | 60 requests | 1 minute |
| Bulk operations | 10 requests | 1 minute |

**Rate Limit Headers:**

```
X-RateLimit-Limit: 300
X-RateLimit-Remaining: 287
X-RateLimit-Reset: 1724668260
Retry-After: 30
```

When rate limit is exceeded, the API returns **429 Too Many Requests** with the `Retry-After` header.

### 2.8 Idempotency

Write operations (POST, PUT, DELETE) support idempotency via the `X-Idempotency-Key` header.

- The key is cached for 24 hours.
- Replaying a request with the same key returns the original response without re-executing.
- Idempotency keys are scoped to the authenticated user.
- GET and DELETE operations are inherently idempotent.

---

## 3. Identity Service

> **Service:** smartportx-identity  
> **Gateway Route:** `/api/v1/auth/*`, `/api/v1/users/*`, `/api/v1/roles/*`  
> **Database:** smartportx_identity  
> **Responsibilities:** Authentication, authorization, user lifecycle, role-based access control

### 3.1 Authentication Endpoints

#### POST /api/v1/auth/login

Authenticate a user and issue JWT access and refresh tokens.

**Authentication:** None (public endpoint)

**Request Body:**

```json
{
  "username": "jdoe",
  "password": "s3cureP@ssw0rd"
}
```

| Field | Type | Required | Validation |
|-------|------|----------|------------|
| `username` | string | yes | 3–50 characters, alphanumeric + underscore |
| `password` | string | yes | 8–128 characters |

**Success Response — 200 OK:**

```json
{
  "accessToken": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "dGhpcyBpcyBhIHJlZnJlc2ggdG9rZW4...",
  "expiresIn": 900,
  "tokenType": "Bearer",
  "user": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "username": "jdoe",
    "displayName": "John Doe",
    "email": "jdoe@smartportx.com",
    "role": "TERMINAL_OP",
    "permissions": [
      "container:read",
      "container:write",
      "yard:read",
      "yard:write",
      "gate:read"
    ]
  }
}
```

**Error Responses:**

| Status | Condition |
|--------|-----------|
| 400 | Missing required fields |
| 401 | Invalid credentials |
| 429 | Too many login attempts |
| 503 | Identity service unavailable |

---

#### POST /api/v1/auth/refresh

Exchange a refresh token for a new access token. Each refresh token is single-use.

**Authentication:** None (public endpoint, but requires valid refresh token)

**Request Body:**

```json
{
  "refreshToken": "dGhpcyBpcyBhIHJlZnJlc2ggdG9rZW4..."
}
```

| Field | Type | Required | Validation |
|-------|------|----------|------------|
| `refreshToken` | string | yes | Non-empty, valid Base64 JWT |

**Success Response — 200 OK:**

```json
{
  "accessToken": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "bmV3IHJlZnJlc2ggdG9rZW4...",
  "expiresIn": 900,
  "tokenType": "Bearer"
}
```

**Error Responses:**

| Status | Condition |
|--------|-----------|
| 400 | Missing refresh token |
| 401 | Refresh token expired, revoked, or already used |
| 503 | Identity service unavailable |

---

#### POST /api/v1/auth/logout

Invalidate the current refresh token, preventing further token refresh.

**Authentication:** Bearer token required

**Request Body:** None

**Success Response — 204 No Content**

**Error Responses:**

| Status | Condition |
|--------|-----------|
| 401 | Invalid or expired token |

---

### 3.2 User Management Endpoints

#### GET /api/v1/users

List all users with pagination and filtering.

**Authentication:** Bearer token required  
**Authorization:** `ADMIN` role only

**Query Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `cursor` | string | — | Pagination cursor |
| `limit` | integer | 25 | Page size (1–100) |
| `sort` | string | `createdAt,desc` | Sort field and direction |
| `filter[role]` | string | — | Filter by role |
| `filter[status]` | string | — | Filter by status: `ACTIVE`, `INACTIVE`, `LOCKED` |
| `filter[search]` | string | — | Search by username, email, or display name |

**Success Response — 200 OK:**

```json
{
  "data": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "username": "jdoe",
      "displayName": "John Doe",
      "email": "jdoe@smartportx.com",
      "role": "TERMINAL_OP",
      "status": "ACTIVE",
      "lastLoginAt": "2026-08-26T08:15:00Z",
      "createdAt": "2026-01-15T10:00:00Z",
      "updatedAt": "2026-08-20T14:30:00Z"
    }
  ],
  "pagination": {
    "nextCursor": "eyJpZCI6MTAwfQ==",
    "hasMore": true,
    "totalCount": 47,
    "limit": 25
  }
}
```

---

#### POST /api/v1/users

Create a new user account.

**Authentication:** Bearer token required  
**Authorization:** `ADMIN` role only

**Request Body:**

```json
{
  "username": "asmith",
  "email": "asmith@smartportx.com",
  "displayName": "Alice Smith",
  "password": "T3mpP@ss!",
  "role": "GATE_OPERATOR",
  "status": "ACTIVE"
}
```

| Field | Type | Required | Validation |
|-------|------|----------|------------|
| `username` | string | yes | 3–50 chars, alphanumeric + underscore, unique |
| `email` | string | yes | Valid email, unique |
| `displayName` | string | yes | 2–100 characters |
| `password` | string | yes | 8–128 chars, min 1 uppercase, 1 number, 1 special |
| `role` | enum | yes | One of: `ADMIN`, `TERMINAL_OP`, `YARD_MANAGER`, `GATE_OPERATOR`, `READONLY` |
| `status` | enum | no | `ACTIVE` (default), `INACTIVE` |

**Success Response — 201 Created:**

```json
{
  "id": "660e8400-e29b-41d4-a716-446655440001",
  "username": "asmith",
  "email": "asmith@smartportx.com",
  "displayName": "Alice Smith",
  "role": "GATE_OPERATOR",
  "status": "ACTIVE",
  "createdAt": "2026-08-26T10:30:00Z"
}
```

**Error Responses:**

| Status | Condition |
|--------|-----------|
| 400 | Validation errors (missing fields, weak password) |
| 409 | Username or email already exists |

---

#### GET /api/v1/users/{id}

Get detailed information about a specific user.

**Authentication:** Bearer token required  
**Authorization:** `ADMIN` role, or the user's own record

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `id` | UUID | User ID |

**Success Response — 200 OK:**

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "username": "jdoe",
  "displayName": "John Doe",
  "email": "jdoe@smartportx.com",
  "role": "TERMINAL_OP",
  "permissions": [
    "container:read",
    "container:write",
    "yard:read",
    "yard:write",
    "gate:read"
  ],
  "status": "ACTIVE",
  "lastLoginAt": "2026-08-26T08:15:00Z",
  "createdAt": "2026-01-15T10:00:00Z",
  "updatedAt": "2026-08-20T14:30:00Z"
}
```

**Error Responses:**

| Status | Condition |
|--------|-----------|
| 404 | User not found |

---

#### PUT /api/v1/users/{id}

Update user details. Partial updates supported.

**Authentication:** Bearer token required  
**Authorization:** `ADMIN` role, or the user's own record (limited fields)

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `id` | UUID | User ID |

**Request Body (all fields optional):**

```json
{
  "displayName": "John D.",
  "email": "john.doe@smartportx.com",
  "role": "YARD_MANAGER",
  "status": "ACTIVE"
}
```

**Success Response — 200 OK:** Returns the updated user object.

**Error Responses:**

| Status | Condition |
|--------|-----------|
| 400 | Validation errors |
| 404 | User not found |
| 409 | Email already in use |

---

#### DELETE /api/v1/users/{id}

Soft-delete a user account (sets status to `INACTIVE`, invalidates all tokens).

**Authentication:** Bearer token required  
**Authorization:** `ADMIN` role only

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `id` | UUID | User ID |

**Success Response — 204 No Content**

**Error Responses:**

| Status | Condition |
|--------|-----------|
| 404 | User not found |
| 409 | Cannot delete the last active admin |

---

### 3.3 Role Management Endpoints

#### GET /api/v1/roles

List all roles with their assigned permissions.

**Authentication:** Bearer token required

**Success Response — 200 OK:**

```json
{
  "data": [
    {
      "id": "110e8400-e29b-41d4-a716-446655440010",
      "name": "ADMIN",
      "description": "System administrator with full access",
      "permissions": [
        "user:read",
        "user:write",
        "user:delete",
        "container:read",
        "container:write",
        "container:delete",
        "yard:read",
        "yard:write",
        "yard:optimize",
        "gate:read",
        "gate:write",
        "gate:admin",
        "vessel:read",
        "vessel:write",
        "integration:read",
        "integration:write",
        "notification:manage",
        "system:admin"
      ],
      "isSystem": true
    },
    {
      "id": "110e8400-e29b-41d4-a716-446655440011",
      "name": "TERMINAL_OP",
      "description": "Terminal operator with container and yard access",
      "permissions": [
        "container:read",
        "container:write",
        "yard:read",
        "yard:write",
        "gate:read",
        "vessel:read",
        "integration:read"
      ],
      "isSystem": true
    },
    {
      "id": "110e8400-e29b-41d4-a716-446655440012",
      "name": "YARD_MANAGER",
      "description": "Yard manager with optimization privileges",
      "permissions": [
        "container:read",
        "container:write",
        "yard:read",
        "yard:write",
        "yard:optimize",
        "vessel:read"
      ],
      "isSystem": true
    },
    {
      "id": "110e8400-e29b-41d4-a716-446655440013",
      "name": "GATE_OPERATOR",
      "description": "Gate operator with appointment and check-in/out access",
      "permissions": [
        "container:read",
        "yard:read",
        "gate:read",
        "gate:write",
        "appointment:read",
        "appointment:write"
      ],
      "isSystem": true
    },
    {
      "id": "110e8400-e29b-41d4-a716-446655440014",
      "name": "READONLY",
      "description": "Read-only viewer for dashboards and reports",
      "permissions": [
        "container:read",
        "yard:read",
        "gate:read",
        "vessel:read",
        "integration:read"
      ],
      "isSystem": true
    }
  ]
}
```

---

#### POST /api/v1/roles

Create a custom role with a subset of permissions.

**Authentication:** Bearer token required  
**Authorization:** `ADMIN` role only

**Request Body:**

```json
{
  "name": "YARD_VIEWER",
  "description": "Read-only access to yard layout and metrics",
  "permissions": [
    "yard:read",
    "container:read"
  ]
}
```

| Field | Type | Required | Validation |
|-------|------|----------|------------|
| `name` | string | yes | 2–50 chars, uppercase, no spaces, unique |
| `description` | string | yes | 10–200 characters |
| `permissions` | array[string] | yes | Non-empty subset of valid permissions |

**Success Response — 201 Created:** Returns the created role object.

**Error Responses:**

| Status | Condition |
|--------|-----------|
| 400 | Validation errors |
| 409 | Role name already exists |

---

## 4. Yard Service

> **Service:** smartportx-yard  
> **Gateway Route:** `/api/v1/yard/*`  
> **Database:** smartportx_yard  
> **Responsibilities:** Yard layout management, container positioning, stacking optimization, utilization metrics

### 4.1 Yard Layout Endpoints

#### GET /api/v1/yard/layout

Retrieve the complete yard layout including all blocks, bays, rows, and tiers with occupancy status.

**Authentication:** Bearer token required  
**Authorization:** Any authenticated user

**Query Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `includeEmpty` | boolean | true | Include empty slots in response |
| `blockFilter` | string | — | Filter to specific block(s), comma-separated |

**Success Response — 200 OK:**

```json
{
  "yardId": "yard-001",
  "yardName": "Main Terminal Yard",
  "totalBlocks": 8,
  "totalSlots": 10000,
  "occupiedSlots": 7500,
  "utilizationRate": 0.75,
  "lastUpdated": "2026-08-26T10:29:00Z",
  "blocks": [
    {
      "blockId": "b1a2c3d4-e5f6-7890-abcd-ef1234567890",
      "blockCode": "B01",
      "name": "Block 1 - Export",
      "type": "EXPORT",
      "totalBays": 20,
      "totalRows": 6,
      "totalTiers": 5,
      "totalSlots": 600,
      "occupiedSlots": 450,
      "utilizationRate": 0.75,
      "bays": [
        {
          "bayId": "c2b3d4e5-f6a7-8901-bcde-f12345678901",
          "bayNumber": 1,
          "rows": [
            {
              "rowId": "d3c4e5f6-a7b8-9012-cdef-123456789012",
              "rowNumber": 1,
              "tiers": [
                {
                  "tierId": "e4d5f6a7-b8c9-0123-def0-234567890123",
                  "tierNumber": 1,
                  "position": "B01-B01-R01-T01",
                  "status": "OCCUPIED",
                  "containerId": "550e8400-e29b-41d4-a716-446655440000",
                  "containerNumber": "MSKU1234567",
                  "containerType": "40HC",
                  "stackWeight": 28000,
                  "maxWeight": 60000,
                  "dangerousGoods": false,
                  "estimatedDeparture": "2026-08-28T06:00:00Z"
                },
                {
                  "tierId": "f5e6a7b8-c9d0-1234-ef01-345678901234",
                  "tierNumber": 2,
                  "position": "B01-B01-R01-T02",
                  "status": "EMPTY",
                  "containerId": null,
                  "containerNumber": null
                }
              ]
            }
          ]
        }
      ]
    }
  ]
}
```

**Container Position Format:**

Positions follow the pattern `B{block}-B{bay}-R{row}-T{tier}`:
- **Block:** Logical grouping of containers (e.g., by export/import/refrigerated)
- **Bay:** Column within a block (numbered 1–N)
- **Row:** Depth position within a bay (numbered 1–N, 1 = ground level front)
- **Tier:** Vertical stack level (numbered 1–N, 1 = ground)

---

#### GET /api/v1/yard/blocks

List all blocks with summary information.

**Authentication:** Bearer token required

**Success Response — 200 OK:**

```json
{
  "data": [
    {
      "blockId": "b1a2c3d4-e5f6-7890-abcd-ef1234567890",
      "blockCode": "B01",
      "name": "Block 1 - Export",
      "type": "EXPORT",
      "totalSlots": 600,
      "occupiedSlots": 450,
      "utilizationRate": 0.75,
      "averageDwellDays": 2.8,
      "dangerousGoodsAllowed": false,
      "refrigeratedSlots": 0
    }
  ],
  "pagination": null
}
```

**Block Types:**

| Type | Description |
|------|-------------|
| `EXPORT` | Containers awaiting vessel loading |
| `IMPORT` | Containers discharged from vessels |
| `TRANSIT` | Through containers (transshipment) |
| `REFRIGERATED` | Power-supplied slots for reefer containers |
| `DANGEROUS` | IMO-classified dangerous goods area |
| `EMPTY` | Empty container storage |

---

#### GET /api/v1/yard/blocks/{blockId}

Get detailed information about a specific block including bay-level occupancy.

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `blockId` | UUID | Block ID |

**Success Response — 200 OK:** Returns full block object with nested bays, rows, tiers.

---

#### PUT /api/v1/yard/blocks/{blockId}

Update block configuration (name, type, dangerous goods policy).

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `blockId` | UUID | Block ID |

**Request Body:**

```json
{
  "name": "Block 1 - Import Priority",
  "type": "IMPORT",
  "dangerousGoodsAllowed": true,
  "maxStackWeight": 70000
}
```

**Success Response — 200 OK:** Returns updated block object.

---

#### GET /api/v1/yard/blocks/{blockId}/bays

List all bays within a block with occupancy data.

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `blockId` | UUID | Block ID |

**Success Response — 200 OK:**

```json
{
  "data": [
    {
      "bayId": "c2b3d4e5-f6a7-8901-bcde-f12345678901",
      "bayNumber": 1,
      "totalRows": 6,
      "totalTiers": 5,
      "totalSlots": 30,
      "occupiedSlots": 22,
      "utilizationRate": 0.7333,
      "maxStackWeight": 60000
    }
  ]
}
```

---

#### GET /api/v1/yard/blocks/{blockId}/bays/{bayId}/rows/{rowId}/tiers/{tierId}

Get the detailed status of a specific tier position.

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `blockId` | UUID | Block ID |
| `bayId` | UUID | Bay ID |
| `rowId` | UUID | Row ID |
| `tierId` | UUID | Tier ID |

**Success Response — 200 OK:**

```json
{
  "tierId": "e4d5f6a7-b8c9-0123-def0-234567890123",
  "position": "B01-B01-R01-T01",
  "status": "OCCUPIED",
  "container": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "number": "MSKU1234567",
    "type": "40HC",
    "owner": "MSC",
    "weight": 28000,
    "status": "IN_YARD",
    "dangerousGoods": null,
    "receivedAt": "2026-08-24T14:30:00Z",
    "estimatedDeparture": "2026-08-28T06:00:00Z"
  },
  "stackWeight": 28000,
  "maxWeight": 60000,
  "stackCount": 1,
  "lastModified": "2026-08-24T14:30:00Z"
}
```

---

#### PUT /api/v1/yard/blocks/{blockId}/bays/{bayId}/rows/{rowId}/tiers/{tierId}/container

Assign a container to a specific yard position.

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `blockId` | UUID | Block ID |
| `bayId` | UUID | Bay ID |
| `rowId` | UUID | Row ID |
| `tierId` | UUID | Tier ID |

**Request Body:**

```json
{
  "containerId": "550e8400-e29b-41d4-a716-446655440000",
  "position": "B01-B01-R01-T01"
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `containerId` | UUID | yes | Container to place |
| `position` | string | no | Position string (auto-derived from path if omitted) |

**Success Response — 200 OK:** Returns updated tier with container details.

**Error Responses:**

| Status | Condition |
|--------|-----------|
| 404 | Block/bay/row/tier or container not found |
| 409 | Position already occupied |
| 422 | Container weight exceeds tier max weight; container status not valid for positioning |

---

#### DELETE /api/v1/yard/blocks/{blockId}/bays/{bayId}/rows/{rowId}/tiers/{tierId}/container

Remove a container from a yard position.

**Path Parameters:** Same as PUT above.

**Request Body:** None

**Success Response — 200 OK:**

```json
{
  "removedContainerId": "550e8400-e29b-41d4-a716-446655440000",
  "position": "B01-B01-R01-T01",
  "removedAt": "2026-08-26T10:30:00Z",
  "movedTo": null
}
```

**Error Responses:**

| Status | Condition |
|--------|-----------|
| 404 | Position not found or position empty |
| 422 | Cannot remove — containers stacked above must be removed first |

---

### 4.2 Optimization Endpoints

#### POST /api/v1/yard/optimize

Run stacking optimization algorithm on a specific block.

**Authentication:** Bearer token required  
**Authorization:** `ADMIN` or `YARD_MANAGER` roles

**Request Body:**

```json
{
  "blockId": "b1a2c3d4-e5f6-7890-abcd-ef1234567890",
  "strategy": "DEPARTURE_CLUSTER",
  "dryRun": true,
  "constraints": {
    "maxResufflesPerMove": 5,
    "preserveReeferPositions": true,
    "avoidDangerousMixing": true
  }
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `blockId` | UUID | yes | Block to optimize |
| `strategy` | enum | yes | Optimization strategy |
| `dryRun` | boolean | no | If true, return recommendations without executing (default: true) |
| `constraints` | object | no | Additional optimization constraints |

**Optimization Strategies:**

| Strategy | Description |
|----------|-------------|
| `FIFO` | First-in-first-out: minimize dwell time by placing newest containers on top |
| `WEIGHT_CLUSTER` | Cluster by weight to minimize crane moves during loading |
| `DEPARTURE_CLUSTER` | Group containers by estimated departure time to reduce reshuffles |
| `BALANCED` | Weighted combination of all strategies |

**Success Response — 200 OK:**

```json
{
  "optimizationId": "770e8400-e29b-41d4-a716-446655440077",
  "blockId": "b1a2c3d4-e5f6-7890-abcd-ef1234567890",
  "strategy": "DEPARTURE_CLUSTER",
  "dryRun": true,
  "analysis": {
    "totalContainers": 450,
    "currentReshuffles": 67,
    "projectedReshuffles": 38,
    "estimatedReshufflingReduction": 0.4328,
    "containersAffected": 112,
    "estimatedExecutionTimeMs": 45000
  },
  "recommendations": [
    {
      "sequence": 1,
      "containerId": "550e8400-e29b-41d4-a716-446655440000",
      "containerNumber": "MSKU1234567",
      "currentPosition": "B01-B03-R02-T03",
      "recommendedPosition": "B01-B05-R01-T01",
      "reason": "Departure on 2026-08-27 — move to top of stack for early access",
      "moveType": "RESHUFFLE",
      "priority": "HIGH"
    },
    {
      "sequence": 2,
      "containerId": "880e8400-e29b-41d4-a716-446655440088",
      "containerNumber": "TCLU9876543",
      "currentPosition": "B01-B05-R01-T01",
      "recommendedPosition": "B01-B02-R04-T02",
      "reason": "Departure on 2026-08-30 — relocate to make room for priority container",
      "moveType": "RESHUFFLE",
      "priority": "MEDIUM"
    }
  ],
  "executedAt": null
}
```

**Dry Run = false (Execute):**

When `dryRun` is `false`, the optimization is executed and the response includes execution results:

```json
{
  "optimizationId": "770e8400-e29b-41d4-a716-446655440077",
  "status": "COMPLETED",
  "executedAt": "2026-08-26T10:35:00Z",
  "completedAt": "2026-08-26T10:35:45Z",
  "movesCompleted": 112,
  "movesFailed": 0,
  "actualReshufflingReduction": 0.4311
}
```

---

#### GET /api/v1/yard/optimize/history

Get history of optimization runs.

**Query Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `cursor` | string | — | Pagination cursor |
| `limit` | integer | 25 | Page size |
| `filter[blockId]` | UUID | — | Filter by block |
| `filter[status]` | string | — | Filter by status: `COMPLETED`, `FAILED`, `IN_PROGRESS` |

**Success Response — 200 OK:**

```json
{
  "data": [
    {
      "optimizationId": "770e8400-e29b-41d4-a716-446655440077",
      "blockId": "b1a2c3d4-e5f6-7890-abcd-ef1234567890",
      "blockCode": "B01",
      "strategy": "DEPARTURE_CLUSTER",
      "status": "COMPLETED",
      "dryRun": false,
      "containersAffected": 112,
      "reshufflingReduction": 0.4311,
      "triggeredBy": "jdoe",
      "executedAt": "2026-08-26T10:35:00Z",
      "completedAt": "2026-08-26T10:35:45Z"
    }
  ],
  "pagination": {
    "nextCursor": null,
    "hasMore": false,
    "totalCount": 1,
    "limit": 25
  }
}
```

---

### 4.3 Yard Metrics Endpoints

#### GET /api/v1/yard/metrics

Get overall yard utilization and performance metrics.

**Authentication:** Bearer token required

**Query Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `period` | string | `24h` | Time period: `1h`, `6h`, `24h`, `7d`, `30d` |

**Success Response — 200 OK:**

```json
{
  "yardId": "yard-001",
  "period": "24h",
  "generatedAt": "2026-08-26T10:30:00Z",
  "totalSlots": 10000,
  "occupiedSlots": 7500,
  "availableSlots": 2500,
  "utilizationRate": 0.7500,
  "utilizationTrend": [
    { "timestamp": "2026-08-25T11:00:00Z", "rate": 0.7200 },
    { "timestamp": "2026-08-25T13:00:00Z", "rate": 0.7350 },
    { "timestamp": "2026-08-25T15:00:00Z", "rate": 0.7420 },
    { "timestamp": "2026-08-25T17:00:00Z", "rate": 0.7380 },
    { "timestamp": "2026-08-25T19:00:00Z", "rate": 0.7450 },
    { "timestamp": "2026-08-25T21:00:00Z", "rate": 0.7500 }
  ],
  "averageDwellTimeDays": 3.2,
  "dwellTimeDistribution": [
    { "range": "0-1 days", "count": 1200, "percentage": 0.1600 },
    { "range": "1-3 days", "count": 3500, "percentage": 0.4667 },
    { "range": "3-7 days", "count": 2100, "percentage": 0.2800 },
    { "range": "7-14 days", "count": 550, "percentage": 0.0733 },
    { "range": "14+ days", "count": 150, "percentage": 0.0200 }
  ],
  "reshufflingRate": 0.0800,
  "totalMoves24h": 342,
  "averageMovesPerHour": 14.25,
  "containersReceived24h": 187,
  "containersDispatched24h": 164,
  "dangerousGoodsCount": 142,
  "refrigeratedCount": 89,
  "emptyContainerCount": 1850
}
```

---

#### GET /api/v1/yard/metrics/blocks

Get per-block metrics for comparison.

**Success Response — 200 OK:**

```json
{
  "data": [
    {
      "blockId": "b1a2c3d4-e5f6-7890-abcd-ef1234567890",
      "blockCode": "B01",
      "blockType": "EXPORT",
      "totalSlots": 600,
      "occupiedSlots": 450,
      "utilizationRate": 0.7500,
      "averageDwellDays": 2.8,
      "reshufflingRate": 0.0650,
      "dangerousGoodsSlots": 0,
      "refrigeratedSlots": 0,
      "emptySlots": 150
    },
    {
      "blockId": "a0b1c2d3-e4f5-6789-abcd-ef1234567891",
      "blockCode": "B02",
      "blockType": "IMPORT",
      "totalSlots": 800,
      "occupiedSlots": 680,
      "utilizationRate": 0.8500,
      "averageDwellDays": 4.1,
      "reshufflingRate": 0.0920,
      "dangerousGoodsSlots": 45,
      "refrigeratedSlots": 30,
      "emptySlots": 120
    }
  ]
}
```

---

### 4.4 Equipment Endpoints

Yard crane/equipment registry (FR-008.1–4). Manual status/assignment updates are RBAC `ADMIN`, `YARD_MANAGER`, or `TERMINAL_OP`; read endpoints are any authenticated user.

#### GET /api/v1/yard/equipment

List all equipment (cranes) with current status.

**Authentication:** Bearer token required

**Query Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `type` | enum | — | Filter by `RTG` / `ASC` |
| `status` | enum | — | Filter by `AVAILABLE`, `ASSIGNED`, `OUT_OF_SERVICE` |

**Success Response — 200 OK:**

```json
{
  "data": [
    {
      "equipmentId": "550e8400-e29b-41d4-a716-446655440000",
      "code": "RTG-01",
      "equipmentType": "RTG",
      "assignedBlockId": "b1a2c3d4-e5f6-7890-abcd-ef1234567890",
      "status": "ASSIGNED",
      "movesTotal": 12,
      "createdAt": "2026-08-26T10:00:00Z",
      "updatedAt": "2026-08-26T10:30:00Z"
    }
  ]
}
```

**Equipment Types:** `RTG` (rubber-tyred gantry), `ASC` (automated stacking crane)
**Equipment Status:** `AVAILABLE`, `ASSIGNED`, `OUT_OF_SERVICE`. An `OUT_OF_SERVICE` equipment is omitted from availability and blocks assignment attempts (409).

---

#### GET /api/v1/yard/equipment/{equipmentId}

Get a single equipment record.

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `equipmentId` | UUID | Equipment ID |

**Success Response — 200 OK:** Returns the equipment object (same shape as above; `assignedBlockId` omitted when unassigned due to `non_null` serialization).

**Error Responses:**

| Status | Condition |
|--------|-----------|
| 404 | Equipment not found |

---

#### PUT /api/v1/yard/equipment/{equipmentId}

Update equipment status and/or block assignment.

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `equipmentId` | UUID | Equipment ID |

**Request Body:**

```json
{
  "status": "ASSIGNED",
  "assignedBlockId": "b1a2c3d4-e5f6-7890-abcd-ef1234567890"
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `status` | enum | no | `AVAILABLE`, `ASSIGNED`, `OUT_OF_SERVICE` |
| `assignedBlockId` | UUID | no | Block to assign (nullable) |

**Success Response — 200 OK:** Returns the updated equipment object.

**Error Responses:**

| Status | Condition |
|--------|-----------|
| 404 | Equipment or target block not found |
| 409 | Equipment is `OUT_OF_SERVICE` (no-op update when status unchanged) |

---

#### GET /api/v1/yard/equipment/{equipmentId}/moves

Moves-per-crane metric (FR-008.4).

**Success Response — 200 OK:**

```json
{
  "equipmentId": "550e8400-e29b-41d4-a716-446655440000",
  "code": "RTG-01",
  "movesTotal": 12,
  "lastUpdated": "2026-08-26T10:30:00Z"
}
```

---

## 5. Container Service

> **Service:** smartportx-container  
> **Gateway Route:** `/api/v1/containers/*`  
> **Database:** smartportx_container  
> **Responsibilities:** Container registry, lifecycle management, movement tracking, event history

### 5.1 Container CRUD Endpoints

#### POST /api/v1/containers

Register a new container in the system.

**Authentication:** Bearer token required  
**Authorization:** `ADMIN` or `TERMINAL_OP` roles

**Request Body:**

```json
{
  "containerNumber": "MSKU1234567",
  "containerType": "40HC",
  "owner": "Mediterranean Shipping Company",
  "consignee": "Acme Corporation",
  "weight": 28500.00,
  "tareWeight": 3900.00,
  "maxPayload": 26580.00,
  "sealNumber": "SL12345678",
  "dangerousGoods": null,
  "billOfLading": "MSKU202608001",
  "originPort": "CNSHA",
  "destinationPort": "SGSIN",
  " expectedArrival": "2026-08-28T06:00:00Z"
}
```

| Field | Type | Required | Validation |
|-------|------|----------|------------|
| `containerNumber` | string | yes | Pattern: `[A-Z]{4}[0-9]{7}`, unique |
| `containerType` | enum | yes | `20DC`, `40DC`, `40HC`, `45HC`, `20RF`, `40RF`, `20TK`, `40TK` |
| `owner` | string | yes | 1–100 characters |
| `consignee` | string | no | 1–100 characters |
| `weight` | decimal | yes | > 0, in kilograms |
| `tareWeight` | decimal | no | > 0, in kilograms |
| `maxPayload` | decimal | no | > 0, in kilograms |
| `sealNumber` | string | no | 1–20 characters |
| `dangerousGoods` | object \| null | no | Dangerous goods declaration (see below) |
| `billOfLading` | string | no | 1–50 characters |
| `originPort` | string | no | UN/LOCODE (5 chars) |
| `destinationPort` | string | no | UN/LOCODE (5 chars) |
| `expectedArrival` | datetime | no | ISO 8601 |

**Dangerous Goods Object:**

```json
{
  "unNumber": "UN1203",
  "properShippingName": "Gasoline",
  "hazardClass": "3",
  "packingGroup": "II",
  "flashPoint": -40,
  "emergencyContact": "+1-555-0123"
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `unNumber` | string | yes | UN number (e.g., `UN1203`) |
| `properShippingName` | string | yes | Official shipping name |
| `hazardClass` | string | yes | IMO hazard class (1–9) |
| `packingGroup` | string | no | `I`, `II`, or `III` |
| `flashPoint` | integer | no | Flash point in °C |
| `emergencyContact` | string | no | Emergency contact number |

**Success Response — 201 Created:**

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "containerNumber": "MSKU1234567",
  "containerType": "40HC",
  "owner": "Mediterranean Shipping Company",
  "consignee": "Acme Corporation",
  "weight": 28500.00,
  "tareWeight": 3900.00,
  "maxPayload": 26580.00,
  "sealNumber": "SL12345678",
  "dangerousGoods": null,
  "billOfLading": "MSKU202608001",
  "originPort": "CNSHA",
  "destinationPort": "SGSIN",
  "expectedArrival": "2026-08-28T06:00:00Z",
  "status": "REGISTERED",
  "currentLocation": null,
  "createdAt": "2026-08-26T10:30:00Z",
  "updatedAt": "2026-08-26T10:30:00Z"
}
```

**Error Responses:**

| Status | Condition |
|--------|-----------|
| 400 | Validation errors |
| 409 | Container number already registered |

---

#### GET /api/v1/containers

List containers with pagination, sorting, and filtering.

**Query Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `cursor` | string | — | Pagination cursor |
| `limit` | integer | 25 | Page size (1–100) |
| `sort` | string | `createdAt,desc` | Sort field and direction (`createdAt`, `asc`/`desc` only — unsupported → 400 `invalid_query`, D-437) |
| `filter[status]` | enum | — | Filter by status |
| `filter[type]` | enum | — | Filter by container type |
| `filter[owner]` | string | — | Filter by owner (partial match) |
| `filter[hasDangerousGoods]` | boolean | — | Filter dangerous goods containers |
| `filter[location]` | string | — | Filter by yard position prefix |
| `filter[createdAfter]` | datetime | — | Created after timestamp |
| `filter[createdBefore]` | datetime | — | Created before timestamp |

**Success Response — 200 OK:**

```json
{
  "data": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "containerNumber": "MSKU1234567",
      "containerType": "40HC",
      "owner": "Mediterranean Shipping Company",
      "status": "IN_YARD",
      "currentLocation": "B01-B03-R02-T03",
      "weight": 28500.00,
      "dangerousGoods": false,
      "createdAt": "2026-08-24T14:30:00Z"
    }
  ],
  "pagination": {
    "nextCursor": "eyJpZCI6MTAwfQ==",
    "hasMore": true,
    "totalCount": 1247,
    "limit": 25
  }
}
```

---

#### GET /api/v1/containers/{id}

Get full container details including current status and location.

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `id` | UUID | Container ID |

**Success Response — 200 OK:**

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "containerNumber": "MSKU1234567",
  "containerType": "40HC",
  "owner": "Mediterranean Shipping Company",
  "consignee": "Acme Corporation",
  "weight": 28500.00,
  "tareWeight": 3900.00,
  "maxPayload": 26580.00,
  "sealNumber": "SL12345678",
  "dangerousGoods": null,
  "billOfLading": "MSKU202608001",
  "originPort": "CNSHA",
  "destinationPort": "SGSIN",
  "expectedArrival": "2026-08-28T06:00:00Z",
  "status": "IN_YARD",
  "currentLocation": "B01-B03-R02-T03",
  "currentBlockCode": "B01",
  "customsStatus": "CLEARED",
  "lastMovement": {
    "movementId": "m1-2026-08-26",
    "type": "YARD_PLACEMENT",
    "from": "B01-B03-R01-T01",
    "to": "B01-B03-R02-T03",
    "timestamp": "2026-08-25T16:00:00Z",
    "operator": "jdoe"
  },
  "createdAt": "2026-08-24T14:30:00Z",
  "updatedAt": "2026-08-25T16:00:00Z"
}
```

---

#### PUT /api/v1/containers/{id}

Update container details. Partial updates supported.

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `id` | UUID | Container ID |

**Request Body (all fields optional):**

```json
{
  "owner": "Updated Owner Name",
  "consignee": "New Consignee",
  "sealNumber": "SL99999999",
  "expectedArrival": "2026-08-29T08:00:00Z"
}
```

**Success Response — 200 OK:** Returns updated container object.

---

#### POST /api/v1/containers/search

Advanced container search with complex criteria.

**Request Body:**

```json
{
  "containerNumber": "MSKU12*",
  "status": ["IN_YARD", "LOADED"],
  "type": ["40HC", "40DC"],
  "owner": "MSC",
  "location": {
    "block": "B01",
    "fromDate": "2026-08-01T00:00:00Z",
    "toDate": "2026-08-31T23:59:59Z"
  },
  "weight": {
    "min": 20000,
    "max": 30000
  },
  "dangerousGoods": false,
  "sort": "weight,desc",
  "limit": 50
}
```

| Field | Type | Description |
|-------|------|-------------|
| `containerNumber` | string | Supports `*` wildcard |
| `status` | string[] | OR logic — any of the listed statuses |
| `type` | string[] | OR logic — any of the listed types |
| `owner` | string | Partial match (case-insensitive) |
| `location` | object | Location-based filter |
| `weight` | object | Weight range filter |
| `dangerousGoods` | boolean | Filter by dangerous goods flag |
| `customsStatus` | string | NOT SUPPORTED — rejected with 400 `invalid_query` (D-437; container does not own customs data) |

**Success Response — 200 OK:** Returns paginated container list matching criteria.

---

### 5.2 Container Lifecycle Endpoints

#### PUT /api/v1/containers/{id}/status

Transition a container through its lifecycle states.

**Container Status State Machine** (DATA-ARCHITECTURE §7.1 enums; all 15 transitions enforced by `ContainerStateMachine`, D-434; a transition to the current status → 422 `invalid_status_transition`):

```
REGISTERED → AT_GATE → IN_YARD → LOADED → DISCHARGED → AT_GATE_OUT → DEPARTED
   │           │          │                          │
   └───┬───────┴──────────┴──────────────────────────┘
       ▼
      HELD ──→ AT_GATE | IN_YARD | DISCHARGED | AT_GATE_OUT      (DEPARTED is terminal)
```

| Status | Description |
|--------|-------------|
| `REGISTERED` | Container entered the system but not yet at port |
| `AT_GATE` | Container at gate check-in |
| `IN_YARD` | Container stored in yard |
| `LOADED` | Container loaded onto vessel |
| `DISCHARGED` | Container discharged from vessel |
| `AT_GATE_OUT` | Container at gate for exit |
| `DEPARTED` | Container has left the port |
| `HELD` | Container on customs hold |

**Request Body:**

```json
{
  "status": "AT_GATE",
  "reason": "Truck driver presented container at Gate 3",
  "location": "GATE-03",
  "operator": "jdoe"
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `status` | enum | yes | Target status (must differ from current; same-status → 422) |
| `reason` | string | no | Human-readable reason |
| `location` | string | no | Optional; if non-blank, updates `currentLocation` (D-437) |
| `operator` | string | no | Ignored — performedBy/changedBy taken from JWT subject |

**Success Response — 200 OK:**

```json
{
  "containerId": "550e8400-e29b-41d4-a716-446655440000",
  "previousStatus": "REGISTERED",
  "newStatus": "AT_GATE",
  "transitionTime": "2026-08-26T10:30:00Z",
  "location": "GATE-03"
}
```

**Error Responses:**

| Status | Condition |
|--------|-----------|
| 404 | Container not found |
| 422 | Invalid status transition (e.g., `LOADED` → `REGISTERED`) |
| 422 | Transition to current status (must differ) |

---

### 5.3 Container History Endpoints

#### GET /api/v1/containers/{id}/movements

Get movement history for a container.

**Query Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `cursor` | string | — | Pagination cursor |
| `limit` | integer | 50 | Page size |
| `filter[from]` | datetime | — | Movement after timestamp |
| `filter[to]` | datetime | — | Movement before timestamp |
| `filter[type]` | string | — | Movement type filter |

**Success Response — 200 OK:**

```json
{
  "data": [
    {
      "movementId": "mv-001",
      "containerId": "550e8400-e29b-41d4-a716-446655440000",
      "containerNumber": "MSKU1234567",
      "movementType": "GATE_IN",
      "from": null,
      "to": "GATE-03",
      "timestamp": "2026-08-24T14:30:00Z",
      "operator": "gate-op-01",
      "reason": null,
      "createdAt": "2026-08-24T14:30:00Z"
    },
    {
      "movementId": "mv-002",
      "containerId": "550e8400-e29b-41d4-a716-446655440000",
      "containerNumber": "MSKU1234567",
      "movementType": "YARD_MOVE",
      "from": "GATE-03",
      "to": "B01-B03-R02-T03",
      "timestamp": "2026-08-24T15:00:00Z",
      "operator": "yard-crane-01",
      "reason": "Stack optimization",
      "createdAt": "2026-08-24T15:00:00Z"
    },
    {
      "movementId": "mv-003",
      "containerId": "550e8400-e29b-41d4-a716-446655440000",
      "containerNumber": "MSKU1234567",
      "movementType": "VESSEL_DISCHARGE",
      "from": "CY-01",
      "to": "B01-B03-R01-T04",
      "timestamp": "2026-08-25T09:15:00Z",
      "operator": "yard-crane-02",
      "reason": null,
      "createdAt": "2026-08-25T09:15:00Z"
    }
  ],
  "pagination": {
    "nextCursor": null,
    "hasMore": false,
    "totalCount": 3,
    "limit": 50
  }
}
```

**Movement Types:**

| Type | Description |
|------|-------------|
| `GATE_IN` | Container entered terminal via gate |
| `GATE_OUT` | Container exited terminal via gate |
| `YARD_MOVE` | Container repositioned within yard |
| `VESSEL_LOAD` | Container loaded onto vessel |
| `VESSEL_DISCHARGE` | Container discharged from vessel |

---

#### GET /api/v1/containers/{id}/events

Get all events (including status changes, IoT, customs) for a container.

**Query Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `cursor` | string | — | Pagination cursor |
| `limit` | integer | 50 | Page size |
| `filter[eventType]` | string | — | Filter by event type |

**Success Response — 200 OK:**

```json
{
  "data": [
    {
      "eventId": "evt-001",
      "eventType": "REGISTERED",
      "containerId": "550e8400-e29b-41d4-a716-446655440000",
      "containerNumber": "MSKU1234567",
      "timestamp": "2026-08-23T09:00:00Z",
      "data": {
        "containerId": "550e8400-e29b-41d4-a716-446655440000",
        "containerNumber": "MSKU1234567",
        "containerType": "40HC",
        "status": "REGISTERED",
        "createdAt": "2026-08-23T09:00:00Z"
      },
      "source": null
    },
    {
      "eventId": "evt-002",
      "eventType": "STATUS_CHANGE",
      "containerId": "550e8400-e29b-41d4-a716-446655440000",
      "containerNumber": "MSKU1234567",
      "timestamp": "2026-08-24T14:30:00Z",
      "data": {
        "containerId": "550e8400-e29b-41d4-a716-446655440000",
        "containerNumber": "MSKU1234567",
        "previousStatus": "REGISTERED",
        "newStatus": "AT_GATE",
        "location": "GATE-03",
        "changedAt": "2026-08-24T14:30:00Z"
      },
      "source": null
    },
    {
      "eventId": "evt-003",
      "eventType": "MOVEMENT",
      "containerId": "550e8400-e29b-41d4-a716-446655440000",
      "containerNumber": "MSKU1234567",
      "timestamp": "2026-08-24T14:31:00Z",
      "data": {
        "movementId": "550e8400-e29b-41d4-a716-446655440001",
        "containerId": "550e8400-e29b-41d4-a716-446655440000",
        "containerNumber": "MSKU1234567",
        "movementType": "GATE_IN",
        "to": "GATE-03",
        "timestamp": "2026-08-24T14:31:00Z",
        "performedBy": "gate-op-01"
      },
      "source": null
    }
  ],
  "pagination": {
    "nextCursor": null,
    "hasMore": false,
    "totalCount": 3,
    "limit": 50
  }
}
```

**Event Types:**

> `smartportx-container` emits only the four tokens below (D-436). Note: `source` is not persisted in the
> container event log (D-440) — consumers needing origin attribution should read the envelope `source` on the
> RabbitMQ message instead.

| Type | Description |
|------|-------------|
| `REGISTERED` | Container registered in the system |
| `STATUS_CHANGE` | Container lifecycle state transition |
| `MOVEMENT` | Physical movement recorded |
| `REMOVED` | Container removed (soft delete) |

---

## 6. Gate Service

> **Service:** smartportx-gate  
> **Gateway Route:** `/api/v1/appointments/*`, `/api/v1/gate/*`  
> **Database:** smartportx_gate  
> **Responsibilities:** Appointment scheduling, gate-in/out processing, queue management, dynamic quotas

### 6.1 Appointment Endpoints

#### POST /api/v1/appointments

Book a new truck appointment.

**Authentication:** Bearer token required  
**Authorization:** `ADMIN`, `TERMINAL_OP`, or `GATE_OPERATOR` roles

**Request Body:**

```json
{
  "truckingCompany": "FastHaul Logistics",
  "truckLicensePlate": "B123ABC",
  "driverName": "John Driver",
  "driverLicense": "DL-9876543210",
  "appointmentType": "PICKUP",
  "containerNumber": "MSKU1234567",
  "preferredTimeSlot": "2026-08-27T10:00:00Z",
  "durationMinutes": 30,
  "specialRequirements": {
    "hazmatCertified": false,
    "reeferConnection": false,
    "doubleStack": false
  }
}
```

| Field | Type | Required | Validation |
|-------|------|----------|------------|
| `truckingCompany` | string | yes | 1–200 characters |
| `truckLicensePlate` | string | yes | 2–15 characters |
| `driverName` | string | yes | 2–100 characters |
| `driverLicense` | string | yes | 5–30 characters |
| `appointmentType` | enum | yes | `PICKUP`, `DROP_OFF`, `BOTH` |
| `containerNumber` | string | conditional | Required for `PICKUP` and `BOTH` types |
| `preferredTimeSlot` | datetime | yes | Must be future, within 14 days |
| `durationMinutes` | integer | no | 15–120 (default: 30) |
| `specialRequirements` | object | no | Additional requirements |

**Success Response — 201 Created:**

```json
{
  "id": "apt-e4d5f6a7-b8c9-0123-def0-234567890123",
  "confirmationNumber": "APT-20260827-0001",
  "status": "CONFIRMED",
  "truckingCompany": "FastHaul Logistics",
  "truckLicensePlate": "B123ABC",
  "driverName": "John Driver",
  "driverLicense": "DL-9876543210",
  "appointmentType": "PICKUP",
  "containerNumber": "MSKU1234567",
  "preferredTimeSlot": "2026-08-27T10:00:00Z",
  "confirmedTimeSlot": "2026-08-27T10:00:00Z",
  "durationMinutes": 30,
  "timeWindowStart": "2026-08-27T09:45:00Z",
  "timeWindowEnd": "2026-08-27T10:15:00Z",
  "gateLane": "GATE-01",
  "createdAt": "2026-08-26T10:30:00Z"
}
```

**Error Responses:**

| Status | Condition |
|--------|-----------|
| 400 | Validation errors |
| 404 | Container not found (for PICKUP type) |
| 409 | Time slot unavailable |
| 422 | Container not available for pickup; appointment outside operating hours |

---

#### GET /api/v1/appointments

List appointments with filtering and pagination.

**Query Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `cursor` | string | — | Pagination cursor |
| `limit` | integer | 25 | Page size |
| `sort` | string | `preferredTimeSlot,asc` | Sort field and direction |
| `filter[date]` | date | — | Filter by appointment date (YYYY-MM-DD) |
| `filter[dateRange][from]` | date | — | Start of date range |
| `filter[dateRange][to]` | date | — | End of date range |
| `filter[company]` | string | — | Filter by trucking company (partial match) |
| `filter[status]` | enum | — | `CONFIRMED`, `PENDING`, `CHECKED_IN`, `COMPLETED`, `CANCELLED`, `NO_SHOW` |
| `filter[type]` | enum | — | `PICKUP`, `DROP_OFF`, `BOTH` |

**Success Response — 200 OK:**

```json
{
  "data": [
    {
      "id": "apt-e4d5f6a7-b8c9-0123-def0-234567890123",
      "confirmationNumber": "APT-20260827-0001",
      "truckingCompany": "FastHaul Logistics",
      "truckLicensePlate": "B123ABC",
      "driverName": "John Driver",
      "appointmentType": "PICKUP",
      "containerNumber": "MSKU1234567",
      "confirmedTimeSlot": "2026-08-27T10:00:00Z",
      "durationMinutes": 30,
      "status": "CONFIRMED",
      "gateLane": "GATE-01"
    }
  ],
  "pagination": {
    "nextCursor": null,
    "hasMore": false,
    "totalCount": 1,
    "limit": 25
  }
}
```

---

#### GET /api/v1/appointments/{id}

Get full details of a specific appointment.

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `id` | UUID | Appointment ID |

**Success Response — 200 OK:** Returns full appointment object (same as POST response).

---

#### PUT /api/v1/appointments/{id}

Update an existing appointment. Only `CONFIRMED` or `PENDING` appointments can be modified.

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `id` | UUID | Appointment ID |

**Request Body (all fields optional):**

```json
{
  "preferredTimeSlot": "2026-08-27T14:00:00Z",
  "truckLicensePlate": "B456DEF",
  "driverName": "Jane Driver",
  "specialRequirements": {
    "reeferConnection": true
  }
}
```

**Success Response — 200 OK:** Returns updated appointment object.

**Error Responses:**

| Status | Condition |
|--------|-----------|
| 404 | Appointment not found |
| 409 | Cannot modify — appointment is already checked in |
| 422 | New time slot unavailable |

---

#### DELETE /api/v1/appointments/{id}

Cancel an appointment.

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `id` | UUID | Appointment ID |

**Query Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `reason` | string | no | Cancellation reason |

**Success Response — 204 No Content**

**Error Responses:**

| Status | Condition |
|--------|-----------|
| 404 | Appointment not found |
| 422 | Cannot cancel — appointment is already checked in or completed |

---

### 6.2 Gate Processing Endpoints

#### POST /api/v1/gate/check-in

Process a truck arriving at the gate. Validates appointment, documents, and assigns yard location.

**Authentication:** Bearer token required  
**Authorization:** `ADMIN` or `GATE_OPERATOR` roles

**Request Body:**

```json
{
  "appointmentId": "apt-e4d5f6a7-b8c9-0123-def0-234567890123",
  "truckLicensePlate": "B123ABC",
  "driverLicense": "DL-9876543210",
  "containerNumber": "MSKU1234567",
  "sealNumber": "SL12345678",
  "documents": [
    "DELIVERY_ORDER",
    "BILL_OF_LADING"
  ],
  "truckWeight": 18500,
  "notes": "Driver reports slight damage to left side"
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `appointmentId` | UUID | yes | Associated appointment |
| `truckLicensePlate` | string | yes | Must match appointment |
| `driverLicense` | string | yes | Must match appointment |
| `containerNumber` | string | yes | Must match appointment |
| `sealNumber` | string | conditional | Required for PICKUP |
| `documents` | string[] | yes | List of document types presented |
| `truckWeight` | integer | no | Gross vehicle weight in kg |
| `notes` | string | no | Operator notes (max 500 chars) |

**Document Types:**

| Document | Description |
|----------|-------------|
| `DELIVERY_ORDER` | Delivery order from shipping line |
| `BILL_OF_LADING` | Original or telex B/L |
| `CUSTOMS_RELEASE` | Customs clearance document |
| `DANGEROUS_GOODS_CERT` | Dangerous goods certificate |
| `REEFER_CHECK` | Reefer pre-trip inspection |

**Success Response — 200 OK:**

```json
{
  "gateTransactionId": "txn-f5e6a7b8-c9d0-1234-ef01-345678901234",
  "appointmentId": "apt-e4d5f6a7-b8c9-0123-def0-234567890123",
  "status": "CHECKED_IN",
  "gateLane": "GATE-01",
  "checkInTime": "2026-08-27T09:52:00Z",
  "yardLocation": "B01-B03-R02-T03",
  "instructions": "Proceed to Block 1, Bay 3. Follow signs to Export area.",
  "estimatedServiceTime": "2026-08-27T10:05:00Z",
  "documentsVerified": true,
  "documents": [
    {
      "type": "DELIVERY_ORDER",
      "status": "VERIFIED",
      "verifiedBy": "gate-op-01"
    },
    {
      "type": "BILL_OF_LADING",
      "status": "VERIFIED",
      "verifiedBy": "gate-op-01"
    }
  ]
}
```

**Error Responses:**

| Status | Condition |
|--------|-----------|
| 404 | Appointment not found |
| 409 | Appointment not in check-in window; truck already checked in |
| 422 | License plate mismatch; missing required documents; container not available |

---

#### POST /api/v1/gate/check-out

Process a truck departing the gate after loading/unloading.

**Authentication:** Bearer token required  
**Authorization:** `ADMIN` or `GATE_OPERATOR` roles

**Request Body:**

```json
{
  "gateTransactionId": "txn-f5e6a7b8-c9d0-1234-ef01-345678901234",
  "containerNumber": "MSKU1234567",
  "sealIntact": true,
  "newSealNumber": null,
  "truckWeight": 42500,
  "notes": null
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `gateTransactionId` | UUID | yes | Gate transaction from check-in |
| `containerNumber` | string | yes | Container being transported |
| `sealIntact` | boolean | yes | Whether the seal is intact |
| `newSealNumber` | string | no | New seal number (if resealed) |
| `truckWeight` | integer | no | Gross vehicle weight in kg |
| `notes` | string | no | Operator notes |

**Success Response — 200 OK:**

```json
{
  "gateTransactionId": "txn-f5e6a7b8-c9d0-1234-ef01-345678901234",
  "status": "CHECKED_OUT",
  "eirNumber": "EIR-20260827-0001",
  "completionTime": "2026-08-27T10:12:00Z",
  "containerNumber": "MSKU1234567",
  "sealIntact": true,
  "netWeight": 24000,
  "documentsIssued": [
    "EQUIPMENT_INTERCHANGE_RECEIPT"
  ],
  "gateLane": "GATE-01",
  "totalProcessingTimeMinutes": 20
}
```

**Error Responses:**

| Status | Condition |
|--------|-----------|
| 404 | Transaction not found |
| 409 | Transaction not in CHECKED_IN state |
| 422 | Seal broken without authorization; weight discrepancy exceeds threshold |

---

### 6.3 Queue & Quota Endpoints

#### GET /api/v1/gate/queue

Get current gate queue status and wait time estimates.

**Success Response — 200 OK:**

```json
{
  "timestamp": "2026-08-26T10:30:00Z",
  "waitingTrucks": 12,
  "inProcessTrucks": 3,
  "averageWaitTimeMinutes": 15,
  "estimatedClearanceTime": "2026-08-26T11:15:00Z",
  "byType": {
    "PICKUP": 5,
    "DROP_OFF": 7
  },
  "byGateLane": [
    {
      "gateLane": "GATE-01",
      "status": "ACTIVE",
      "currentQueue": 4,
      "averageProcessingMinutes": 8,
      "operator": "gate-op-01"
    },
    {
      "gateLane": "GATE-02",
      "status": "ACTIVE",
      "currentQueue": 5,
      "averageProcessingMinutes": 10,
      "operator": "gate-op-02"
    },
    {
      "gateLane": "GATE-03",
      "status": "MAINTENANCE",
      "currentQueue": 0,
      "averageProcessingMinutes": 0,
      "operator": null
    }
  ],
  "upcomingAppointments": [
    {
      "confirmationNumber": "APT-20260826-0015",
      "timeSlot": "2026-08-26T11:00:00Z",
      "type": "PICKUP",
      "company": "FastHaul Logistics"
    }
  ]
}
```

---

#### GET /api/v1/gate/quotas

Get appointment quotas for available time windows.

**Query Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `date` | date | today | Date to query |
| `gateLane` | string | — | Filter by gate lane |

**Success Response — 200 OK:**

```json
{
  "date": "2026-08-27",
  "quotas": [
    {
      "id": "quota-001",
      "timeSlot": "2026-08-27T06:00:00Z",
      "durationMinutes": 60,
      "gateLane": "ALL",
      "maxAppointments": 20,
      "currentBookings": 14,
      "availableSlots": 6,
      "appointmentTypes": ["PICKUP", "DROP_OFF"]
    },
    {
      "id": "quota-002",
      "timeSlot": "2026-08-27T07:00:00Z",
      "durationMinutes": 60,
      "gateLane": "ALL",
      "maxAppointments": 20,
      "currentBookings": 18,
      "availableSlots": 2,
      "appointmentTypes": ["PICKUP", "DROP_OFF"]
    }
  ]
}
```

---

#### PUT /api/v1/gate/quotas/{id}

Update a quota (dynamic adjustment based on conditions).

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `id` | string | Quota ID |

**Request Body:**

```json
{
  "maxAppointments": 25,
  "reason": "Increased capacity due to vessel departure scheduled"
}
```

**Success Response — 200 OK:** Returns updated quota object.

---

## 7. Vessel Service

> **Service:** smartportx-vessel  
> **Gateway Route:** `/api/v1/vessels/*`  
> **Database:** smartportx_vessel  
> **Responsibilities:** Vessel registry, scheduling, discharge planning, progress tracking

### 7.1 Vessel Management Endpoints

#### POST /api/v1/vessels

Register a new vessel in the system.

**Authentication:** Bearer token required  
**Authorization:** `ADMIN` or `TERMINAL_OP` roles

**Request Body:**

```json
{
  "imoNumber": "IMO9876543",
  "vesselName": "MSC Diana",
  "vesselType": "CONTAINER",
  "length": 400.0,
  "beam": 58.8,
  "draft": 16.0,
  "teuCapacity": 24000,
  "grossTonnage": 228400,
  "flag": "PA",
  "operator": "Mediterranean Shipping Company",
  "callSign": "H3RC"
}
```

| Field | Type | Required | Validation |
|-------|------|----------|------------|
| `imoNumber` | string | yes | Pattern: `IMO[0-9]{7}`, unique |
| `vesselName` | string | yes | 2–100 characters |
| `vesselType` | enum | yes | `CONTAINER`, `BULK`, `TANKER`, `GENERAL`, `ROLL_ON_ROLL_OFF` |
| `length` | decimal | yes | > 0, in meters |
| `beam` | decimal | yes | > 0, in meters |
| `draft` | decimal | yes | > 0, in meters |
| `teuCapacity` | integer | yes | > 0 (for container vessels) |
| `grossTonnage` | decimal | no | > 0, in GT |
| `flag` | string | no | ISO 3166-1 alpha-2 country code |
| `operator` | string | no | 1–200 characters |
| `callSign` | string | no | 3–7 alphanumeric characters |

**Success Response — 201 Created:**

```json
{
  "id": "vsl-g5h6i7j8-k9l0-1234-mno1-234567890123",
  "imoNumber": "IMO9876543",
  "vesselName": "MSC Diana",
  "vesselType": "CONTAINER",
  "length": 400.0,
  "beam": 58.8,
  "draft": 16.0,
  "teuCapacity": 24000,
  "grossTonnage": 228400,
  "flag": "PA",
  "operator": "Mediterranean Shipping Company",
  "callSign": "H3RC",
  "createdAt": "2026-08-26T10:30:00Z"
}
```

---

#### GET /api/v1/vessels

List registered vessels.

**Query Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `cursor` | string | — | Pagination cursor |
| `limit` | integer | 25 | Page size |
| `filter[operator]` | string | — | Filter by operator |
| `filter[type]` | enum | — | Filter by vessel type |
| `filter[inPort]` | boolean | — | Filter vessels currently in port |

**Success Response — 200 OK:** Paginated list of vessel objects.

---

#### GET /api/v1/vessels/{id}

Get full vessel details including current status and schedule.

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `id` | UUID | Vessel ID |

**Success Response — 200 OK:**

```json
{
  "id": "vsl-g5h6i7j8-k9l0-1234-mno1-234567890123",
  "imoNumber": "IMO9876543",
  "vesselName": "MSC Diana",
  "vesselType": "CONTAINER",
  "length": 400.0,
  "beam": 58.8,
  "draft": 16.0,
  "teuCapacity": 24000,
  "grossTonnage": 228400,
  "flag": "PA",
  "operator": "Mediterranean Shipping Company",
  "callSign": "H3RC",
  "currentStatus": "IN_PORT",
  "currentBerth": 3,
  "nextArrival": "2026-08-28T06:00:00Z",
  "nextDeparture": "2026-08-29T18:00:00Z",
  "createdAt": "2026-08-26T10:30:00Z"
}
```

---

### 7.2 Schedule Endpoints

#### POST /api/v1/vessels/{id}/schedule

Add a schedule entry for a vessel.

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `id` | UUID | Vessel ID |

**Request Body:**

```json
{
  "eta": "2026-08-28T06:00:00Z",
  "etd": "2026-08-29T18:00:00Z",
  "berthNumber": 3,
  "priority": "HIGH",
  "voyageNumber": "MSC-2026-EA42",
  "portOfCall": "SGSIN",
  "nextPort": "NLRTM",
  "estimatedTEUs": 12500,
  "actualTEUs": null,
  "notes": "Priority discharge — 400 reefer containers"
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `eta` | datetime | yes | Estimated time of arrival |
| `etd` | datetime | yes | Estimated time of departure (must be after ETA) |
| `berthNumber` | integer | yes | Assigned berth (1–8) |
| `priority` | enum | yes | `HIGH`, `MEDIUM`, `LOW` |
| `voyageNumber` | string | no | Carrier voyage number |
| `portOfCall` | string | no | UN/LOCODE of previous port |
| `nextPort` | string | no | UN/LOCODE of next port |
| `estimatedTEUs` | integer | no | Estimated TEU count |
| `actualTEUs` | integer | no | Actual TEU count (filled after discharge) |

**Success Response — 201 Created:** Returns created schedule entry.

---

#### GET /api/v1/vessels/schedule

Get vessel schedule filtered by date range.

**Query Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `from` | datetime | now | Start of date range |
| `to` | datetime | +7 days | End of date range |
| `berth` | integer | — | Filter by berth |
| `priority` | enum | — | Filter by priority |

**Success Response — 200 OK:**

```json
{
  "data": [
    {
      "scheduleId": "sch-i8j9k0l1-m2n3-4567-opq2-345678901234",
      "vesselId": "vsl-g5h6i7j8-k9l0-1234-mno1-234567890123",
      "vesselName": "MSC Diana",
      "imoNumber": "IMO9876543",
      "eta": "2026-08-28T06:00:00Z",
      "etd": "2026-08-29T18:00:00Z",
      "berthNumber": 3,
      "priority": "HIGH",
      "voyageNumber": "MSC-2026-EA42",
      "estimatedTEUs": 12500,
      "status": "SCHEDULED",
      "dischargePlan": false,
      "arrivalTime": null,
      "departureTime": null
    }
  ],
  "pagination": null
}
```

---

### 7.3 Discharge Operation Endpoints

#### POST /api/v1/vessels/{id}/discharge-plan

Create a discharge plan mapping containers from vessel positions to yard targets.

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `id` | UUID | Vessel ID |

**Request Body:**

```json
{
  "voyageNumber": "MSC-2026-EA42",
  "containers": [
    {
      "containerNumber": "MSKU1234567",
      "containerType": "40HC",
      "weight": 28500,
      "vesselPosition": "020206",
      "yardTarget": "B01-B03-R02-T03",
      "priority": "HIGH",
      "dangerousGoods": false,
      "reefer": false
    },
    {
      "containerNumber": "TCLU9876543",
      "containerType": "20DC",
      "weight": 18000,
      "vesselPosition": "020208",
      "yardTarget": "B02-B05-R01-T01",
      "priority": "MEDIUM",
      "dangerousGoods": false,
      "reefer": false
    }
  ]
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `voyageNumber` | string | yes | Associated voyage |
| `containers` | array | yes | List of containers to discharge |
| `containerNumber` | string | yes | ISO 6346 container number |
| `containerType` | enum | yes | Container type |
| `weight` | decimal | yes | Gross weight in kg |
| `vesselPosition` | string | yes | Stowage position on vessel (bay-row-tier) |
| `yardTarget` | string | yes | Target yard position |
| `priority` | enum | yes | `HIGH`, `MEDIUM`, `LOW` |
| `dangerousGoods` | boolean | no | Dangerous goods flag |
| `reefer` | boolean | no | Reefer container flag |

**Success Response — 201 Created:**

```json
{
  "dischargePlanId": "dp-j9k0l1m2-n3o4-5677-rst3-456789012345",
  "vesselId": "vsl-g5h6i7j8-k9l0-1234-mno1-234567890123",
  "voyageNumber": "MSC-2026-EA42",
  "totalContainers": 2,
  "totalTEUs": 3,
  "estimatedDurationMinutes": 45,
  "status": "PLANNED",
  "createdAt": "2026-08-26T10:30:00Z"
}
```

---

#### GET /api/v1/vessels/{id}/discharge-plan

Get the current discharge plan for a vessel.

**Query Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `voyageNumber` | string | — | Filter by voyage |

**Success Response — 200 OK:**

```json
{
  "dischargePlanId": "dp-j9k0l1m2-n3o4-5677-rst3-456789012345",
  "vesselId": "vsl-g5h6i7j8-k9l0-1234-mno1-234567890123",
  "voyageNumber": "MSC-2026-EA42",
  "totalContainers": 2,
  "totalTEUs": 3,
  "status": "IN_PROGRESS",
  "progress": {
    "discharged": 1,
    "inTransit": 0,
    "stacked": 1,
    "pending": 0,
    "percentageComplete": 1.0
  },
  "containers": [
    {
      "containerNumber": "MSKU1234567",
      "vesselPosition": "020206",
      "yardTarget": "B01-B03-R02-T03",
      "status": "STACKED",
      "actualYardPosition": "B01-B03-R02-T03",
      "priority": "HIGH"
    },
    {
      "containerNumber": "TCLU9876543",
      "vesselPosition": "020208",
      "yardTarget": "B02-B05-R01-T01",
      "status": "DISCHARGED",
      "actualYardPosition": null,
      "priority": "MEDIUM"
    }
  ],
  "createdAt": "2026-08-26T10:30:00Z",
  "updatedAt": "2026-08-26T10:45:00Z"
}
```

---

#### PUT /api/v1/vessels/{id}/discharge-progress

Update the discharge progress for a specific container.

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `id` | UUID | Vessel ID |

**Request Body:**

```json
{
  "containerNumber": "TCLU9876543",
  "status": "STACKED",
  "actualYardPosition": "B02-B05-R01-T01",
  "dischargedAt": "2026-08-28T08:30:00Z",
  "stackedAt": "2026-08-28T08:45:00Z",
  "equipmentId": "YC-002",
  "operator": "yard-crane-02",
  "notes": null
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `containerNumber` | string | yes | Container to update |
| `status` | enum | yes | `DISCHARGED`, `IN_TRANSIT`, `STACKED` |
| `actualYardPosition` | string | conditional | Required when status is `STACKED` |
| `dischargedAt` | datetime | no | Actual discharge timestamp |
| `stackedAt` | datetime | no | Actual stacking timestamp |
| `equipmentId` | string | no | Crane/equipment identifier |
| `operator` | string | no | Operator username |
| `notes` | string | no | Notes (max 500 chars) |

**Success Response — 200 OK:** Returns updated container discharge record.

---

#### GET /api/v1/vessels/{id}/discharge-progress

Get discharge progress summary for the vessel.

**Query Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `voyageNumber` | string | — | Filter by voyage |

**Success Response — 200 OK:**

```json
{
  "vesselId": "vsl-g5h6i7j8-k9l0-1234-mno1-234567890123",
  "vesselName": "MSC Diana",
  "voyageNumber": "MSC-2026-EA42",
  "totalContainers": 2,
  "totalTEUs": 3,
  "progress": {
    "discharged": 1,
    "inTransit": 0,
    "stacked": 1,
    "pending": 0,
    "percentageComplete": 1.0
  },
  "timeline": {
    "planCreated": "2026-08-26T10:30:00Z",
    "dischargeStarted": "2026-08-28T07:00:00Z",
    "dischargeCompleted": "2026-08-28T08:45:00Z",
    "estimatedCompletion": "2026-08-28T08:30:00Z"
  },
  "performance": {
    "movesPerHour": 25,
    "averageMoveTimeMinutes": 2.4,
    "onSchedule": true,
    "deviationMinutes": -15
  },
  "lastUpdated": "2026-08-26T10:45:00Z"
}
```

---

## 8. Integration Service

> **Service:** smartportx-integration  
> **Gateway Route:** `/api/v1/integration/*`  
> **Database:** smartportx_integration  
> **Responsibilities:** EDIFACT message parsing, IoT sensor data ingestion, customs status management

### 8.1 EDIFACT Endpoints

#### POST /api/v1/integration/edifact/parse

Parse an incoming EDIFACT message and extract structured data.

**Authentication:** Bearer token required  
**Authorization:** `ADMIN` or `TERMINAL_OP` roles

**Request Body:**

```json
{
  "messageType": "COPRAR",
  "rawMessage": "UNH+1+IFTMIN:D:95B:UN'BGM+45+PO123456+9'DTM+137:20260826:102'NAD+CZ+SH1234+++ACME CORP+++SGSIN+++65123456'LOC+9+SGSIN'QTY+52:24000:UN'FTX+AAA+++ELECTRONIC COMPONENTS'UNT+10+1'"
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `messageType` | enum | yes | `COPRAR`, `COARRI`, `CODECO`, `COPARN` |
| `rawMessage` | string | yes | Raw EDIFACT message string |

**EDIFACT Message Types:**

| Type | Description |
|------|-------------|
| `COPRAR` | Container discharge/load report (pre-arrival) |
| `COARRI` | Container discharge/load confirmation |
| `CODECO` | Container gate-in/gate-out report |
| `COPARN` | Container announcement (planned movement) |

**Success Response — 200 OK:**

```json
{
  "correlationId": "corr-p3q4r5s6-t7u8-9012-vwx4-567890123456",
  "messageType": "COPRAR",
  "parsedData": {
    "messageReference": "PO123456",
    "messageDate": "2026-08-26",
    "carrier": {
      "name": "ACME CORP",
      "code": "SH123456",
      "country": "SG"
    },
    "portOfLoading": "SGSIN",
    "containers": [
      {
        "equipmentNumber": "MSKU1234567",
        "equipmentSize": "40",
        "equipmentType": "HC",
        "totalGrossWeight": 24000,
        "commodityDescription": "ELECTRONIC COMPONENTS",
        "sealNumbers": [],
        "dangerousGoods": false
      }
    ]
  },
  "validationErrors": [],
  "parsedAt": "2026-08-26T10:30:00Z"
}
```

**Error Responses:**

| Status | Condition |
|--------|-----------|
| 400 | Malformed EDIFACT message |
| 422 | Unsupported message type; parsing errors in message structure |

---

#### GET /api/v1/integration/edifact/messages

List parsed EDIFACT messages with filtering.

**Query Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `cursor` | string | — | Pagination cursor |
| `limit` | integer | 25 | Page size |
| `filter[type]` | enum | — | Filter by message type |
| `filter[dateRange][from]` | datetime | — | Start of date range |
| `filter[dateRange][to]` | datetime | — | End of date range |
| `filter[hasErrors]` | boolean | — | Filter messages with parsing errors |

**Success Response — 200 OK:**

```json
{
  "data": [
    {
      "correlationId": "corr-p3q4r5s6-t7u8-9012-vwx4-567890123456",
      "messageType": "COPRAR",
      "status": "PROCESSED",
      "containerCount": 1,
      "hasErrors": false,
      "parsedAt": "2026-08-26T10:30:00Z",
      "processedAt": "2026-08-26T10:30:05Z"
    }
  ],
  "pagination": {
    "nextCursor": null,
    "hasMore": false,
    "totalCount": 1,
    "limit": 25
  }
}
```

---

### 8.2 IoT Endpoints

#### POST /api/v1/integration/iot/data

Ingest IoT sensor data from containers (simulated or real).

**Authentication:** Bearer token required (or API key for IoT gateways)

**Request Body:**

```json
{
  "containerNumber": "MSKU1234567",
  "sensorType": "TEMPERATURE",
  "value": "4.2",
  "unit": "°C",
  "timestamp": "2026-08-26T10:00:00Z",
  "batteryLevel": 72,
  "signalStrength": -65,
  "deviceId": "sensor-MSKU1234567-temp-01",
  "location": {
    "latitude": 1.2644,
    "longitude": 103.8222
  }
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `containerNumber` | string | yes | Container identifier |
| `sensorType` | enum | yes | `GPS`, `TEMPERATURE`, `HUMIDITY`, `DOOR_SENSOR` |
| `value` | string | yes | Sensor reading (string to support various types) |
| `unit` | string | no | Unit of measurement |
| `timestamp` | datetime | yes | When the reading was taken |
| `batteryLevel` | integer | no | Battery percentage (0–100) |
| `signalStrength` | integer | no | Signal strength in dBm |
| `deviceId` | string | no | Unique sensor device identifier |
| `location` | object | no | GPS coordinates (for GPS sensors) |

**Sensor Types:**

| Type | Value Format | Unit | Alert Thresholds |
|------|-------------|------|-----------------|
| `GPS` | `lat,lng` | decimal degrees | Geofence violation |
| `TEMPERATURE` | decimal | °C | < -5 or > 40 (configurable) |
| `HUMIDITY` | decimal | % | > 95% |
| `DOOR_SENSOR` | `OPEN` or `CLOSED` | — | `OPEN` when not authorized |

**Success Response — 202 Accepted:**

```json
{
  "status": "accepted",
  "containerNumber": "MSKU1234567",
  "sensorType": "TEMPERATURE",
  "timestamp": "2026-08-26T10:00:00Z",
  "alertGenerated": false
}
```

**If threshold exceeded:**

```json
{
  "status": "accepted",
  "containerNumber": "MSKU1234567",
  "sensorType": "TEMPERATURE",
  "timestamp": "2026-08-26T10:00:00Z",
  "alertGenerated": true,
  "alertId": "alert-x1y2z3-a4b5-6789-cde0-123456789012",
  "alertMessage": "Temperature 42.5°C exceeds threshold of 40°C for container MSKU1234567"
}
```

---

#### GET /api/v1/integration/iot/readings/{containerId}

Get latest sensor readings for a container.

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `containerId` | UUID | Container ID |

**Query Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `sensorType` | enum | — | Filter by specific sensor type |
| `since` | datetime | -24h | Start of time range for readings |

**Success Response — 200 OK:**

```json
{
  "containerId": "550e8400-e29b-41d4-a716-446655440000",
  "containerNumber": "MSKU1234567",
  "lastUpdated": "2026-08-26T10:00:00Z",
  "readings": [
    {
      "sensorType": "TEMPERATURE",
      "value": "4.2",
      "unit": "°C",
      "timestamp": "2026-08-26T10:00:00Z",
      "batteryLevel": 72,
      "status": "NORMAL",
      "deviceId": "sensor-MSKU1234567-temp-01"
    },
    {
      "sensorType": "HUMIDITY",
      "value": "65.3",
      "unit": "%",
      "timestamp": "2026-08-26T10:00:00Z",
      "batteryLevel": 80,
      "status": "NORMAL",
      "deviceId": "sensor-MSKU1234567-hum-01"
    },
    {
      "sensorType": "DOOR_SENSOR",
      "value": "CLOSED",
      "timestamp": "2026-08-26T10:00:00Z",
      "batteryLevel": 85,
      "status": "NORMAL",
      "deviceId": "sensor-MSKU1234567-door-01"
    },
    {
      "sensorType": "GPS",
      "value": "1.2644,103.8222",
      "timestamp": "2026-08-26T10:00:00Z",
      "batteryLevel": 68,
      "status": "NORMAL",
      "deviceId": "sensor-MSKU1234567-gps-01",
      "location": {
        "latitude": 1.2644,
        "longitude": 103.8222
      }
    }
  ]
}
```

---

### 8.3 Customs Endpoints

#### GET /api/v1/integration/customs/status

Get customs status for containers.

**Query Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `containerNumber` | string | — | Filter by specific container |
| `status` | enum | — | Filter by customs status |
| `cursor` | string | — | Pagination cursor |
| `limit` | integer | 25 | Page size |

**Success Response — 200 OK:**

```json
{
  "data": [
    {
      "containerId": "550e8400-e29b-41d4-a716-446655440000",
      "containerNumber": "MSKU1234567",
      "customsStatus": "CLEARED",
      "reason": "Documentation verified, duties paid",
      "clearedBy": "customs-officer-01",
      "clearedAt": "2026-08-25T08:00:00Z",
      "declarationNumber": "DEC-2026-SG-001234",
      "dutyStatus": "PAID",
      "holdReason": null,
      "lastUpdated": "2026-08-25T08:00:00Z"
    }
  ],
  "pagination": {
    "nextCursor": null,
    "hasMore": false,
    "totalCount": 1,
    "limit": 25
  }
}
```

**Customs Statuses:**

| Status | Description |
|--------|-------------|
| `PENDING` | Customs declaration submitted, awaiting review |
| `UNDER_REVIEW` | Customs authority is reviewing documentation |
| `CLEARED` | Container cleared for release |
| `HELD` | Container held by customs (requires action) |
| `RELEASED` | Container released after clearance |
| `EXAMINATION` | Container selected for physical examination |
| `ABANDONED` | Container abandoned / documentation expired |

---

#### PUT /api/v1/integration/customs/status/{containerId}

Update customs status for a container.

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `containerId` | UUID | Container ID |

**Request Body:**

```json
{
  "status": "CLEARED",
  "reason": "Documentation verified, duties paid",
  "clearedBy": "customs-officer-01",
  "declarationNumber": "DEC-2026-SG-001234",
  "dutyStatus": "PAID",
  "holdReason": null
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `status` | enum | yes | New customs status |
| `reason` | string | yes | Reason for status change |
| `clearedBy` | string | conditional | Required when status is `CLEARED` |
| `declarationNumber` | string | no | Customs declaration reference |
| `dutyStatus` | enum | no | `PENDING`, `PAID`, `WAIVED`, `EXEMPT` |
| `holdReason` | string | conditional | Required when status is `HELD` |

**Success Response — 200 OK:** Returns updated customs record.

**Error Responses:**

| Status | Condition |
|--------|-----------|
| 404 | Container not found |
| 422 | Invalid status transition |

---

## 9. Notification Service

> **Service:** smartportx-notification  
> **Gateway Route:** `/api/v1/notifications/*`, `/ws/*`  
> **Database:** smartportx_notification  
> **Responsibilities:** Real-time WebSocket notifications, user preference management, alert routing

### 9.1 WebSocket Endpoints

#### WS /ws/notifications

WebSocket endpoint for real-time notifications using STOMP protocol.

**Connection URL:**

```
ws://localhost:8080/ws/notifications
```

**Connection Headers:**

```
Authorization: Bearer {jwt_token}
```

**STOMP Protocol:**

1. Client connects to WebSocket endpoint
2. Client sends STOMP CONNECT frame with Authorization header
3. Client subscribes to relevant topics
4. Server pushes notifications as STOMP MESSAGE frames

**Available Subscription Topics:**

| Topic | Description | Payload |
|-------|-------------|---------|
| `/topic/yard-status` | Yard-wide status updates | Yard metrics snapshot |
| `/topic/gate-status` | Gate queue and processing updates | Queue status |
| `/topic/vessel-status` | Vessel arrival/departure/discharge events | Vessel event |
| `/topic/containers/{id}` | Container-specific events | Container event |
| `/topic/appointments/{id}` | Appointment status changes | Appointment event |
| `/topic/alerts` | System alerts and warnings | Alert object |

**Notification Payload Format:**

```json
{
  "notificationId": "ntf-a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "category": "YARD_UPDATE",
  "severity": "INFO",
  "title": "Container Positioned",
  "message": "Container MSKU1234567 placed at B01-B03-R02-T03",
  "timestamp": "2026-08-26T10:30:00Z",
  "data": {
    "containerId": "550e8400-e29b-41d4-a716-446655440000",
    "containerNumber": "MSKU1234567",
    "position": "B01-B03-R02-T03",
    "operator": "jdoe"
  },
  "actionUrl": "/containers/550e8400-e29b-41d4-a716-446655440000"
}
```

**Notification Categories:**

| Category | Description |
|----------|-------------|
| `YARD_UPDATE` | Yard layout, stacking, or optimization changes |
| `GATE_EVENT` | Gate-in, gate-out, queue changes |
| `VESSEL_UPDATE` | Vessel arrival, departure, discharge progress |
| `CONTAINER_EVENT` | Container status change, movement |
| `APPOINTMENT` | Appointment booked, confirmed, checked-in |
| `ALERT` | System alerts (reefer temp, DG violation, etc.) |
| `CUSTOMS` | Customs status updates |
| `IOT` | IoT sensor alerts |

**Severity Levels:**

| Level | Description | Example |
|-------|-------------|---------|
| `INFO` | Informational update | Container positioned |
| `WARNING` | Requires attention | Reefer temp approaching threshold |
| `ERROR` | Requires immediate action | Reefer temp exceeded threshold |
| `CRITICAL` | System-wide critical event | Safety incident |

**Heartbeat:**

Server sends STOMP heartbeat frames every 30 seconds. Client should respond with heartbeat to maintain connection.

**Reconnection:**

Client should implement exponential backoff reconnection:
- Initial delay: 1 second
- Max delay: 30 seconds
- Max retries: 5

---

### 9.2 Notification Preferences Endpoints

#### GET /api/v1/notifications/preferences

Get the current user's notification preferences.

**Authentication:** Bearer token required

**Success Response — 200 OK:**

```json
{
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "emailEnabled": true,
  "emailAddress": "jdoe@smartportx.com",
  "smsEnabled": false,
  "smsNumber": null,
  "websocketEnabled": true,
  "pushEnabled": false,
  "categories": {
    "YARD_UPDATE": true,
    "GATE_EVENT": true,
    "VESSEL_UPDATE": true,
    "CONTAINER_EVENT": true,
    "APPOINTMENT": true,
    "ALERT": true,
    "CUSTOMS": true,
    "IOT": false
  },
  "quietHours": {
    "enabled": false,
    "start": "22:00",
    "end": "06:00",
    "timezone": "UTC"
  },
  "digestEnabled": false,
  "digestFrequency": "DAILY",
  "updatedAt": "2026-08-20T14:30:00Z"
}
```

---

#### PUT /api/v1/notifications/preferences

Update the current user's notification preferences.

**Request Body (all fields optional — partial update):**

```json
{
  "emailEnabled": true,
  "emailAddress": "john.doe@smartportx.com",
  "smsEnabled": false,
  "websocketEnabled": true,
  "pushEnabled": true,
  "categories": {
    "YARD_UPDATE": true,
    "GATE_EVENT": true,
    "VESSEL_UPDATE": false,
    "CONTAINER_EVENT": true,
    "APPOINTMENT": true,
    "ALERT": true,
    "CUSTOMS": true,
    "IOT": true
  },
  "quietHours": {
    "enabled": true,
    "start": "22:00",
    "end": "06:00",
    "timezone": "Asia/Singapore"
  },
  "digestEnabled": true,
  "digestFrequency": "DAILY"
}
```

**Success Response — 200 OK:** Returns updated preferences object.

---

## 10. Data Models

### Container

```json
{
  "id": "uuid",
  "containerNumber": "string (ISO 6346)",
  "containerType": "enum: 20DC|40DC|40HC|45HC|20RF|40RF|20TK|40TK",
  "owner": "string",
  "consignee": "string",
  "weight": "decimal (kg)",
  "tareWeight": "decimal (kg)",
  "maxPayload": "decimal (kg)",
  "sealNumber": "string",
  "dangerousGoods": "DangerousGoods|null",
  "billOfLading": "string",
  "originPort": "string (UN/LOCODE)",
  "destinationPort": "string (UN/LOCODE)",
  "expectedArrival": "datetime",
  "status": "enum: EXPECTED|RECEIVED|IN_YARD|POSITIONED|READY_FOR_PICKUP|LOADED|RELEASED|GATE_OUT",
  "currentLocation": "string (yard position)|null",
  "customsStatus": "enum: PENDING|UNDER_REVIEW|CLEARED|HELD|RELEASED|EXAMINATION|ABANDONED",
  "createdAt": "datetime",
  "updatedAt": "datetime"
}
```

### Yard Position

```
Format: B{block}-B{bay}-R{row}-T{tier}
Example: B01-B03-R02-T03

Block: 1-2 alphanumeric characters (e.g., B01, R01)
Bay:   1-3 digits (e.g., 01, 003)
Row:   1-2 digits (e.g., 01, 06)
Tier:  1-2 digits (e.g., 01, 05 — ground level is 01)
```

### Appointment

```json
{
  "id": "uuid",
  "confirmationNumber": "string (APT-YYYYMMDD-NNNN)",
  "truckingCompany": "string",
  "truckLicensePlate": "string",
  "driverName": "string",
  "driverLicense": "string",
  "appointmentType": "enum: PICKUP|DROP_OFF|BOTH",
  "containerNumber": "string",
  "preferredTimeSlot": "datetime",
  "confirmedTimeSlot": "datetime",
  "durationMinutes": "integer (15-120)",
  "timeWindowStart": "datetime",
  "timeWindowEnd": "datetime",
  "gateLane": "string",
  "status": "enum: PENDING|CONFIRMED|CHECKED_IN|COMPLETED|CANCELLED|NO_SHOW",
  "createdAt": "datetime",
  "updatedAt": "datetime"
}
```

### Vessel

```json
{
  "id": "uuid",
  "imoNumber": "string (IMO[0-9]{7})",
  "vesselName": "string",
  "vesselType": "enum: CONTAINER|BULK|TANKER|GENERAL|ROLL_ON_ROLL_OFF",
  "length": "decimal (meters)",
  "beam": "decimal (meters)",
  "draft": "decimal (meters)",
  "teuCapacity": "integer",
  "grossTonnage": "decimal",
  "flag": "string (ISO 3166-1 alpha-2)",
  "operator": "string",
  "callSign": "string",
  "currentStatus": "enum: AT_ANCHOR|IN_PORT|IN_TRANSIT|UNDER_MAINTENANCE",
  "currentBerth": "integer|null",
  "createdAt": "datetime"
}
```

### Gate Transaction

```json
{
  "gateTransactionId": "uuid",
  "appointmentId": "uuid",
  "status": "enum: CHECKED_IN|IN_YARD|CHECKED_OUT",
  "gateLane": "string",
  "checkInTime": "datetime",
  "checkOutTime": "datetime|null",
  "eirNumber": "string",
  "truckLicensePlate": "string",
  "driverLicense": "string",
  "containerNumber": "string",
  "sealNumber": "string",
  "sealIntact": "boolean|null",
  "documentsVerified": "boolean",
  "yardLocation": "string",
  "instructions": "string"
}
```

### Notification

```json
{
  "notificationId": "uuid",
  "category": "enum: YARD_UPDATE|GATE_EVENT|VESSEL_UPDATE|CONTAINER_EVENT|APPOINTMENT|ALERT|CUSTOMS|IOT",
  "severity": "enum: INFO|WARNING|ERROR|CRITICAL",
  "title": "string",
  "message": "string",
  "timestamp": "datetime",
  "data": "object (context-specific)",
  "actionUrl": "string (relative path)"
}
```

---

## 11. Event Schema

All inter-service events use the **CloudEvents** specification (v1.0) over Apache Kafka.

### CloudEvents Envelope

```json
{
  "specversion": "1.0",
  "type": "com.smartportx.container.status-changed",
  "source": "smartportx-container",
  "id": "evt-550e8400-e29b-41d4-a716-446655440000",
  "time": "2026-08-26T10:30:00.000Z",
  "datacontenttype": "application/json",
  "subject": "550e8400-e29b-41d4-a716-446655440000",
  "traceparent": "00-abc123def456-789abc000001-01",
  "data": {
    "containerId": "550e8400-e29b-41d4-a716-446655440000",
    "containerNumber": "MSKU1234567",
    "previousStatus": "IN_YARD",
    "newStatus": "POSITIONED",
    "location": "B01-B03-R02-T03",
    "operator": "jdoe"
  }
}
```

### Event Types

| Event Type | Source Service | Description |
|------------|----------------|-------------|
| `com.smartportx.container.registered` | container | New container registered |
| `com.smartportx.container.status-changed` | container | Container status transition |
| `com.smartportx.container.moved` | container | Container physical movement |
| `com.smartportx.container.positioned` | container | Container placed in yard slot |
| `com.smartportx.yard.layout-updated` | yard | Yard layout change |
| `com.smartportx.yard.optimization-completed` | yard | Optimization run finished |
| `com.smartportx.gate.check-in` | gate | Truck checked in |
| `com.smartportx.gate.check-out` | gate | Truck checked out |
| `com.smartportx.vessel.arrived` | vessel | Vessel arrived at berth |
| `com.smartportx.vessel.departed` | vessel | Vessel departed |
| `com.smartportx.vessel.discharge-progress` | vessel | Discharge progress update |
| `com.smartportx.appointment.booked` | gate | Appointment created |
| `com.smartportx.appointment.confirmed` | gate | Appointment confirmed |
| `com.smartportx.customs.status-changed` | integration | Customs status update |
| `com.smartportx.iot.alert` | integration | IoT sensor threshold alert |
| `com.smartportx.edifact.received` | integration | EDIFACT message processed |

---

## 12. Security Considerations

### Transport Security

- All API traffic must use **TLS 1.3** in production.
- HTTP (non-TLS) is permitted only in local development environments.
- HSTS header is enforced: `Strict-Transport-Security: max-age=31536000; includeSubDomains`.

### Authentication Security

- JWTs are signed with **RS256** (RSA + SHA-256) using 2048-bit keys.
- Access tokens expire after **15 minutes** (900 seconds).
- Refresh tokens expire after **7 days** and are single-use.
- Failed login attempts trigger progressive lockout:
  - 5 failures: 15-minute lockout
  - 10 failures: 1-hour lockout
  - 15 failures: account disabled, admin notification

### Input Validation

- All request inputs are validated against schemas before processing.
- SQL injection protection via parameterized queries (JPA/Hibernate).
- XSS protection via output encoding.
- Container number format strictly validated: `[A-Z]{4}[0-9]{7}`.

### Authorization

- Role-based access control (RBAC) enforced at gateway level.
- Each endpoint declares required roles in its contract.
- Service-to-service calls use elevated-privilege JWTs scoped to internal network only.

### Data Protection

- PII (driver names, licenses) is encrypted at rest.
- Audit logs are immutable and retained for 7 years.
- API keys and secrets are stored in HashiCorp Vault (not in code or config files).

### Rate Limiting

- Per-user rate limiting enforced at API Gateway (see §2.7).
- Circuit breaker pattern for inter-service calls (Resilience4j).
- Bulkhead isolation per service to prevent cascade failures.

### CORS Policy

```json
{
  "allowedOrigins": ["https://smartportx.example.com"],
  "allowedMethods": ["GET", "POST", "PUT", "DELETE", "OPTIONS"],
  "allowedHeaders": ["Authorization", "Content-Type", "X-Request-ID", "X-Idempotency-Key"],
  "allowCredentials": true,
  "maxAge": 3600
}
```

---

## 13. Change Log

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0.0 | 2026-08-26 | SmartPortX Team | Initial API contract documentation |
| 0.9.0 | 2026-08-15 | SmartPortX Team | Pre-release review draft |
| 0.8.0 | 2026-08-01 | SmartPortX Team | Added Integration Service endpoints |
| 0.7.0 | 2026-07-15 | SmartPortX Team | Added Vessel Service, Notification Service |
| 0.6.0 | 2026-07-01 | SmartPortX Team | Added Gate Service endpoints |
| 0.5.0 | 2026-06-15 | SmartPortX Team | Added Container Service lifecycle endpoints |
| 0.4.0 | 2026-06-01 | SmartPortX Team | Added Yard Service optimization endpoints |
| 0.3.0 | 2026-05-15 | SmartPortX Team | Added Identity Service, User/Role management |
| 0.2.0 | 2026-05-01 | SmartPortX Team | Added Container Service basic CRUD |
| 0.1.0 | 2026-04-15 | SmartPortX Team | Initial draft — Yard Service layout endpoints |

---

*This document is the single source of truth for all SmartPortX API contracts. All API implementations must conform to these contracts. Changes to API contracts require review and approval from the API Governance Board.*
