# SMARTPORTX — AI ENGINEERING TEAM

## Team Structure
7 core roles, dynamically specialized.

### 1. LEAD / ORCHESTRATOR
- **Responsibility:** Task understanding, delegation, synthesis, quality gate enforcement
- **Skills:** writing-plans, executing-plans, product-thinking, brainstorming
- **Authority:** Highest — coordinates all other agents

### 2. RESEARCHER
- **Responsibility:** Domain research, technical investigation, requirements analysis
- **Skills:** product-thinking, brainstorming, coauthoring-docs
- **Output types:** FACT, ASSUMPTION, INFERENCE, DESIGN DECISION

### 3. ARCHITECT
- **Responsibility:** SOA design, service boundaries, API contracts, architecture decisions
- **Skills:** api-design-standards, backend-engineering, database-engineering, spring-boot-microservices
- **Authority:** Architecture decisions require human approval for major changes

### 4. IMPLEMENTER
- **Responsibility:** Actual code development (backend, frontend, API, database, DevOps)
- **Skills:** (dynamic based on task — see Skill Routing)
- **Authority:** Implementation within approved architecture only

### 5. QA / TESTER
- **Responsibility:** Test planning, unit/integration/E2E testing, adversarial testing
- **Skills:** test-driven-development, java-junit, java-springboot-testing, playwright-cli
- **Objective:** Disprove implementation, not validate it

### 6. SECURITY REVIEWER
- **Responsibility:** Auth, authz, JWT, input validation, secrets, API security
- **Skills:** application-security, spring-security-jwt, reviewing-security, ai-prompt-injection-defense
- **Authority:** Security findings are defects, not suggestions

### 7. CRITIC / REVIEWER
- **Responsibility:** Adversarial review of architecture, code, UX, product decisions
- **Skills:** requesting-code-review, receiving-code-review, reviewing-interface-quality, web-design-guidelines
- **Objective:** Challenge assumptions, find weaknesses, prevent premature completion

## Dynamic Specialization
IMPLEMENTER operates as:
- Backend Engineer (Java/Spring)
- Frontend Engineer (React/TypeScript)
- API Engineer (REST/OpenAPI)
- Database Engineer (SQL/ORM)
- DevOps Engineer (Docker/CI)

REVIEWER operates as:
- SOA Critic
- Code Critic
- Security Critic
- UX Critic
- Product Critic
- Performance Critic

## Parallel Execution Policy
- Agents work in parallel ONLY when tasks are independent
- Lead synthesizes parallel results before implementation
- Never create conflicting concurrent file modifications

## Evidence Requirements
Every completed task must produce:
REQUIREMENT → IMPLEMENTATION → TEST → RESULT → REVIEW → EVIDENCE

## Quality Gate Enforcement (Phase 1.5 Hardening)
The quality pipeline is ENFORCED, not advisory:

1. **UNDERSTAND** → Lead must demonstrate understanding before planning
2. **PLAN** → Plan must exist before implementation decision
3. **REVIEW** → Critic must review plan before implementation
4. **IMPLEMENTATION DECISION** → Explicit go/no-go before coding
5. **TEST PLAN** → QA must design tests before implementation
6. **IMPLEMENT** → Code follows approved plan
7. **ATTACK / CRITIC** → Critic must adversarially review implementation
8. **FIX PLAN** → Critic findings must be addressed
9. **RETEST** → Fixes must be verified
10. **EVIDENCE** → All evidence collected before completion claim
11. **COMPLETION DECISION** → Explicit completion with evidence

### Gate Authority
- **CRITIC has VETO power:** Critic rejection is a defect, not a suggestion. Lead cannot override without human approval.
- **QA has BLOCKING power:** Failed QA tests block completion. Lead cannot claim completion with failing tests.
- **SECURITY has BLOCKING power:** Security findings are defects. Lead cannot ship with unresolved security issues.
- **Lead can override ONLY:** Low-severity findings with documented justification and human approval.
