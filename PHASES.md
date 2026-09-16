# HarborFlow — Phase Definitions & Quality Gates

This file defines the mandatory phase sequence for HarborFlow and the gate criteria that MUST be met before advancing to the next phase. **Phase order is non-skippable.** No phase advances without its gate criteria met.

## Phase Map
```
0  Research
1  Comparison
2  Gap Analysis
3  Innovation Proposals
4  Critical Evaluation
5  Feasibility
6  Final Direction
[ GATE: PLAN.md ]
A  Architecture
I  Implementation
T  Testing
R  Adversarial Review
E  Evidence & Completion
```

## Phase 0 — Research
- **Goal:** Evidence-based investigation of maritime terminal operating systems, container management platforms, port community systems, and competitors. No design, no code.
- **Key activities:**
  - Domain research (TOS, PCS, YMS, container tracking, gate/appointment, yard optimization, resource management, reporting)
  - Platform landscape discovery (do NOT assume Navis N4 or any leader is "best")
  - Source verification — authoritative, cited sources only
  - Build the feature/competitor matrix (RESEARCH.md)
- **Deliverables:** `docs/research/platform-landscape.md`, `docs/research/platform-<name>.md`, `docs/research/sources.md`, populated `RESEARCH.md` matrix
- **Gate criteria:**
  - Every claim is information-classified
  - Every FACT/SOURCE-DERIVED claim has a citation
  - Multiple platforms researched (not just a few leaders)
  - Negative findings documented
  - No application code written

## Phase 1 — Comparison
- **Goal:** Compare the researched platforms across capabilities, architecture, technology, and market segmentation.
- **Key activities:** feature-comparison matrix, market segmentation (leader/challenger/niche), architecture comparison
- **Deliverables:** `docs/research/feature-comparison.md`, `docs/research/architecture-comparison.md`
- **Gate criteria:**
  - Comparison includes all researched platforms
  - Each cell is evidence-attributed
  - Architecture comparison covers the SOA-relevant patterns
  - Reconciliation of HarborFlow vs SmartPortX service decomposition documented (by SOA-ARCHITECT)

## Phase 2 — Gap Analysis
- **Goal:** Identify genuine market/feature gaps — missing features, weak implementations, integration gaps — with evidence.
- **Key activities:** gap identification, cross-checking against assignment mandate, distinguishing genuine gaps from perceived ones
- **Deliverables:** `docs/research/gap-analysis.md`
- **Gate criteria:**
  - Gaps are grounded in evidence, not invention
  - Gaps are checked against the 8 mandated HarborFlow services
  - Assumptions about gaps are explicitly flagged

## Phase 3 — Innovation Proposals
- **Goal:** Propose innovations / differentiators based on the gap analysis.
- **Key activities:** formulate innovation proposals, use "Potential differentiation" language, map each to the assignment/market need
- **Deliverables:** `docs/research/innovation-opportunities.md`
- **Gate criteria:**
  - Each proposal is traceable to a gap + evidence
  - Differentiation claims are not overstated ("unique" only with proof)
  - Proposals are scoped to what the SOA demonstration can realistically show

## Phase 4 — Critical Evaluation
- **Goal:** Adversarially evaluate the innovation proposals (technical + product).
- **Key activities:** TECHNICAL-CRITIC and PRODUCT-CRITIC challenge each proposal (evidence, alternatives, trade-offs, user value, scope)
- **Deliverables:** critique / challenge reports
- **Gate criteria:**
  - Each proposal received technical + product critique
  - Valid findings incorporated or explicitly rejected with reason
  - No scope creep survives review

## Phase 5 — Feasibility
- **Goal:** Assess whether the proposed direction is feasible within the SOA demonstration scope and stack.
- **Key activities:** technical feasibility, effort/risk assessment, alignment with assignment mandates
- **Deliverables:** feasibility assessment
- **Gate criteria:**
  - Feasibility is based on the mandated stack and research findings
  - Risks identified with mitigations
  - Alignment with the 8-service assignment confirmed

## Phase 6 — Final Direction
- **Goal:** Synthesize research + critique + feasibility into a single recommended direction.
- **Key activities:** converge on the final scope, decomposition, tech choices, and innovation set
- **Deliverables:** final-direction statement feeding PLAN.md
- **Gate criteria:**
  - Direction integrates specialist findings and user decisions
  - Decision record captures options, rationale, and trade-offs
  - Assumptions flagged

## GATE: PLAN.md
- **Goal:** Produce the definitive implementation plan (PLAN.md).
- **Gate criteria:**
  - Written from the final direction (Phase 6)
  - Reviewed before implementation
  - **No implementation may begin until this gate passes.** This is the hard research→code boundary.

## Phase A — Architecture
- **Goal:** Produce architecture and design artifacts from the plan.
- **Deliverables:** architecture overview, service boundaries, ADRs (in `docs/adrs/`), API contracts & data models (in `docs/specs/`), security architecture
- **Gate criteria:** architecture reviewed (TECHNICAL-CRITIC, SECURITY-CRITIC); ADRs recorded; design traces to PLAN.md and assignment

## Phase I — Implementation
- **Goal:** Implement the designed system (not before Phase A).
- **Gate criteria:** implementation follows approved design; tests accompany implementation (TDD where practical)

## Phase T — Testing
- **Goal:** Verify unified behavior across the distributed system.
- **Gate criteria:** unit/integration/contract/API/E2E/failure/security tests pass with recorded evidence; every FR/NFR/Security requirement traced

## Phase R — Adversarial Review
- **Goal:** Deliberately attack the implementation.
- **Gate criteria:** security review, critic review, failure injection run; valid findings fixed and retested

## Phase E — Evidence & Completion
- **Goal:** Record evidence for every completion claim.
- **Gate criteria:** no task marked complete without evidence; evidence log demonstrates the full verify chain

## Quality Gates (Stage-3 protocol — applies within and across phases)
1 UNDERSTAND → 2 PLAN → 3 REVIEW → 4 IMPLEMENT → 5 TEST → 6 ATTACK → 7 FIX → 8 RETEST → 9 EVIDENCE → 10 COMPLETE.
No task is complete without recorded evidence. "Done" is not evidence.
