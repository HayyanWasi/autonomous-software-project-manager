# Module: Requirement Analyst Agent

This is the first agent in the Chain of Responsibility.

Its responsibility is to transform an unstructured project idea into a validated RequirementContext that becomes the foundation for all downstream agents.

---

## 1. Input Specifications

### Data Source

Raw user input string.

Example:

```text
I need a food delivery application for university students.
```

### Pre-Processing Rules

Before invoking the LLM:

Validate input.

Rules:

* Minimum length: 10 characters
* Minimum words: 3
* Input must describe a software system, application, platform, automation, or business process

If validation fails:

```java
return AgentResult.failure(
    "Project description is too short."
);
```

No LLM call should be made.

---

## 2. Dependencies

Required services:

```java
AiService
EventLogger
ProjectStateRepository
```

Required patterns:

* Adapter Pattern (AiService)
* Observer Pattern (EventLogger)
* Mediator Pattern (CentralOrchestrator)

---

## 3. Agent Execution Flow

Step 1

Publish event:

```text
REQUIREMENT_ANALYSIS_STARTED
```

Step 2

Load:

```text
BaseAgentPrompt.md
requirement-analyst.md
```

Step 3

Construct final system prompt.

Step 4

Call:

```java
aiService.generateStructuredOutput(...)
```

Step 5

Deserialize response into:

```java
RequirementContext
```

Step 6

Run validation rules.

Step 7

Update ProjectState.

Step 8

Persist state.

Step 9

Publish completion event.

---

## 4. LLM Responsibilities

The LLM must act as:

```text
Senior Software Requirements Engineer
```

The model must identify:

### Project Idea

Single concise statement.

### Executive Summary

Business-focused overview.

### User Roles

Examples:

* Customer
* Admin
* Vendor

### Core Features

Examples:

* Authentication
* Search
* Notifications

### Assumptions

Explicit assumptions made due to missing information.

### Constraints

Technology or business limitations.

### Non Functional Requirements

Examples:

* Security
* Scalability
* Availability

### Open Questions

Questions required for later clarification.

### Completion Score

Percentage representing requirement completeness.

Range:

```text
0 - 100
```

### Clarification Flag

```java
needsClarification
```

Must be:

```text
true
```

when:

* critical information is missing
* assumptions exceed 3 items
* completionScore < 70

---

## 5. Hallucination Prevention Rules

The model MUST NOT:

* invent technologies
* invent integrations
* invent user roles not supported by input
* invent business rules

If information is missing:

add to:

```java
openQuestions
```

instead of guessing.

---

## 6. Output Schema

Response MUST be valid JSON.

No markdown.

No explanations.

No code fences.

Schema:

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

---

## 7. Validation Rules

After deserialization:

Reject output if:

```java
projectIdea == null
```

or

```java
userRoles.isEmpty()
```

or

```java
coreFeatures.isEmpty()
```

or

```java
completionScore < 0
```

or

```java
completionScore > 100
```

Return:

```java
AgentResult.failure(...)
```

---

## 8. State Update Rules

Upon success:

```java
ProjectState.setRequirementContext(...)
```

Persist:

```java
ProjectStateEntity
```

through repository.

---

## 9. Event Publishing

Publish:

```text
REQUIREMENT_ANALYSIS_STARTED
REQUIREMENT_ANALYSIS_COMPLETED
REQUIREMENT_ANALYSIS_FAILED
```

Include:

* projectId
* timestamp
* execution duration

---

## 10. Return Type

Agent must return:

```java
AgentResult<RequirementContext>
```

Never return null.

Success:

```java
AgentResult.success(context)
```

Failure:

```java
AgentResult.failure(errorMessage)
```

---

## 11. Execution Directives

When implementing this module:

1. Generate RequirementAnalystAgent.
2. Inject AiService.
3. Inject EventLogger.
4. Inject ProjectStateRepository.
5. Implement validation layer.
6. Implement JSON deserialization.
7. Implement state persistence.
8. Implement event publishing.
9. Return AgentResult<RequirementContext>.
10. Ensure all code compiles under Java 21 and Spring Boot 3.
