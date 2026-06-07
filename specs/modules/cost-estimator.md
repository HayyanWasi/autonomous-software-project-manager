# Module: Cost Estimator Agent

This is the fifth agent in the Chain of Responsibility. Its job is to calculate the total budget and resource costs based on the project plan and duration.

## 1. Input Specifications
* **Data Source:** `GanttContext` (for duration and roles) and `RequirementContext` (for overall scope), provided via Mediator.

## 2. Core Logic
* Connect to the `AiService` (Adapter pattern).
* Instruct the LLM to act as a Project Cost Estimator.
* Traverse the Composite `GanttContext` structure to map roles (e.g., Frontend Dev, Backend Dev, DevOps) to standard hourly rates.
* Calculate the total effort estimation and budget breakdown.
* Publish state updates ("Calculating Effort...", "Estimating Budget...") via the `EventLogger` (Observer pattern).

## 3. Output Specifications
* The LLM response MUST be parsed into a Java Record named `CostContext`.
* `CostContext` must contain:
```java
public record TaskLeaf(
    String name,
    String assigneeRole,
    String complexity,
    int estimatedDays,
    String justification,
    List<String> dependencies
) {}
public record CostContext(
    double totalBudget,
    String currency,
    int totalEstimatedHours,
    List<RoleCost> costBreakdown,
    String financialSummary
) {}

public record RoleCost(
    String roleName,
    double hourlyRate,
    int allocatedHours,
    double totalCost
) {}
```
* This record is passed to the `CentralOrchestrator` (Mediator) to be routed to the Risk Analyst.

## 4. Execution Directives
When tasked to build this module:
1. Generate the `CostContext` and `RoleCost` Records first.
2. Generate the `CostEstimator` class implementing the base Agent interface.
3. Ensure the LLM performs accurate math (or rely on Java logic to recalculate the totals if LLM math is untrusted).

## 5. Estimation Rules

The Project Planner may estimate duration.

Duration must NOT be arbitrary.

Use:

Small      = 1-3 days
Medium     = 4-7 days
Large      = 8-15 days
Very Large = 16-30 days

Estimate duration using:
- Requirement complexity
- Database complexity
- Number of dependencies

The planner must provide justification for unusually large tasks.