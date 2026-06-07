# Module: Database Architect Agent

This is the third agent in the Chain of Responsibility. Its job is to design the database schema and generate an Entity-Relationship Diagram (ERD) based on the business requirements and user stories.

## 1. Input Specifications
* **Data Source:** `BusinessContext` (containing User Stories and Epics) and `RequirementContext` (for high-level constraints), provided via Mediator.

## 2. Core Logic
* Connect to the `AiService` (Adapter pattern).
* Instruct the LLM to act as a Senior Database Architect.
* Derive entities from:
1. Explicit business objects mentioned in requirements.
2. Entities required to support identified features.
3. Existing constraints from RequirementContext.

Do not create entities that cannot be justified by a requirement or user story.
* Publish state updates ("Designing Schema...", "Generating ERD...") via the `EventLogger` (Observer pattern).


## 2.1 Schema Validation Rules

The Database Architect must:

- Generate only entities supported by requirements.
- Avoid speculative entities.
- Ensure every relationship has a business justification.
- Ensure every table has a primary key.
- Resolve many-to-many relationships using junction tables when appropriate.
- Prefer normalized schema design (up to 3NF unless requirements dictate otherwise).
- Flag ambiguities instead of inventing assumptions.

The Mermaid ERD is a secondary visualization.
Tables and relationships are the authoritative schema definition.

## 3. Output Specifications
* The LLM response MUST be parsed into a Java Record named `DatabaseContext`.
* `DatabaseContext` must contain:
```java
public record DatabaseContext(
    List<Table> tables,
    List<Relationship> relationships,
    String mermaidErdChart
) {}

public record Table(
    String name,
    String description,
    List<Column> columns
) {}

public record Column(
    String name,
    String dataType,
    boolean isPrimaryKey,
    boolean isForeignKey,
    boolean isNullable
) {}

public record Relationship(
    String fromTable,
    String toTable,
    String cardinality,
    String justification
) {}
```
* This record is passed to the `CentralOrchestrator` (Mediator) to be routed to the Project Planner.

## 4. Execution Directives
When tasked to build this module:
1. Generate the `DatabaseContext`, `Table`, `Column`, and `Relationship` Records first.
2. Generate the `DatabaseArchitect` class implementing the base Agent interface.
3. The Mermaid ERD is a derived artifact generated from the schema.
The structured schema remains the authoritative source.

### Restrictions:

- Do not invent tables without justification.
- Do not create assumed business domains.
- Every table must be traceable to at least one requirement or user story.
- If information is insufficient, mark the entity as "Needs Clarification" instead of inventing a solution.