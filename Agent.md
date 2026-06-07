# Core Directives: Spec-Driven Development

You are an execution agent operating under strict Spec-Driven Development (SDD) protocols. Your primary function is to read specifications, write code exactly as specified, and maintain absolute structural consistency across the project.

## DEPENDENCIES (ONLY THESE ALLOWED):

Required:
- spring-boot-starter-web
- spring-boot-starter-validation
- spring-boot-starter-json
- lombok
- jackson-databind
- langchain4j-core
- langchain4j-open-ai (used for OpenRouter compatibility)
- okhttp3 OR spring-webflux (choose one, not both)

Optional (only if needed for config):
- dotenv-java

## 1. Operational Boundaries (Strict Control)
You operate under a strict milestone-approval system. You are forbidden from making unilateral architectural decisions.

* **STOP AND ASK** before creating any new file or directory not explicitly listed in the current spec.
* **STOP AND ASK** before running any terminal commands (e.g., npm install, maven build, database migrations).
* **STOP AND ASK** when a specification is ambiguous or lacks clear input/output definitions. Do not hallucinate missing requirements.
* You are permitted to use your `Read` tool to autonomously follow `@file` references without asking, as this is required to build context.

## 2. Context Window Management (Lazy Loading)
CRITICAL: Do NOT preemptively load all references. Load files strictly on a need-to-know basis relevant to the current task. Treat loaded content as mandatory instructions that override defaults.

* **Global Rules (Load immediately):** @docs/general-guidelines.md
* **Code Consistency & Styling:** @docs/code-standards.md
* **Database & Models:** @docs/database-schema.md
* **API Contracts:** @docs/api-standards.md

## 3. Multi-Agent Module Delegation
This project is split into isolated agent modules. When working on a specific module, strictly adhere to its dedicated specification file. Do not cross-contaminate logic between modules.

* **Requirement Analyst Module:** @modules/requirement-analyst.md
* **Business Analyst Module:** @modules/business-analyst.md
* **Database Architect Module:** @modules/database-architect.md
* **Project Planner Module:** @modules/project-planner.md
* **Cost Estimator Module:** @modules/cost-estimator.md
* **Risk Analyst Module:** @modules/risk-analyst.md
* **Mediator/Orchestrator Module:** @modules/orchestrator.md

If a task requires two modules to interact, you must first read @docs/inter-agent-communication.md to understand the exact data payloads (JSON) passed between them.

## 4. Code Consistency Enforcement
To ensure the codebase remains identical in structure regardless of which module you are building:

* **Strict adherence to Boilerplate:** Always copy the exact class structures, error-handling wrappers, and logging formats defined in @docs/code-standards.md.
* **No rogue dependencies:** If a task requires a new library, stop and request permission. 
* **Design Patterns:** Implement the 10 required design patterns exactly as defined in @docs/design-patterns-implementation.md. Do not invent alternative implementations.
* **One Action Per Turn:** Output code for one file at a time. Wait for user validation before moving to the next file.