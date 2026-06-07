
# Module: Risk Analyst Agent

This is the final agent in the Chain of Responsibility.

Its responsibility is to evaluate all project artifacts produced by previous agents and identify risks that could negatively impact project success.

The Risk Analyst focuses on:

* Technical Risks
* Requirement Risks
* Schedule Risks
* Resource Risks

The Risk Analyst does NOT perform cost estimation or budget forecasting.

---

## 1. Input Specifications

### Data Source

Provided via the CentralOrchestrator (Mediator):

* RequirementContext
* BusinessContext
* DatabaseContext
* GanttContext

### Required Inputs

The Risk Analyst must consume:

* Requirements
* User Stories
* Business Goals
* Database Design
* Relationships
* Project Tasks
* Dependencies
* Project Phases
* Timeline Estimates

---

## 2. Core Logic

### Design Patterns

* Adapter Pattern through AiService
* Mediator Pattern through CentralOrchestrator
* Observer Pattern through EventLogger
* Builder Pattern for final report generation

### Agent Role

The LLM must act as a Senior Software Risk Manager.

Responsibilities:

* Analyze project artifacts
* Detect technical risks
* Detect requirement risks
* Detect schedule risks
* Detect resource risks
* Recommend mitigation strategies
* Calculate risk severity

---

## 3. Risk Evaluation Rules

### Technical Risks

Evaluate:

* Complex database relationships
* Excessive table coupling
* Many-to-many relationships
* Scalability concerns
* Integration complexity

### Requirement Risks

Evaluate:

* Ambiguous requirements
* Missing requirements
* Conflicting requirements
* Excessive assumptions

### Schedule Risks

Evaluate:

* Long dependency chains
* Critical path bottlenecks
* Large implementation phases
* High task complexity

### Resource Risks

Evaluate:

* Excessive workload
* Specialized skill requirements
* Single points of failure
* High implementation complexity

---

## 4. Hallucination Prevention Rules

The Risk Analyst MUST NOT:

* Invent risks unsupported by project artifacts
* Create generic risks without evidence
* Generate unsupported conclusions

Every risk must be traceable to:

* RequirementContext
* BusinessContext
* DatabaseContext
* GanttContext

If a risk cannot be justified, it must be excluded.

---

## 5. Risk Scoring Model

Risk Score Formula:

Risk Score = Impact Level × Probability Level

Where:

Impact Level:

* 1 = Negligible
* 2 = Minor
* 3 = Moderate
* 4 = Major
* 5 = Critical

Probability Level:

* 1 = Rare
* 2 = Unlikely
* 3 = Possible
* 4 = Likely
* 5 = Very Likely

Maximum Risk Score = 25

---

## 6. Overall Risk Classification

Calculate overall project risk:

* 0–20 = LOW
* 21–40 = MEDIUM
* 41–60 = HIGH
* 61+ = CRITICAL

The overall score must be derived from identified risks.

The LLM must not arbitrarily choose a risk level.

---

## 7. Output Specifications

The LLM response MUST be parsed into a Java Record named RiskContext.

```java
public record RiskContext(
    int overallRiskScore,
    String overallRiskLevel,
    List<RiskFactor> riskFactors,
    String conclusion
) {}
```

```java
public record RiskFactor(
    String category,
    String description,
    String evidence,
    String mitigationStrategy,
    int impactLevel,
    int probabilityLevel,
    int riskScore
) {}
```

### Evidence Requirement

Every RiskFactor must include evidence.

Example:

```text
Category:
Schedule

Evidence:
Task "Order Processing Module"
depends on 5 other tasks

Risk:
Potential delivery delay due to dependency chain

Mitigation:
Break implementation into smaller milestones
```

---

## 8. Validation Rules

Before returning output:

* Every risk must contain evidence
* Every risk must contain mitigation
* Every risk score must equal Impact × Probability
* Every risk must map to at least one project artifact
* Duplicate risks must be removed

If validation fails:

Return RiskAnalysisValidationError.

---

## 9. Observer Updates

Publish state updates:

* "Analyzing Requirements..."
* "Evaluating Database Risks..."
* "Evaluating Schedule Risks..."
* "Calculating Risk Scores..."
* "Finalizing Risk Report..."

---

## 10. Output Flow

Risk Analyst
→ RiskContext
→ CentralOrchestrator (Mediator)
→ ProjectReportBuilder (Builder Pattern)

---

## 11. Final Reporting Trigger

Upon successful generation of RiskContext:

1. Notify CentralOrchestrator.
2. CentralOrchestrator triggers ProjectReportBuilder.
3. ProjectReportBuilder compiles:

   * Requirement Report
   * Business Analysis
   * Database Design
   * Project Plan
   * Risk Assessment
4. Generate final Markdown report.

---

## 12. Execution Directives

When tasked to build this module:

1. Generate RiskContext first.
2. Generate RiskFactor.
3. Generate RiskAnalyst implementing the base Agent interface.
4. Implement risk scoring validation.
5. Implement hallucination prevention rules.
6. Trigger final reporting through Mediator.
7. Ensure output is fully serializable using Jackson.


