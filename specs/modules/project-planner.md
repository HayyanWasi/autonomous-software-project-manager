# Module: Project Planner Agent

This is the fourth agent in the Chain of Responsibility.

Its responsibility is to transform business requirements, user stories, and database design into a structured Work Breakdown Structure (WBS) and project execution plan.

The Project Planner is responsible for task decomposition, sequencing, and dependency mapping.

The Project Planner is NOT responsible for estimating duration, effort, cost, or team size.

Those responsibilities belong to the Cost Estimator Agent.

---

## 1. Input Specifications

### Data Sources

Provided via the CentralOrchestrator (Mediator):

* RequirementContext
* BusinessContext
* DatabaseContext

### Required Inputs

The planner must consume:

* Functional Requirements
* User Stories
* Epics
* Database Entities
* Database Relationships
* High-Level Constraints

---

## 2. Core Logic

### Design Pattern

Uses the Composite Design Pattern to represent project structure.

Structure:

Project
→ Phases
→ Tasks

### AI Responsibilities

Connect to the AiService (Adapter Pattern).

The LLM acts as a Senior Technical Project Planner.

Responsibilities:

* Convert requirements into implementation tasks
* Organize tasks into project phases
* Determine execution order
* Identify dependencies
* Create a Work Breakdown Structure (WBS)
* Generate a visualization-ready Gantt structure

### Observer Updates

Publish progress through EventLogger (Observer Pattern):

Examples:

* "Analyzing Requirements..."
* "Generating Work Breakdown Structure..."
* "Mapping Dependencies..."
* "Generating Gantt Structure..."

---

## 3. Planning Constraints

The Project Planner MUST:

### Requirement Traceability

Every task must be traceable to:

* A Requirement
* A User Story
* A Database Component

### Dependency Rules

* Circular dependencies are prohibited
* A dependency must reference an existing task
* Invalid dependencies must be rejected

### Phase Rules

Every task must belong to a project phase.

Allowed phases:

* Requirements
* Design
* Database
* Backend Development
* Frontend Development
* Integration
* Testing
* Deployment

### Scope Rules

The planner must NOT:

* Estimate duration
* Estimate cost
* Estimate effort
* Estimate team size
* Recommend salaries
* Generate project budgets

These responsibilities belong to the Cost Estimator Agent.

---

## 4. Output Specifications

The LLM response MUST be parsed into a Java Record named GanttContext.

### GanttContext

```java
public record GanttContext(
    ProjectNode rootProject,
    int totalPhases,
    int totalTasks,
    String planningSummary,
    String mermaidGanttChart
) {}
```

### Composite Pattern Contracts

```java
public interface ProjectComponent {

    String name();

    List<ProjectComponent> children();
}
```

### ProjectNode

```java
public record ProjectNode(
    String name,
    List<ProjectComponent> children
) implements ProjectComponent {}
```

### TaskLeaf

```java
public record TaskLeaf(
    String name,
    String assigneeRole,
    String complexity,
    List<String> dependencies
) implements ProjectComponent {

    @Override
    public List<ProjectComponent> children() {
        return Collections.emptyList();
    }
}
```

### Complexity Values

Allowed values:

* Small
* Medium
* Large
* Very Large

These values will later be used by the Cost Estimator Agent to calculate effort and duration.

---

## 5. Mermaid Generation Rules

The planner must generate a valid Mermaid Gantt chart.

However:

The Mermaid chart is NOT the source of truth.

The Composite Project Structure is the authoritative representation.

The Mermaid chart is a visualization artifact generated from the project structure.

---

## 6. Validation Rules

Before returning output, the planner must verify:

* Every task belongs to a phase
* Every dependency references an existing task
* No circular dependencies exist
* Every task maps to at least one requirement or user story
* No orphan tasks exist

If validation fails:

Return a PlanningValidationError.

Do not generate a Gantt chart.

---

## 7. Output Flow

Project Planner
→ GanttContext
→ CentralOrchestrator (Mediator)
→ Cost Estimator Agent

The Cost Estimator Agent will use:

* Task Complexity
* Dependencies
* Task Count
* Project Structure

to calculate:

* Duration
* Effort
* Cost
* Team Allocation

---

## 8. Execution Directives

When tasked to build this module:

1. Generate Composite Pattern interfaces and records first.
2. Generate GanttContext.
3. Generate ProjectPlanner implementing the base Agent interface.
4. Implement validation logic.
5. Implement Mermaid generation.
6. Ensure output is fully serializable using Jackson.
7. Do not implement cost or duration estimation.
