======================================================================
MODULE: TOKEN MANAGEMENT LAYER
======================================================================

You are building a Token Management Layer for a multi-agent 
Java Spring Boot pipeline. This layer must be built BEFORE 
any agent makes an LLM call.

======================================================================
CONFIRMED TECH STACK
======================================================================

- Java 21
- Spring Boot 3.5.0
- LangChain4j 1.0.0-beta3 + GoogleAiGeminiChatModel
- PostgreSQL + Spring Data JPA (already in pom.xml)
- Jackson for serialization
- Lombok + SLF4J
- No new dependencies allowed

======================================================================
WHAT THIS MODULE DOES
======================================================================

1. CACHING
   Before every LLM call, hash the input.
   Check PostgreSQL for a cached response.
   If found, return cached response — zero LLM tokens spent.
   If not found, call LLM, then save response to cache.

2. INPUT STRIPPING
   Each agent receives ONLY the fields it needs.
   Never pass full accumulated context forward.
   Stripping is done by dedicated mapper classes.

3. OUTPUT TOKEN LIMITING
   Each agent has a configured max output token limit.
   Limits are defined in application.properties.
   GeminiAiService reads the limit per agent before calling LLM.

4. PROMPT COMPRESSION
   System prompts must be concise.
   No conversational filler.
   Every word in a prompt must earn its place.

======================================================================
AGENT INPUT STRIPPING RULES
======================================================================

Requirement Analyst  → Input: raw user string only
Business Analyst     → Input: executiveSummary, userRoles, 
                               coreFeatures, constraints, 
                               assumptions, nonFunctionalRequirements
Database Architect   → Input: userStories, coreFeatures, 
                               constraints ONLY
                               (strip marketPainPoints, 
                               competitorInsights, businessSummary)
Project Planner      → Input: tables, relationships, epics ONLY
Cost Estimator       → Input: table count, feature count, 
                               epic count ONLY
Risk Analyst         → Input: full context (last agent — acceptable)

======================================================================
BUILD ORDER — ONE FILE PER TURN, WAIT FOR APPROVAL
======================================================================

Step 1 → LlmCacheEntry.java
         JPA entity. Fields:
         - String inputHash (unique, indexed)
         - String agentName
         - String responseJson
         - LocalDateTime createdAt

Step 2 → LlmCacheRepository.java
         Spring Data JPA repository.
         Method: Optional<LlmCacheEntry> findByInputHash(String hash)

Step 3 → TokenBudgetConfig.java
         @ConfigurationProperties class.
         Reads per-agent max token limits from application.properties.

Step 4 → InputStripper.java
         Static utility class.
         One method per agent:
         - toBusinessAnalystInput(RequirementContext)
         - toDatabaseArchitectInput(BusinessContext)
         - toProjectPlannerInput(DatabaseContext)
         - toCostEstimatorInput(ProjectContext)
         - toRiskAnalystInput(ProjectContext)
         Returns stripped record for each agent.

Step 5 → CachingAiService.java
         Wraps existing AiService with caching logic.
         Pattern: Decorator (wraps AiService interface)
         Logic:
         1. Hash systemPrompt + userPrompt using SHA-256
         2. Check LlmCacheRepository for existing entry
         3. If found: log cache hit, return cached response
         4. If not found: call delegate AiService, save to cache
         5. Return response

Step 6 → Add to application.properties:
         token.budget.requirement-analyst=800
         token.budget.business-analyst=1200
         token.budget.database-architect=1000
         token.budget.project-planner=1500
         token.budget.cost-estimator=600
         token.budget.risk-analyst=800

======================================================================
DATABASE MIGRATION
======================================================================

Create this table in PostgreSQL:

CREATE TABLE llm_cache (
    id BIGSERIAL PRIMARY KEY,
    input_hash VARCHAR(64) UNIQUE NOT NULL,
    agent_name VARCHAR(100) NOT NULL,
    response_json TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_llm_cache_hash ON llm_cache(input_hash);

======================================================================
CACHING RULES
======================================================================

- Hash function: SHA-256 on (systemPrompt + userPrompt)
- Cache is permanent — no expiry during development
- Cache hit must be logged: 
  log.info("Cache hit for agent: {} hash: {}", agentName, hash)
- Cache miss must be logged:
  log.info("Cache miss for agent: {} — calling LLM", agentName)
- Never cache error responses
- Never cache empty responses

======================================================================
STOP CONDITIONS
======================================================================

STOP AND ASK if:
- Any step requires a dependency not in pom.xml
- Any file is not in the build order above
- Behavior for a specific agent input is ambiguous

======================================================================
BEGIN
======================================================================

Confirm you have read all specifications.
Start with Step 1: LlmCacheEntry.java only.
Wait for approval before Step 2.