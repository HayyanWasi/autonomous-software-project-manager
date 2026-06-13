# LangChain4j Agent Implementation Guide

LangChain4j behaves differently from the Python version of LangChain. It requires strict configuration, especially when enforcing structured outputs (JSON). This guide outlines the exact implementation pattern you must follow when writing concrete Agents for this pipeline.

## 1. Using the Adapter Pattern
Do not inject LangChain4j classes (like `OpenAiChatModel` or `ChatMemory`) directly into your Agent.
Instead, use the generic `AiService` adapter.

```java
// Correct Agent structure
@Component
public class RequirementAnalystAgent implements Agent<RequirementContext> {
    
    private final AiService aiService;
    private final EventLogger logger;
    
    public RequirementAnalystAgent(AiService aiService, EventLogger logger) {
        this.aiService = aiService;
        this.logger = logger;
    }
    
    @Override
    public AgentResult<RequirementContext> execute(ProjectState state) {
        // Implementation here...
    }
}
```

## 2. Enforcing JSON Schemas (Structured Output)

Because we use OpenRouter which aggregates many models (some of which lack native JSON-schema enforcement), we enforce schema output through **Jackson mapping** combined with **prompt engineering**.

1. **The Target Record:** Ensure your target Context (e.g., `RequirementContext`) has `@JsonIgnoreProperties(ignoreUnknown = true)` and uses Java Records.
2. **The Prompt Instruction:** You must explicitly inject the JSON schema requirements into your System Prompt.
3. **The Parsing Logic:** Use Jackson's `ObjectMapper` to parse the `aiService.chat(...)` response.

### Example Prompt String Injection:
```java
String systemPrompt = """
    You are a Senior Requirement Analyst.
    You MUST respond with pure, valid JSON matching the following schema.
    Do not wrap the JSON in markdown blocks like ```json ... ```.
    Do not output any additional text before or after the JSON.

    {
        "projectIdea": "string",
        "executiveSummary": "string",
        "userRoles": ["string"],
        "coreFeatures": ["string"],
        "completionScore": "double (0.0 - 1.0)",
        "needsClarification": "boolean"
    }
    """;
```

## 3. Handling Parsing & LLM Failures

If the LLM returns invalid JSON or hallucinates, the `ObjectMapper` will throw an exception.
You **MUST** catch this and return the **Null Object Pattern** via `AgentResult.failure()`.

```java
try {
    String rawJson = aiService.chat(systemPrompt, userPrompt);
    
    // Sometimes LLMs still wrap in markdown despite instructions.
    // Basic cleanup before parsing:
    if (rawJson.startsWith("```json")) {
        rawJson = rawJson.replaceAll("```json", "").replaceAll("```", "").trim();
    }
    
    ObjectMapper mapper = new ObjectMapper();
    RequirementContext output = mapper.readValue(rawJson, RequirementContext.class);
    
    return AgentResult.success(getName(), output);
    
} catch (Exception e) {
    // Return a Null Object on failure
    RequirementContext fallback = new RequirementContext(
        "", "Analysis Failed", List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), 0.0, true
    );
    return AgentResult.failure(getName(), fallback, "LLM parsing failed: " + e.getMessage());
}
```

## 4. Observer Logging

Always notify the `EventLogger` during your agent's lifecycle:

```java
logger.publish(getName(), EventType.REQUIREMENT_ANALYSIS_STARTED, "Analyzing raw input...");
// ... LLM call ...
logger.publish(getName(), EventType.REQUIREMENT_ANALYSIS_COMPLETED, "Parsed RequirementContext.");
```

## Checklist for New Agents
- [ ] Implement `Agent<YourContextType>`.
- [ ] Inject `AiService` and `EventLogger`.
- [ ] Define System Prompt with strict JSON mapping instructions.
- [ ] Handle markdown wrapper stripping.
- [ ] Parse using Jackson.
- [ ] Catch exceptions and return `AgentResult.failure(...)` with a valid Null Object.
