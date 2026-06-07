# Required Design Patterns Implementation Guide

This project strictly requires the use of 10 specific design patterns. Do not invent alternative implementations or force these patterns where they do not belong. Apply them exactly as mapped below:

1. **Singleton:** Used for `OpenRouterConnectionManager`. Ensures only one instance manages API keys and connection pooling.
2. **Factory Method:** Used for `AgentFactory`. Instantiates specific agent implementations (Requirement Analyst, Risk Analyst, etc.) based on the pipeline stage.
3. **Builder:** Used for `ProjectReportBuilder`. Compiles the final output step-by-step as each agent finishes its section.
4. **Chain of Responsibility:** Defines the core execution pipeline. `RequirementAgent` -> `BusinessAgent` -> `DatabaseAgent`, etc. Each agent processes the context and passes it to the next.
5. **Mediator:** Used for `CentralOrchestrator`. Prevents agents from calling each other directly. Agents report completion to the Mediator, which triggers the next step.
6. **Observer:** Used for `EventLogger` and UI updates. Agents publish state changes ("Evaluating risk...", "Generating ERD..."), and observers listen to these events.
7. **Adapter:** Used to wrap the LangChain4j OpenRouter calls. Creates a clean `AiService` interface that our agents use, shielding them from external library specifics.
8. **Bridge:** Separates the `AgentTask` abstraction from its execution mechanism. Allows swapping between an LLM-based execution and a rule-based execution without changing the agent.
9. **Composite:** Used by the `ProjectPlanner` agent to build the Gantt chart breakdown. A task can be a single leaf or a composite containing sub-tasks.
10. **Null Object:** Used for `EmptyReportSection`. When an AI call fails or hallucinates unparseable data, return this instead of null to keep the Builder pattern from crashing.