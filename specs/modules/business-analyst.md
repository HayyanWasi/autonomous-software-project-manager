# Module: Business Analyst Agent

This is the second agent in the Chain of Responsibility.

Its responsibility is to transform business requirements into actionable business insights, user stories, epics, and market-driven recommendations.

Unlike other agents, this agent has access to a Market Research Tool and must incorporate external research into its analysis.

---

## 1. Input Specifications

### Data Source

Provided via the CentralOrchestrator (Mediator):

* RequirementContext

### Required Inputs

The Business Analyst must consume:

* Executive Summary
* User Roles
* Core Features
* Constraints
* Assumptions
* Non-Functional Requirements

---

## 2. Core Logic

### Design Pattern

* Uses Adapter Pattern through AiService.
* Communicates through CentralOrchestrator (Mediator).
* Publishes progress updates using EventLogger (Observer).

### Agent Role

The LLM must act as a Senior Business Analyst.

Responsibilities:

* Analyze requirements
* Identify business goals
* Generate epics
* Generate user stories
* Discover user pain points
* Analyze competitor weaknesses
* Recommend high-value features
* Validate business assumptions

---

## 3. Market Research Tool Usage

The Business Analyst has access to a Market Research Tool.

The tool may retrieve:

* Competitor information
* User reviews
* Forum discussions
* Public feedback
* Industry trends

Examples:

* Reddit discussions
* App Store reviews
* Play Store reviews
* Product review platforms
* Competitor websites

---

## 4. Research Rules

The Business Analyst MUST:

### Evidence First

Recommendations must be supported by either:

* Requirements
* Research findings

### Competitor Analysis

Identify:

* Common strengths
* Common weaknesses
* Missing opportunities

### Pain Point Analysis

Extract:

* Frequently reported complaints
* User frustrations
* Operational challenges

### Feature Recommendations

Recommend features only when supported by:

* User requirements
* Market evidence

---

## 5. Hallucination Prevention Rules

The Business Analyst MUST NOT:

* Invent competitors
* Invent market statistics
* Invent customer feedback
* Invent trends

If research data is unavailable:

* State "Insufficient Market Evidence"
* Continue analysis using requirements only

---

## 6. Output Specifications

The LLM response MUST be parsed into a Java Record named BusinessContext.

```java
public record BusinessContext(

    List<String> businessGoals,

    List<String> epics,

    List<UserStory> userStories,

    List<String> marketPainPoints,

    List<String> competitorInsights,

    List<String> recommendedFeatures,

    List<String> validatedAssumptions,

    String businessSummary

) {}
```

### UserStory

```java
public record UserStory(
    String actor,
    String action,
    String benefit
) {}
```

Example:

"As a Customer, I want to track my order so that I know when it will arrive."

```java
new UserStory(
    "Customer",
    "Track my order",
    "Know estimated delivery time"
);
```

---

## 7. Validation Rules

Before returning output:

* Every User Story must map to at least one requirement.
* Every Recommended Feature must have business justification.
* Every Competitor Insight must originate from research.
* Every Pain Point must originate from research.
* Duplicate stories must be removed.

If validation fails:

Return BusinessAnalysisValidationError.

---

## 8. Observer Updates

Publish state updates:

* "Analyzing Business Requirements..."
* "Researching Market Trends..."
* "Analyzing Competitors..."
* "Generating User Stories..."
* "Preparing Business Report..."

---

## 9. Output Flow

Business Analyst
→ BusinessContext
→ CentralOrchestrator (Mediator)
→ Database Architect Agent

---

## 10. Execution Directives

When tasked to build this module:

1. Generate UserStory record first.
2. Generate BusinessContext record.
3. Generate BusinessAnalyst class implementing the base Agent interface.
4. Integrate Market Research Tool.
5. Implement validation logic.
6. Ensure output is fully serializable using Jackson.
7. Ensure unsupported claims are rejected.
