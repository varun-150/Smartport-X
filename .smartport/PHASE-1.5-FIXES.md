# SMARTPORTX — PHASE 1.5 FIXES

## Fix Protocol
Only safe, justified fixes applied. No aesthetic refactoring.

---

## FIX F-001: Reclassify Skills with META/AI ENGINEERING Category

**Problem:** META/AI ENGINEERING skills scattered across CORE, SUPPORTING, SPECIALIZED, IRRELEVANT
**Root Cause:** Phase 1 did not distinguish application-domain skills from AI-infrastructure skills
**Proposed Fix:** Add META category to SKILL-MATRIX.md, reclassify 8 skills
**Risk:** LOW — documentation change only
**Change:** Update SKILL-MATRIX.md with new META section
**Validation:** Verify all META skills are correctly listed

## FIX F-002: Reclassify Misclassified IRRELEVANT Skills

**Problem:** ai-prompt-injection-defense and web-design-guidelines marked IRRELEVANT but are Tier 1
**Root Cause:** Phase 1 classified by application domain only
**Proposed Fix:** Move both to appropriate categories (META and CORE respectively)
**Risk:** LOW — documentation change only
**Change:** Update SKILL-MATRIX.md classifications
**Validation:** Verify reclassified skills are in correct categories

## FIX F-003: Record Test Decision in DECISIONS.md

**Problem:** Project memory test requires a recorded decision
**Root Cause:** Test procedure requires persistence verification
**Proposed Fix:** Add D-004 test decision to DECISIONS.md
**Risk:** NONE — test only, marked as test
**Change:** Append to DECISIONS.md
**Validation:** Verify decision persists and is readable

## FIX F-004: Add Phase 1.5 Evidence Entries

**Problem:** EVIDENCE.md needs entries for Phase 1.5 validation
**Root Cause:** Evidence-based completion requires recording
**Proposed Fix:** Add E-004 through E-008 entries
**Risk:** NONE — documentation only
**Change:** Append to EVIDENCE.md
**Validation:** Verify entries are complete and accurate

## FIX W-001: Add Quality Gate Enforcement to AGENT-TEAM.md

**Problem:** Quality gates defined but not enforced
**Root Cause:** Pipeline defined without blocking mechanism
**Proposed Fix:** Add explicit enforcement rules to AGENT-TEAM.md
**Risk:** LOW — documentation change only
**Change:** Add "Quality Gate Enforcement" section to AGENT-TEAM.md
**Validation:** Verify enforcement rules are clear

## FIX W-002: Add Critic/QA Blocking Authority to AGENT-TEAM.md

**Problem:** Critic and QA are advisory, not blocking
**Root Cause:** Authority defined but not enforced
**Proposed Fix:** Add explicit blocking authority rules
**Risk:** LOW — documentation change only
**Change:** Add "Gate Authority" subsection to quality gates
**Validation:** Verify authority rules are clear

## NOT FIXED (Justified):

**W-003 (Architecture drift detection):** Requires automated tooling not available in current environment. Documented as known limitation.
**W-004 (Parallel output conflicts):** Manual resolution by Lead is appropriate for current scale. Automated conflict resolution would add unnecessary complexity.
**W-005 (Skill degradation monitoring):** Requires external monitoring not available. Documented as known limitation.
**W-006 (Agent roles conceptual):** OpenCode architecture limitation. Roles are enforced by documentation and discipline, not by platform. Documented as known limitation.
