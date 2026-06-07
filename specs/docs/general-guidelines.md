# Global Tech Stack and Architecture Guidelines

This document contains the non-negotiable technology stack and architectural rules for the Autonomous Software Project Manager. These rules apply to all modules.

## 1. Technology Stack
* **Language:** Java 17+
* **Build Tool:** Maven. Use standard directory structures (`src/main/java`, `src/test/java`).
* **AI Orchestration:** LangChain4j. Do not use Python frameworks or raw HTTP clients for AI orchestration.
* **LLM Provider:** OpenRouter. Use `OpenAiChatModel` from LangChain4j configured with the OpenRouter Base URL and appropriate model names (e.g., Claude 3.5 Sonnet or Gemini 2.5 Flash etc..).
* **Data Parsing:** Jackson. All unstructured LLM text must be parsed into strict Java Objects using Jackson annotations.
* **Database (Future-proofing):** PostgreSQL. Assume pgvector will be used for memory storage.

## 2. Architectural Boundaries
* **No Bloatware:** Do not introduce heavy web frameworks (like Spring Boot) unless explicitly instructed in a module spec. We are building a modular backend AI pipeline, not a REST API (yet).
* **Immutability:** Use Java Records for Data Transfer Objects (DTOs) passed between agents.
* **Error Handling:** Never swallow exceptions. Use the Null Object Pattern for AI generation failures rather than returning standard nulls to prevent pipeline crashes.
* **Logging:** Implement standard SLF4J logging for every agent action. Do not use `System.out.println`.