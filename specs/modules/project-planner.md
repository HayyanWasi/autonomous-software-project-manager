# Module: Project Planner Agent

This is the fourth agent in the Chain of Responsibility.

Responsibility: transform business requirements, user stories, and database 
design into a structured Work Breakdown Structure (WBS) and project execution plan.

The Project Planner handles task decomposition, sequencing, and dependency 
mapping ONLY.

NOT responsible for: duration, effort, cost, team size — those belong to 
Cost Estimator Agent.

---

## 0. Project Context

- Base package: com.autonomouspm
- Models package: com.autonomouspm.models (create if not exists)
- Agent package: com.autonomouspm.agents.planner
- Infrastructure package: com.autonomouspm.infrastructure
- Token management package: com.autonomouspm.tokenmanagement
- Language: Java 21, Spring Boot 3.5.0, LangChain4j 1.0.0-beta3
- DTOs: Java Records only
- Logging: SLF4J only — zero System.out.println
- All LLM calls: CachingAiService only — never AiService directly
- Token budget: 1500 max output tokens
- JSON parsing: Jackson only

---

## 1. Input Specifications

Provided via CentralOrchestrator (Mediator) as ProjectState.

### Input Stripping (mandatory)

InputStripper already exists at com.autonomouspm.tokenmanagement.InputStripper.

Call EXACTLY this existing method — do not create a new one:

  InputStripper.toProjectPlannerInput(DatabaseContext database, BusinessContext business)

This returns ProjectPlannerInput containing:
- List<Table> tables          (from DatabaseContext)
- List<Relationship> relationships (from DatabaseContext)
- List<String> epics          (from BusinessContext)

Do NOT read from ProjectState directly for LLM input.
Do NOT add a new method to InputStripper.

---

## 2. Core Logic

### Composite Design Pattern
Represents project structure as a tree:

  Project (ProjectNode — root)
  └── Phase (ProjectNode — composite)
      └── Task (TaskLeaf — leaf)

Mark each with comments:
  // COMPOSITE PATTERN — Component
  // COMPOSITE PATTERN — Composite
  // COMPOSITE PATTERN — Leaf

### AI Responsibilities
Connect to CachingAiService (never AiService directly).
LLM acts as Senior Technical Project Planner.

LLM must return strict JSON only — no markdown, no preamble, no explanation.

LLM responsibilities:
- Convert requirements into tasks
- Organize tasks into phases
- Determine execution order
- Identify dependencies
- Return WBS as JSON matching GanttContext structure

### Observer Updates via EventLogger:
- "Analyzing Requirements..."
- "Generating Work Breakdown Structure..."
- "Mapping Dependencies..."
- "Generating Gantt Structure..."

---

## 3. Planning Constraints

### Allowed Phases (LLM must use only these — validate and reject others):
- Requirements
- Design
- Database
- Backend Development
- Frontend Development
- Integration
- Testing
- Deployment

### Dependency Rules:
- No circular dependencies
- Dependencies must reference existing task names
- Invalid dependencies rejected

### Scope — planner must NOT produce:
- Duration estimates
- Cost estimates
- Effort estimates
- Team size or salary recommendations

---

## 4. Records to Create

Create in com.autonomouspm.models:

```java
// COMPOSITE PATTERN — Component
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = ProjectNode.class, name = "node"),
    @JsonSubTypes.Type(value = TaskLeaf.class, name = "leaf")
})
public interface ProjectComponent {
    String name();
    List<ProjectComponent> children();
}

// COMPOSITE PATTERN — Composite
public record ProjectNode(
    String name,
    List<ProjectComponent> children
) implements ProjectComponent {}

// COMPOSITE PATTERN — Leaf
public record TaskLeaf(
    String name,
    String assigneeRole,
    String complexity,        // Small | Medium | Large | Very Large
    List<String> dependencies
) implements ProjectComponent {
    @Override
    public List<ProjectComponent> children() {
        return Collections.emptyList();
    }
}

public record GanttContext(
    ProjectNode rootProject,
    int totalPhases,
    int totalTasks,
    String planningSummary,
    String mermaidGanttChart
) {}

public record PlanningValidationError(
    String agentName,
    String errorCode,
    String message
) {}
```

### Null Object on LLM failure:
Return EmptyGanttContext — never return null:
- rootProject = new ProjectNode("Empty", List.of())
- totalPhases = 0
- totalTasks = 0
- planningSummary = ""
- mermaidGanttChart = ""

---

## 5. MermaidGanttGenerator

Create MermaidGanttGenerator.java in com.autonomouspm.infrastructure.

Takes ProjectNode root, produces Mermaid Gantt string.
Pure Java — no LLM involved.

Output format:gantt
title Project Plan
dateFormat YYYY-MM-DD
section Backend Development
User Authentication :task1, 2024-01-01, 3d
Order Management    :task2, after task1, 5d

Rules:
- Each ProjectNode child = one section
- Each TaskLeaf = one task entry
- Complexity → placeholder duration:
    Small      → 2d
    Medium     → 4d
    Large      → 7d
    Very Large → 12d
- Dependencies use "after X" syntax
- Start date baseline: 2024-01-01, increment per phase
- Pure Java logic only

---

## 6. ProjectPlannerValidator

Create ProjectPlannerValidator.java in com.autonomouspm.agents.planner.

Validate:
- At least one phase exists
- At least one task exists
- Every task belongs to a phase
- Every dependency references an existing task name
- No circular dependencies (DFS check)
- No orphan tasks
- Phase names match allowed list only

On failure:
- Log warnings via SLF4J
- Return PlanningValidationError
- Do NOT generate Gantt chart
- Do NOT throw exceptions
- Do NOT return null

---

## 7. Output Flow

ProjectPlannerAgent → GanttContext → CentralOrchestrator → Cost Estimator Agent

Cost Estimator will use: task complexity, dependencies, task count, 
project structure.

---

## 8. Execution Order — build one file at a time, wait for approval each time:

1. ProjectComponent.java        (com.autonomouspm.models)
2. ProjectNode.java             (com.autonomouspm.models)
3. TaskLeaf.java                (com.autonomouspm.models)
4. GanttContext.java            (com.autonomouspm.models)
5. PlanningValidationError.java (com.autonomouspm.models)
6. MermaidGanttGenerator.java   (com.autonomouspm.infrastructure)
7. ProjectPlannerValidator.java (com.autonomouspm.agents.planner)
8. ProjectPlannerAgent.java     (com.autonomouspm.agents.planner)

### Hard Restrictions:
- No new Maven dependencies
- No new InputStripper methods — use existing toProjectPlannerInput
- SLF4J only — no System.out.println
- CachingAiService only — never AiService directly
- Never return null — Null Object pattern always
- Java Records for all DTOs
- Every task must trace to a requirement, user story, or database entity