# Module: Orchestrator + Report Builder + SSE Streaming

This phase wires the full pipeline together with real-time streaming.
Each agent's result is sent to the React UI immediately upon completion.

---

## 0. Project Context

- Base package: com.autonomouspm
- Core package: com.autonomouspm.core
- Infrastructure package: com.autonomouspm.infrastructure
- Controller package: com.autonomouspm.controller
- Language: Java 21, Spring Boot 3.5.0
- Logging: SLF4J only — zero System.out.println
- No new Maven dependencies — SseEmitter is built into Spring Boot
- No new database tables except one new column on project_state

---

## 1. ProjectState + ProjectStateEntity Update

Before building new files, update two existing files:

### ProjectState.java
Add field:
  private String finalReport;
Add getter/setter for finalReport.

### ProjectStateEntity.java
Add field:
  @Column(name = "final_report", columnDefinition = "TEXT")
  private String finalReport;

Update fromState(): entity.setFinalReport(state.getFinalReport())
Update toState(): state.setFinalReport(this.finalReport)

Add new PipelineStatus enum value: PARTIAL_COMPLETE

---

## 2. SSE Event Types

Create PipelineEvent.java in com.autonomouspm.core:

```java
// One SSE event sent to React UI after each agent completes
public record PipelineEvent(
    String eventName,     // matches agent name — see list below
    String status,        // COMPLETED | FAILED | PARTIAL
    Object data,          // the context record — serialized as JSON
    String message        // human readable e.g. "Requirements analyzed"
) {}
```

Event names — use exactly these strings:
- "requirement-complete"
- "business-complete"
- "database-complete"
- "planner-complete"
- "risk-complete"
- "pipeline-complete"
- "pipeline-failed"

---

## 3. File 1 — AgentTask.java

Create AgentTask.java in com.autonomouspm.infrastructure.

```java
// BRIDGE PATTERN — Abstraction
// Separates the task definition from its execution mechanism.
// CentralOrchestrator works with AgentTask only —
// never depends on how execution happens internally.
public interface AgentTask<T> {
    T execute(ProjectState state);
    String agentName();
    String eventName(); // SSE event name to emit on completion
}
```

Create LlmAgentTask.java in com.autonomouspm.infrastructure:

```java
// BRIDGE PATTERN — Concrete Implementation
// Executes an Agent<T> against ProjectState.
// Future: swap with RuleBasedAgentTask without touching Orchestrator.
public class LlmAgentTask<T> implements AgentTask<T> {
    private final Agent<T> agent;
    private final String agentName;
    private final String eventName;

    public LlmAgentTask(Agent<T> agent, String agentName, String eventName) {
        this.agent = agent;
        this.agentName = agentName;
        this.eventName = eventName;
    }

    @Override
    public T execute(ProjectState state) {
        return agent.process(state);
    }

    @Override
    public String agentName() { return agentName; }

    @Override
    public String eventName() { return eventName; }
}
```

---

## 4. File 2 — ProjectReportBuilder.java

Create ProjectReportBuilder.java in com.autonomouspm.core.

```java
// BUILDER PATTERN — builds Markdown report section by section.
// Each withX() method adds one agent's section.
// Diagrams NOT embedded — React renders them directly from
// databaseContext.mermaidErdChart and ganttContext.mermaidGanttChart.
// build() assembles the final Markdown string.
public class ProjectReportBuilder {

    private final StringBuilder report = new StringBuilder();

    public ProjectReportBuilder withHeader(String projectIdea) {...}
    public ProjectReportBuilder withRequirements(RequirementContext ctx) {...}
    public ProjectReportBuilder withBusinessAnalysis(BusinessContext ctx) {...}
    public ProjectReportBuilder withDatabaseDesign(DatabaseContext ctx) {...}
    public ProjectReportBuilder withProjectPlan(GanttContext ctx) {...}
    public ProjectReportBuilder withRiskAnalysis(RiskContext ctx) {...}
    public ProjectReportBuilder withFooter() {...}

    // BUILDER PATTERN — terminal operation
    public String build() { return report.toString(); }
}
```

### Section format rules:
- Each section starts with ## heading
- Lists use - bullet points
- If context is null or empty — skip section entirely
- Never write "null" or "empty" in report
- NO Mermaid diagrams embedded — React renders from context fields

### Sections in order:
1. # Project Report: {projectIdea}
2. ## Requirements Summary
   - List functional requirements
   - List constraints
3. ## Business Analysis
   - List epics
   - Total user stories count
   - List core features
4. ## Database Design
   - List table names and descriptions only
   - Note: "ERD diagram rendered in UI"
5. ## Project Plan
   - List phases and task count per phase
   - Note: "Gantt chart rendered in UI"
6. ## Risk Assessment
   - Overall risk level (LOW/MEDIUM/HIGH/CRITICAL)
   - Overall risk score
   - For each RiskFactor: category, description, mitigation
7. ---\n*Generated by Autonomous Project Manager*

---

## 5. File 3 — CentralOrchestrator.java

Create CentralOrchestrator.java in com.autonomouspm.core.

```java
// MEDIATOR PATTERN — CentralOrchestrator
// Agents never call each other directly.
// Every agent reports to Orchestrator which decides what runs next.
// After each agent completes, result is immediately streamed to UI
// via SseEmitter before the next agent starts.
@Service
public class CentralOrchestrator {

    // FACTORY PATTERN — Spring container instantiates and injects
    // the correct Agent implementation for each pipeline stage.
    // No manual AgentFactory class needed.
    private final RequirementAnalystAgent requirementAgent;
    private final BusinessAnalystAgent businessAgent;
    private final DatabaseArchitectAgent databaseAgent;
    private final ProjectPlannerAgent plannerAgent;
    private final RiskAnalystAgent riskAgent;
    private final ProjectStateRepository stateRepository;
    private final EventLogger eventLogger;
    private final ObjectMapper objectMapper;
}
```

