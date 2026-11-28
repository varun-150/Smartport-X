# HarborFlow — Project Instructions (AGENTS.md)

## Project Identity
**HarborFlow** — enterprise maritime container operations platform. Service-Oriented Architecture demonstration with 8 mandated microservices (container, vessel, yard, gate, resource, notification, reporting, auth), Spring Boot + Eureka + Spring Cloud Gateway, PostgreSQL, React.

## Relationship to Legacy SmartPortX (RECONCILED — user decision: DO NOT RENAME)
This workspace ALSO contains a fully-researched, designed, and partially-implemented legacy project **SmartPortX** (in `.smartport/` with 8 different services and a working codebase in `smartportx-*`). **User decision: do NOT rename SmartPortX.** Therefore:

- **HarborFlow is a FRESH project.** It is NOT a rename or rebrand of SmartPortX.
- Do NOT modify, rename, or copy SmartPortX artifacts into HarborFlow.
- SmartPortX's `.smartport/` docs and `smartportx-*` code are **kept as reference material ONLY** — do not treat them as HarborFlow deliverables.
- HarborFlow's decomposition (8 services incl. `resource` + `reporting` + `auth`) differs from SmartPortX (which had `integration`, `gateway`, `identity` and no standalone `resource`/`reporting`). Do NOT silently conflate the two; the SOA-ARCHITECT must produce a documented **reconciliation** of the differences during Phase 1, and the VIVA/CRITIC agents must be able to defend why HarborFlow has its own decomposition.
- Do not lift SmartPortX's already-completed research into HarborFlow without re-verifying. HarborFlow research is fresh.

## Phase-Gated Process (MANDATORY)
HarborFlow follows a strict, research-first, quality-gated process. **Phase order is mandatory and non-skippable:**

0. Research → 1. Comparison → 2. Gap Analysis → 3. Innovation Proposals → 4. Critical Evaluation → 5. Feasibility → 6. Final Direction → **PLAN.md** → Architecture → Implementation → Testing → Adversarial Review → Evidence.

**Hard rule: No application code is written before Phase 0 research and the PLAN.md gate.** Phases 0–6 are research/analysis/design only (docs in `docs/`).

Full phase definitions and gates: see `PHASES.md`.

## Information Classification (MANDATORY)
Every claim in every document must be classified:
- **FACT** — from assignment spec or verified primary source
- **SOURCE-DERIVED** — from a credible secondary source (cite it)
- **INFERENCE** — logical conclusion (state reasoning)
- **ASSUMPTION** — unverified belief (flag it)
- **DESIGN PROPOSAL** — our proposed solution

Never fabricate. Never present assumptions as facts. Use **"Potential differentiation"** until evidence supports a stronger claim. Every FACT/SOURCE-DERIVED claim carries a source citation.

## Skill Activation
Activate the MINIMAL relevant set. Prefer project skills (`harborflow-*`) for HarborFlow-specific work; use global opencode skills for general engineering tasks.

### Project Skills (activated per task)
| Skill | Activate when |
|---|---|
| `harborflow-domain` | Maritime/container operations domain logic, glossary, assignment spec |
| `harborflow-research` | Platform/incompetitor research, source verification, Phase 0 |
| `harborflow-soa` | Service boundaries, architecture, ADRs, decomposition |
| `harborflow-api` | REST endpoints, data models, contracts, error handling |
| `harborflow-testing` | Tests, verification, distributed test strategy |
| `harborflow-security` | Auth (auth service), RBAC, OWASP, threat modeling |
| `harborflow-critic` | Adversarial technical/product review |
| `harborflow-viva` | Evaluation/defense preparation |

### Global Skills (Tier 1 — general process)
writing-plans, executing-plans, brainstorming, product-thinking, requesting-code-review, receiving-code-review, reviewing-security, systematic-debugging, test-driven-development, verifying-before-completion, documentation-standards, writing-guidelines.

### Global Skills (stack-specific)
java-springboot, spring-boot-microservices, spring-data-jpa, api-design-standards, rest-api-conventions, backend-engineering, database-engineering, application-security, spring-security-jwt, layered-architecture, flyway-migrations, production-readiness, java-junit, java-springboot-testing, docker, evolving-apis-and-schemas, frontend-architecture, typescript-standards, designing-frontend-interfaces, spring-cloud-gateway, production-observability.

## Agent Team (roles in `.harborflow/agents/`)
RESEARCHER, DOMAIN-ANALYST, SOA-ARCHITECT, COMPETITIVE-ANALYST, TECHNICAL-CRITIC, PRODUCT-CRITIC, SECURITY-CRITIC, VIVA-CRITIC, ORCHESTRATOR.

- **ORCHESTRATOR** routes tasks, enforces phase gates, synthesizes; does NOT override specialist findings.
- Dispatch specialists via the task tool for independent work; do not duplicate their research.
- Present specialist findings to the user for decisions.

## Quality Gates (Stage-3 protocol)
1 UNDERSTAND → 2 PLAN → 3 REVIEW → 4 IMPLEMENT → 5 TEST → 6 ATTACK → 7 FIX → 8 RETEST → 9 EVIDENCE → 10 COMPLETE.
No phase advances without its gate criteria met (see PHASES.md). No task is complete without recorded evidence.

## Documentation Locations
- `AGENTS.md`, `PHASES.md` — process/instructions (this file + phases)
- `RESEARCH.md` — master research / competitor matrix
- `RESEARCH-TEMPLATE.md` — per-platform research template
- `docs/research/` — research outputs (platform-landscape, platform-*, sources, feature-comparison, gap-analysis, innovation-opportunities, viva-preparation)
- `docs/adrs/` — architecture decision records
- `docs/plans/` — plans (PLAN.md and phase plans)
- `docs/specs/` — API contracts / data models (created when design begins)
- `.harborflow/skills/` — HarborFlow project skills
- `.harborflow/agents/` — agent role definitions
- `.harborflow/skill-registry.json` — HarborFlow skill/agent registry

## Anti-Patterns (avoid)
- Writing application code before Phase 0 research / PLAN.md gate
- Fabricating facts or presenting assumptions as facts
- Copying SmartPortX research into HarborFlow without re-verification
- Renaming/merging SmartPortX into HarborFlow (user decided: keep separate)
- Overstating differentiation ("unique") without evidence
- Silently conflating HarborFlow's services with SmartPortX's services
- Skipping evidence recording before claiming completion
