# SMARTPORTX — SKILL MATRIX

## Environment Summary
- **Skills on disk:** 77 folders
- **Skills registered:** 65 in skill-registry.json
- **Discrepancy:** 12 unregistered/partial skill folders

---

## CLASSIFICATION: META / AI ENGINEERING
Skills that govern the AI engineering team itself. These are the infrastructure of the team, not the application.

| # | Skill | Category | Tier | Reason |
|---|-------|----------|------|--------|
| 1 | ai-agent-engineering | ai | 2 | Agent system design and loops |
| 2 | context-engineering | ai | 2 | LLM context management |
| 3 | dispatching-parallel-agents | ai | 3 | Multi-agent coordination |
| 4 | subagent-driven-development | workflow | 3 | Parallel task execution |
| 5 | creating-skills | meta | 3 | Skill creation and modification |
| 6 | mcp-tool-integration | ai | 3 | MCP tool integration |
| 7 | ai-prompt-injection-defense | security | 1 | AI security (if LLM features built) |

Note: `customize-opencode` is a built-in skill (no folder), used only for OpenCode configuration.

---

## CLASSIFICATION: CORE
Required frequently for SmartPortX (SOA/port logistics platform).

| # | Skill | Category | Tier | Reason |
|---|-------|----------|------|--------|
| 8 | writing-plans | architecture | 1 | Every major task needs planning |
| 9 | executing-plans | workflow | 1 | Plan execution with checkpoints |
| 10 | product-thinking | product | 1 | Scope definition, MVP, user stories |
| 11 | brainstorming | product | 1 | Requirements exploration |
| 12 | systematic-debugging | debugging | 1 | Bug investigation protocol |
| 13 | test-driven-development | testing | 1 | Test-first implementation |
| 14 | verifying-before-completion | quality | 1 | Evidence before claims |
| 15 | requesting-code-review | code-review | 1 | Review before completion |
| 16 | receiving-code-review | code-review | 1 | Handling review feedback |
| 17 | reviewing-security | security | 1 | Security review gate |
| 18 | investigating-performance | performance | 1 | Performance analysis |
| 19 | documentation-standards | documentation | 1 | Project documentation |
| 20 | writing-guidelines | documentation | 1 | Documentation quality |
| 21 | web-design-guidelines | design | 1 | UI review gate (reclassified from IRRELEVANT) |
| 22 | java-springboot | backend | 2 | Primary backend framework |
| 23 | spring-boot-microservices | architecture | 2 | SOA/microservices design |
| 24 | spring-data-jpa | backend | 2 | Data access layer |
| 25 | api-design-standards | api | 2 | REST API design |
| 26 | rest-api-conventions | api | 2 | Controller/DTO patterns |
| 27 | backend-engineering | backend | 2 | Service layer patterns |
| 28 | database-engineering | backend | 2 | Schema, indexing, queries |
| 29 | application-security | security | 2 | OWASP, auth, validation |
| 30 | spring-security-jwt | security | 2 | JWT authentication |
| 31 | layered-architecture | architecture | 2 | Layer separation enforcement |
| 32 | flyway-migrations | backend | 2 | Database migrations |
| 33 | production-readiness | quality | 2 | Observability, deployment |
| 34 | java-junit | testing | 2 | Unit testing |
| 35 | java-springboot-testing | testing | 2 | Integration testing |
| 36 | docker | devops | 2 | Containerization |
| 37 | evolving-apis-and-schemas | api | 2 | API versioning/evolution |

## CLASSIFICATION: SUPPORTING
Useful when specific tasks require them.

