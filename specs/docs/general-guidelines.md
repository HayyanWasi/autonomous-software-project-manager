# Global Tech Stack and Architecture Guidelines

This document contains the non-negotiable technology stack and architectural rules for the Autonomous Software Project Manager. These rules apply to all modules.

## 1. Technology Stack
* **Language:** Java 17+
* **Build Tool:** Maven. Use standard directory structures (`src/main/java`, `src/test/java`).
* **AI Orchestration:** LangChain4j. Do not use Python frameworks or raw HTTP clients for AI orchestration.
* **LLM Provider:** Gemini. Use `GeminiChatModel` from LangChain4j configured with the appropriate model names (e.g., Gemini 2.5 Flash etc..).
* **Data Parsing:** Jackson. All unstructured LLM text must be parsed into strict Java Objects using Jackson annotations.
* **Database (Future-proofing):** PostgreSQL. Assume pgvector will be used for memory storage.
Backend Framework: Spring Boot 3.x
- Use @Component, @Service, @Value annotations
- Spring Boot manages dependency injection
- Do NOT expose REST endpoints yet — pipeline runs as a backend service
AI Layer: LangChain4j with GeminiChatModel
- All LLM calls go through LangChain4j only
- Never call Gemini API directly via RestTemplate
- OkHttp3 is used only for external tool calls (e.g. Tavily)

## 2. Architectural Boundaries
* **No Bloatware:** We are building a modular backend AI pipeline, not a REST API (yet).
* **Immutability:** Use Java Records for Data Transfer Objects (DTOs) passed between agents.
* **Error Handling:** Never swallow exceptions. Use the Null Object Pattern for AI generation failures rather than returning standard nulls to prevent pipeline crashes.
* **Logging:** Implement standard SLF4J logging for every agent action. Do not use `System.out.println`.
