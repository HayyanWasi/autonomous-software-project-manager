# Spring Boot Project Structure
## Fixed — do not change without user approval

---

## Directory Layout

```
backend/
├── src/
│   └── main/
│       ├── java/
│       │   └── com/spm/
│       │       ├── agents/
│       │       │   ├── IAgent.java
│       │       │   ├── RequirementAnalystAgent.java
│       │       │   ├── BusinessAnalystAgent.java
│       │       │   ├── DatabaseArchitectAgent.java
│       │       │   ├── ProjectPlannerAgent.java
│       │       │   ├── CostEstimatorAgent.java
│       │       │   └── RiskAnalystAgent.java
│       │       ├── patterns/
│       │       │   ├── mediator/AgentMediator.java
│       │       │   ├── chain/AgentChain.java
│       │       │   ├── observer/PipelineEventBus.java
│       │       │   ├── builder/OutputBuilder.java
│       │       │   ├── factory/AgentFactory.java
│       │       │   ├── composite/OutputComposite.java
│       │       │   ├── singleton/GeminiClient.java
│       │       │   ├── adapter/GeminiResponseAdapter.java
│       │       │   ├── bridge/AgentOutputBridge.java
│       │       │   └── nullobject/NullAgent.java
│       │       ├── dto/
│       │       │   ├── AgentInputDto.java
│       │       │   ├── AgentOutputDto.java
│       │       │   ├── SrsOutputDto.java
│       │       │   ├── UserStoriesOutputDto.java
│       │       │   ├── ErdOutputDto.java
│       │       │   ├── GanttOutputDto.java
│       │       │   ├── BudgetOutputDto.java
│       │       │   └── RiskReportOutputDto.java
│       │       ├── prompts/
│       │       │   ├── RequirementAnalystPrompt.java
│       │       │   ├── BusinessAnalystPrompt.java
│       │       │   ├── DatabaseArchitectPrompt.java
│       │       │   ├── ProjectPlannerPrompt.java
│       │       │   ├── CostEstimatorPrompt.java
│       │       │   └── RiskAnalystPrompt.java
│       │       ├── controller/
│       │       │   └── PipelineController.java
│       │       └── config/
│       │           └── AppConfig.java
│       └── resources/
│           ├── application.properties
│           └── outputs/              ← agent outputs saved here
├── pom.xml
└── outputs/                          ← runtime output files
```

---

## Maven Dependencies (pom.xml)

Only these dependencies are approved. Add nothing without user approval.

```xml
<!-- Spring Boot Web -->
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-web</artifactId>
</dependency>

<!-- HTTP Client for Gemini API -->
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-webflux</artifactId>
</dependency>

<!-- JSON -->
<dependency>
  <groupId>com.fasterxml.jackson.core</groupId>
  <artifactId>jackson-databind</artifactId>
</dependency>

<!-- Lombok (reduce boilerplate) -->
<dependency>
  <groupId>org.projectlombok</groupId>
  <artifactId>lombok</artifactId>
  <scope>provided</scope>
</dependency>

<!-- Logging -->
<dependency>
  <groupId>org.slf4j</groupId>
  <artifactId>slf4j-api</artifactId>
</dependency>
```