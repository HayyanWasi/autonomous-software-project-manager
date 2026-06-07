# Module: Requirement Analyst Agent

This is the first agent in the Chain of Responsibility. Its job is to take the user's initial raw input and expand it into a structured Software Requirements Specification (SRS) summary.

## 1. Input Specifications
* **Data Source:** Raw String input from the user (e.g., "I need a food delivery app").
* **Pre-processing:** Check if the input is too brief (under 3 words). If so, trigger an error state demanding more context.

## 2. Core Logic
* Connect to the `AiService` (Adapter pattern).
* Use a strict system prompt directing the LLM to act as a Senior Requirement Analyst.
* The prompt must instruct the LLM to identify: Primary Users, Core Features, and Non-Functional Requirements.

## 3. Output Specifications
* The LLM response MUST be parsed into a Java Record named `RequirementContext`.
* `RequirementContext` must contain:
```java
public record RequirementContext(
    String projectIdea,
    String executiveSummary,

    List<String> userRoles,
    List<String> coreFeatures,

    List<String> assumptions,
    List<String> constraints,
    List<String> nonFunctionalRequirements,
    List<String> openQuestions,

    double completionScore,
    boolean needsClarification
) {}
```
* This record is then passed to the `CentralOrchestrator` (Mediator) to be routed to the Business Analyst.

## 4. Execution Directives
When tasked to build this module:
1. Generate the `RequirementContext` Record first.
2. Generate the `RequirementAnalyst` class implementing your base Agent interface.
3. Implement the LLM prompt and Jackson parsing logic.