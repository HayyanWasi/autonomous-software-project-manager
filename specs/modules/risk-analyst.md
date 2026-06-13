# Module: Risk Analyst Agent

This is the final agent in the Chain of Responsibility.

Its responsibility is to research real-world risks for the user's 
original project idea using web search, then summarize findings 
into structured risk factors.

The Risk Analyst does NOT analyze pipeline artifacts.
The Risk Analyst does NOT perform cost or budget forecasting.

---

## 0. Project Context

- Base package: com.autonomouspm
- Context package: com.autonomouspm.context
- Agent package: com.autonomouspm.agents.risk
- Infrastructure package: com.autonomouspm.infrastructure
- Token management package: com.autonomouspm.tokenmanagement
- Language: Java 21, Spring Boot 3.5.0, LangChain4j 1.0.0-beta3
- DTOs: Java Records only — no regular classes for data
- Logging: SLF4J only — zero System.out.println
- All LLM calls: CachingAiService only — never AiService directly
- Token budget: 800 max output tokens
- JSON parsing: Jackson only

---

## 1. Input Specifications

The Risk Analyst receives ONLY:
- The original user idea string (e.g. "food delivery app")
- Extracted from RequirementContext.projectIdea (one field only)

Do NOT pass full ProjectState to the LLM.
Do NOT pass DatabaseContext, GanttContext, or BusinessContext to the LLM.
InputStripper.toRiskAnalystInput() must be updated to return only 
the idea string, not the full ProjectState.

---

## 2. Web Search Logic

Inject MarketResearchTool (already exists at com.autonomouspm.service.MarketResearchTool).
Do NOT create a new tool.

Run exactly 3 searches using search(String query):

Search 1: "{idea} startup risks failures"
Search 2: "{idea} legal compliance challenges"  
Search 3: "{idea} technical challenges problems"

Where {idea} is the original user idea string.

Rules:
- Run all 3 searches before calling the LLM
- Combine results into one string, labelled by search type
- If all 3 return INSUFFICIENT_EVIDENCE sentinel, 
  skip LLM call and return EmptyRiskContext (Null Object)
- If only some return INSUFFICIENT_EVIDENCE, proceed 
  with available results only

---

## 3. LLM Call

After collecting search results, call CachingAiService with:

System prompt:
  "You are a Senior Risk Analyst. Analyze the following real-world 
   research about {idea} and identify 4-6 concrete risks. 
   Return strict JSON only — no markdown, no preamble."

User prompt:
  Combined search results string

The LLM must return JSON matching RiskContext structure exactly.
Token budget: 800 max output tokens.

---

## 4. Observer Updates via EventLogger:

- "Researching Market Risks..."
- "Analyzing Legal Risks..."
- "Analyzing Technical Risks..."
- "Calculating Risk Scores..."
- "Finalizing Risk Report..."

---

## 5. Records to Create in com.autonomouspm.context:

```java
public record RiskContext(
    int overallRiskScore,
    String overallRiskLevel,    // LOW | MEDIUM | HIGH | CRITICAL
    List<RiskFactor> riskFactors,
    String conclusion
) {}

public record RiskFactor(
    String category,            // Technical | Legal | Market | Resource
    String description,
    String evidence,            // must come from search results
    String mitigationStrategy,
    int impactLevel,            // 1-5
    int probabilityLevel,       // 1-5
    int riskScore               // impactLevel × probabilityLevel
) {}

public record RiskAnalysisValidationError(
    String agentName,
    String errorCode,
    String message
) {}
```

### Overall Risk Level:
Derived from average riskScore of all RiskFactors:
- 1-8   = LOW
- 9-14  = MEDIUM
- 15-19 = HIGH
- 20-25 = CRITICAL

### Null Object on any failure — never return null:
EmptyRiskContext:
- overallRiskScore = 0
- overallRiskLevel = "UNKNOWN"
- riskFactors = List.of()
- conclusion = ""

---

## 6. Validation Rules

Create RiskAnalystValidator.java in com.autonomouspm.agents.risk.

Validate:
- At least one RiskFactor exists
- Every RiskFactor has non-empty evidence
- Every RiskFactor has non-empty mitigationStrategy
- Every riskScore equals impactLevel × probabilityLevel
- No duplicate risk descriptions
- impactLevel and probabilityLevel are between 1-5

On failure:
- Log warnings via SLF4J
- Return RiskAnalysisValidationError
- Do NOT throw exceptions
- Do NOT return null

---

## 7. Output Flow

RiskAnalystAgent
→ RiskContext
→ CentralOrchestrator (Mediator)
→ ProjectReportBuilder (Builder Pattern)

---

## 8. InputStripper Update

Update existing InputStripper.toRiskAnalystInput() in 
com.autonomouspm.tokenmanagement.InputStripper.

Current signature returns full ProjectState — change it to:

  public static String toRiskAnalystInput(ProjectState state) {
      if (state == null || state.getRequirementContext() == null) {
          return "";
      }
      return state.getRequirementContext().projectIdea();
  }

Only extract the idea string. Nothing else.

---

## 9. Execution Order — one file at a time, wait for approval each:

1. RiskContext.java              (com.autonomouspm.context)
2. RiskFactor.java               (com.autonomouspm.context)
3. RiskAnalysisValidationError.java (com.autonomouspm.context)
4. InputStripper.java update     (update toRiskAnalystInput only)
5. RiskAnalystValidator.java     (com.autonomouspm.agents.risk)
6. RiskAnalystAgent.java         (com.autonomouspm.agents.risk)

### Hard Restrictions:
- Do not add any Maven dependency not in pom.xml
- Do not create any file not listed above
- SLF4J only — no System.out.println
- CachingAiService only for LLM calls
- MarketResearchTool reused directly — no new search tool
- Never return null — Null Object always
- Java Records for all DTOs
- Exactly 3 Tavily searches — no more, no less