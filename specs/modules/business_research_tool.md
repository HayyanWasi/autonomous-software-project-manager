# Module: Market Research Tool

This tool is used exclusively by the Business Analyst Agent.

Its responsibility is to retrieve real, external market data to support evidence-based business analysis.
It prevents hallucination by enforcing a strict boundary: if evidence is not found, it says so explicitly.

---

## 1. Tool Identity

| Property    | Value                              |
|-------------|------------------------------------|
| Name        | MarketResearchTool                 |
| Used By     | Business Analyst Agent only        |
| Type        | External API Tool                  |
| Cost        | Free (Tavily free tier)            |
| Pattern     | Adapter (wraps Tavily REST API)    |

---

## 2. Why Tavily

Tavily Search API is built specifically for AI agents.

Unlike general search APIs, Tavily:
- Returns clean, pre-extracted text — not raw HTML
- Is optimized for LLM consumption
- Provides relevance scoring per result
- Offers a free tier of 1000 searches/month — enough for any SDA demo
- Requires only a free account at tavily.com — no credit card

**Signup:** https://tavily.com  
**Free tier:** 1000 API calls/month  
**API key format:** `tvly-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx`

---

## 3. Configuration

Add to `application.properties`:

```properties
# Tavily Search API (free tier)
tavily.api-key=tvly-your-key-here
tavily.base-url=https://api.tavily.com
tavily.max-results=5
tavily.search-depth=basic
```

Add to `pom.xml` — no extra dependency needed.  
Tavily uses a plain REST API. Spring's `RestTemplate` is sufficient.

---

## 4. API Contract

### Endpoint

```
POST https://api.tavily.com/search
Content-Type: application/json
```

### Request Body

```json
{
  "api_key": "tvly-your-key-here",
  "query": "food delivery app user complaints 2024",
  "search_depth": "basic",
  "max_results": 5,
  "include_answer": true,
  "include_raw_content": false
}
```

### Response Structure

```json
{
  "answer": "A concise AI-generated summary of search results",
  "results": [
    {
      "title": "Page title",
      "url": "https://...",
      "content": "Extracted page text snippet",
      "score": 0.91
    }
  ]
}
```

### What the Tool Uses

- `answer` field — primary source (pre-summarized by Tavily)
- `results[].content` — fallback if answer is empty
- `results[].score` — used to filter low-relevance results (threshold: 0.5)

---

## 5. Search Strategy

The BA Agent calls `researchDomain(domain)` which runs 4 targeted queries:

| Query Template                        | Purpose                            |
|---------------------------------------|------------------------------------|
| `{domain} user complaints`            | Identify pain points               |
| `{domain} competitor weaknesses`      | Competitor gap analysis            |
| `{domain} market trends 2024`         | Industry context                   |
| `{domain} popular features`           | Feature recommendation evidence    |

Each query result is labeled and combined into a single research string.
This combined string is injected into the BA Agent's LLM prompt.

---

## 6. Hallucination Prevention Contract

This is the most important section.

The tool enforces a hard rule:

> If Tavily returns no usable result for a query,  
> the tool returns the exact string `"Insufficient Market Evidence"`  
> for that query.

The BA Agent's validator then enforces:

- `competitorInsights` must be empty OR contain `"Insufficient Market Evidence"` if research was unavailable
- `marketPainPoints` must be empty OR contain `"Insufficient Market Evidence"` if research was unavailable

This means: **the LLM is never asked to invent data**.  
If real data does not exist, the field is explicitly marked — not fabricated.

---

## 7. Java Implementation Contract

### Interface

The tool does NOT implement `AiService`.  
It is a standalone `@Component` injected directly into `BusinessAnalystAgent`.

```java
@Component
public class MarketResearchTool {

    public String search(String query);

    public String researchDomain(String domain);
}
```

### `search(String query)`

- Calls Tavily POST `/search` with the given query
- Returns `answer` field if non-empty
- Falls back to top `results[].content` if answer is empty
- Returns `"Insufficient Market Evidence"` if both are empty or request fails
- Never throws — always returns a safe string

### `researchDomain(String domain)`

- Runs 4 queries using the templates in Section 5
- Combines non-empty results into a labeled string
- Returns `"Insufficient Market Evidence"` if ALL 4 queries return no data
- Returns combined findings otherwise

---

## 8. Error Handling Rules

| Scenario                        | Behaviour                                              |
|---------------------------------|--------------------------------------------------------|
| Tavily is unreachable           | Return `"Insufficient Market Evidence"` — do not crash |
| API key is invalid              | Log warning, return `"Insufficient Market Evidence"`   |
| Rate limit exceeded (429)       | Log warning, return `"Insufficient Market Evidence"`   |
| Empty results                   | Return `"Insufficient Market Evidence"`                |
| Low relevance score (< 0.5)     | Discard result, try next                               |
| All results low relevance       | Return `"Insufficient Market Evidence"`                |

The pipeline must NEVER fail because the research tool failed.  
Market research is an enhancement — not a hard dependency.

---

## 9. Observer Updates

The BA Agent publishes these events during research:

```
"Researching Market Trends..."
"Analyzing Competitors..."
```

The tool itself does not publish events directly.  
Event publishing is the BA Agent's responsibility.

---

## 10. Output Flow

```
BusinessAnalystAgent
  → MarketResearchTool.researchDomain(domain)
      → Tavily API (4 queries)
      → Combined research string
  → Injected into LLM prompt
  → LLM generates BusinessContext using real evidence
```

---

## 11. Security Rules

- The API key must NEVER be hardcoded in source code
- Always load from `application.properties` or environment variable
- For production: use `TAVILY_API_KEY` environment variable
- Never log the API key — not even partially

```properties
# Safe: loaded from environment in production
tavily.api-key=${TAVILY_API_KEY:tvly-dev-key-for-local-only}
```

---

## 12. Execution Directives

When tasked to build this module:

1. Read this document fully before writing any code.
2. Create `MarketResearchTool.java` as a Spring `@Component`.
3. Load all config from `application.properties` via `@Value`.
4. Implement `search()` — single query, safe return.
5. Implement `researchDomain()` — 4 queries, combined result.
6. All HTTP calls via `RestTemplate` — no extra libraries.
7. All errors caught — never propagate exceptions upward.
8. Test with a hardcoded `RequirementContext` for "food delivery app" before integrating.