### Method signature:
```java
public void runPipeline(String userIdea, SseEmitter emitter)
```

### Pipeline execution rules:

```
1. Create ProjectState with UUID pipeline ID
2. Set status = IN_PROGRESS
3. Save to PostgreSQL immediately

4. Define agents in order as LlmAgentTask list:
   - requirementAgent  → event "requirement-complete"
   - businessAgent     → event "business-complete"
   - databaseAgent     → event "database-complete"
   - plannerAgent      → event "planner-complete"
   - riskAgent         → event "risk-complete"

5. For each AgentTask:
   a. Execute task → get result
   b. Save result to correct ProjectState field
   c. Persist to PostgreSQL
   d. Emit SSE event immediately:
        emitter.send(SseEmitter.event()
            .name(task.eventName())
            .data(new PipelineEvent(
                task.eventName(),
                "COMPLETED",
                result,
                task.agentName() + " completed"
            )))
   e. If result is Null Object:
        emit PipelineEvent(eventName, "PARTIAL", result, "Agent failed")
        set status = PARTIAL_COMPLETE
        persist
        emitter.complete()
        return

6. After all agents succeed:
   a. Build Markdown via ProjectReportBuilder (all sections)
   b. Save finalReport to ProjectState
   c. Set status = COMPLETED
   d. Persist final state
   e. Emit "pipeline-complete" event with finalReport string
   f. Call emitter.complete()
```

### Null Object detection:
- RequirementContext failed: functionalRequirements.isEmpty()
- BusinessContext failed: userStories.isEmpty()
- DatabaseContext failed: tables.isEmpty()
- GanttContext failed: totalTasks == 0
- RiskContext failed: riskFactors.isEmpty()

### Error handling:
- Wrap every agent execution in try/catch
- On exception:
    log via SLF4J
    set status = FAILED
    persist
    emit "pipeline-failed" event with error message
    call emitter.completeWithError(e)
    return
- Never throw from runPipeline

---

## 6. File 4 — PipelineController.java

Create PipelineController.java in com.autonomouspm.controller.

```java
@RestController
@RequestMapping("/api/pipeline")
@CrossOrigin(origins = "*")  // React dev server
public class PipelineController {

    private final CentralOrchestrator orchestrator;
    private final ExecutorService executor = 
        Executors.newCachedThreadPool();

    // POST /api/pipeline/run
    // Body: { "idea": "food delivery app" }
    // Returns: SseEmitter — React connects via EventSource
    @PostMapping(value = "/run", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter runPipeline(@RequestBody PipelineRequest request) {
        
        SseEmitter emitter = new SseEmitter(300_000L); // 5 min timeout
        
        // Run pipeline in background thread — never block HTTP thread
        executor.submit(() -> {
            orchestrator.runPipeline(request.idea(), emitter);
        });
        
        return emitter;
    }

    // GET /api/pipeline/{pipelineId}
    // Returns saved ProjectState from PostgreSQL
    @GetMapping("/{pipelineId}")
    public ResponseEntity<ProjectState> getPipeline(
            @PathVariable String pipelineId) {...}
}
```

Create PipelineRequest.java in com.autonomouspm.controller:
```java
public record PipelineRequest(String idea) {}
```

---

## 7. React Integration Note (for frontend phase)

```javascript
// React connects like this — no library needed
const source = new EventSource('/api/pipeline/run');

source.addEventListener('requirement-complete', (e) => {
    const event = JSON.parse(e.data);
    // render RequirementContext section immediately
    setRequirements(event.data);
});

source.addEventListener('database-complete', (e) => {
    const event = JSON.parse(e.data);
    // render Mermaid ERD immediately
    setErdChart(event.data.mermaidErdChart);
});

source.addEventListener('pipeline-complete', (e) => {
    const event = JSON.parse(e.data);
    // render final markdown report
    setFinalReport(event.data);
    source.close();
});

source.addEventListener('pipeline-failed', (e) => {
    source.close();
});
```

Note: EventSource only supports GET requests by default.
For POST use fetch with ReadableStream instead:

```javascript
const response = await fetch('/api/pipeline/run', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ idea: userInput })
});

const reader = response.body.getReader();
// read chunks and parse SSE events manually
```

---

## 8. Execution Order — one file at a time, wait for approval:

1. ProjectState.java update        (add finalReport + PARTIAL_COMPLETE)
2. ProjectStateEntity.java update  (add finalReport column mapping)
3. PipelineEvent.java              (com.autonomouspm.core)
4. AgentTask.java                  (com.autonomouspm.infrastructure)
5. LlmAgentTask.java               (com.autonomouspm.infrastructure)
6. ProjectReportBuilder.java       (com.autonomouspm.core)
7. CentralOrchestrator.java        (com.autonomouspm.core)
8. PipelineRequest.java            (com.autonomouspm.controller)
9. PipelineController.java         (com.autonomouspm.controller)

### Hard Restrictions:
- No new Maven dependencies — SseEmitter is in Spring Boot already
- SLF4J only — no System.out.println
- Never throw from runPipeline — always emit error event
- Never return null — Null Object detection only
- Spring injection for agents — no manual AgentFactory class
- NO Mermaid syntax in ProjectReportBuilder output
- Pipeline runs in background thread — never block HTTP thread
- All pattern comments must be present:
    BRIDGE PATTERN — AgentTask + LlmAgentTask
    BUILDER PATTERN — ProjectReportBuilder
    MEDIATOR PATTERN — CentralOrchestrator
    FACTORY PATTERN — CentralOrchestrator constructor comment