| # | Skill | Category | Tier | Reason |
|---|---|---|---|---|
| 30 | frontend-architecture | architecture | 2 | If frontend is React/Next.js |
| 31 | typescript-standards | frontend | 2 | TypeScript code quality |
| 32 | designing-frontend-interfaces | ui | 2 | UI implementation |
| 33 | designing-user-experience | ux | 2 | UX design |
| 34 | building-accessible-interfaces | ui | 2 | Accessibility compliance |
| 35 | reviewing-interface-quality | ui | 2 | UI review |
| 36 | web-performance-core | performance | 2 | Frontend performance |
| 37 | playwright-cli | testing | 2 | Browser testing |
| 38 | testing-webapps | testing | 2 | Web app testing |
| 39 | production-observability | quality | 2 | Monitoring/logging |
| 40 | deploying-to-vercel | devops | 2 | If deploying to Vercel |
| 41 | using-git-worktrees | git | 2 | Feature isolation |
| 42 | finishing-a-development-branch | git | 2 | Branch completion |
| 43 | coauthoring-docs | documentation | 2 | Structured doc writing |
| 44 | writing-release-notes | documentation | 2 | Release documentation |
| 45 | nextjs-production | frontend | 2 | If using Next.js |
| 46 | vercel-react-best-practices | frontend | 2 | React performance |
| 47 | vercel-composition-patterns | frontend | 2 | React patterns |
| 48 | spring-cloud-gateway | architecture | 2 | API gateway |

## CLASSIFICATION: SPECIALIZED
Use only under particular circumstances.

| # | Skill | Category | Tier | Reason |
|---|---|---|---|---|
| 51 | rag-engineering | ai | 3 | Only if RAG features needed |
| 52 | building-mcp-servers | ai | 3 | Only if building MCP servers |
| 53 | building-finetuning-datasets | ai | 3 | Only for model fine-tuning |
| 54 | image-to-code | frontend | 3 | Only for image-to-code tasks |
| 55 | vercel-react-native-skills | frontend | 3 | Only for React Native |
| 56 | motion-web-animation | animation | 3 | Only for web animations |
| 57 | vercel-react-view-transitions | animation | 3 | Only for view transitions |
| 58 | narrative-design | game-design | 3 | Only for game narrative |
| 59 | applying-themes | design | 3 | Only for theming |
| 60 | brandkit | design | 3 | Only for brand design |
| 61 | high-end-visual-design | design | 3 | Only for premium UI |
| 62 | minimalist-ui | design | 3 | Only for minimalist style |
| 63 | industrial-brutalist-ui | design | 3 | Only for brutalist style |
| 64 | redesign-existing-projects | design | 3 | Only for redesigns |
| 65 | design-taste-frontend | design | 3 | Only for anti-slop UI |
| 66 | awesome-design | design | 3 | Only for design reference |

## CLASSIFICATION: IRRELEVANT
Should not be activated for SmartPortX.

| # | Skill | Category | Tier | Reason |
|---|-------|----------|------|--------|
| 67 | game-design | game-design | 2 | Not a game project |
| 68 | game-development | game-dev | 2 | Not a game project |
| 69 | game-ux | game-design | 2 | Not a game project |
| 70 | game-feel-animation | animation | 3 | Not a game project |
| 71 | vercel-optimize | performance | 2 | Only if deployed to Vercel |

## Skill Count Verification (Updated Phase 1.5)
- **Folders on disk:** 77
- **Registered in skill-registry.json:** 65
- **Unregistered valid folders:** 12 (docker, flyway-migrations, java-junit, java-springboot, java-springboot-testing, layered-architecture, production-observability, rest-api-conventions, spring-boot-microservices, spring-cloud-gateway, spring-data-jpa, spring-security-jwt)
- **Built-in (no folder):** 1 (customize-opencode)
- **Total loadable skills:** 78
- **META / AI Engineering:** 7 skills
- **Core:** 30 skills (including web-design-guidelines reclassified from IRRELEVANT)
- **Supporting:** 19 skills (ai-agent-engineering and context-engineering moved to META)
- **Specialized:** 16 skills (6 moved to META)
- **Irrelevant:** 5 skills (ai-prompt-injection-defense → META, web-design-guidelines → CORE, customize-opencode → META)
