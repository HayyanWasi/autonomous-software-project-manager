package com.autonomouspm.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Standalone market-research tool used exclusively by the
 * {@code BusinessAnalystAgent}.
 *
 * <p>Spec: {@code specs/modules/business_research_tool.md}
 *
 * <p>Wraps the <a href="https://tavily.com">Tavily Search API</a> (Adapter pattern)
 * behind two safe methods. It is deliberately <em>not</em> an {@code AiService}
 * implementation — it is an external HTTP tool injected directly into the agent.
 *
 * <p><b>Hallucination-prevention contract:</b> every method is guaranteed to
 * return a usable string and never to throw. When Tavily is unreachable, returns
 * nothing, or yields only low-relevance results, the methods return the exact
 * sentinel {@link #INSUFFICIENT_EVIDENCE}. The agent's validator then forbids the
 * LLM from inventing competitor or pain-point data when this sentinel is present.
 *
 * <p><b>Security:</b> the API key is loaded from configuration and is never logged.
 */
@Component
public class MarketResearchTool {

    private static final Logger log = LoggerFactory.getLogger(MarketResearchTool.class);

    /** Exact sentinel returned whenever real evidence cannot be obtained. */
    public static final String INSUFFICIENT_EVIDENCE = "Insufficient Market Evidence";

    /** Relevance threshold below which a Tavily result is discarded. */
    private static final double MIN_RELEVANCE_SCORE = 0.5;

    /** Search query templates run by {@link #researchDomain(String)}. */
    private static final List<QueryTemplate> DOMAIN_QUERIES = List.of(
            new QueryTemplate("PAIN POINTS", "%s user complaints"),
            new QueryTemplate("COMPETITOR WEAKNESSES", "%s competitor weaknesses"),
            new QueryTemplate("MARKET TRENDS", "%s market trends 2024"),
            new QueryTemplate("POPULAR FEATURES", "%s popular features")
    );

    private final RestTemplate restTemplate;
    private final String apiKey;
    private final String baseUrl;
    private final int maxResults;
    private final String searchDepth;

    /**
     * @param builder      Spring-auto-configured {@link RestTemplateBuilder}
     * @param apiKey       Tavily API key ({@code tavily.api-key})
     * @param baseUrl      Tavily base URL ({@code tavily.base-url})
     * @param maxResults   maximum results per query ({@code tavily.max-results})
     * @param searchDepth  Tavily search depth ({@code tavily.search-depth})
     */
    public MarketResearchTool(RestTemplateBuilder builder,
                              @Value("${tavily.api-key:}") String apiKey,
                              @Value("${tavily.base-url:https://api.tavily.com}") String baseUrl,
                              @Value("${tavily.max-results:5}") int maxResults,
                              @Value("${tavily.search-depth:basic}") String searchDepth) {
        this.restTemplate = builder
                .connectTimeout(Duration.ofSeconds(10))
                .readTimeout(Duration.ofSeconds(20))
                .build();
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.maxResults = maxResults;
        this.searchDepth = searchDepth;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Runs a single Tavily search and returns the best available text.
     *
     * <p>Resolution order: the pre-summarised {@code answer} field, then the
     * highest-relevance {@code results[].content} snippet (score ≥
     * {@value #MIN_RELEVANCE_SCORE}). Returns {@link #INSUFFICIENT_EVIDENCE} when
     * nothing usable is found or the request fails. Never throws.
     *
     * @param query the search query (may be {@code null} or blank)
     * @return usable research text, or {@link #INSUFFICIENT_EVIDENCE}
     */
    public String search(String query) {
        if (query == null || query.isBlank()) {
            return INSUFFICIENT_EVIDENCE;
        }
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("MarketResearchTool – Tavily API key is not configured; returning insufficient evidence.");
            return INSUFFICIENT_EVIDENCE;
        }

        try {
            TavilyResponse response = restTemplate.postForObject(
                    baseUrl + "/search", buildRequest(query), TavilyResponse.class);

            if (response == null) {
                return INSUFFICIENT_EVIDENCE;
            }
            if (response.answer() != null && !response.answer().isBlank()) {
                return response.answer().trim();
            }

            String snippet = bestSnippet(response.results());
            return snippet != null ? snippet : INSUFFICIENT_EVIDENCE;

        } catch (Exception e) {
            // Market research is an enhancement, never a hard dependency.
            log.warn("MarketResearchTool – Tavily request failed for a query: {}", e.getMessage());
            return INSUFFICIENT_EVIDENCE;
        }
    }

    /**
     * Runs the four domain-research queries (pain points, competitor weaknesses,
     * market trends, popular features) and combines the non-empty findings into a
     * single labelled string for injection into the LLM prompt.
     *
     * @param domain the product domain to research (e.g. "food delivery app")
     * @return combined labelled findings, or {@link #INSUFFICIENT_EVIDENCE} when
     *         all four queries fail
     */
    public String researchDomain(String domain) {
        if (domain == null || domain.isBlank()) {
            return INSUFFICIENT_EVIDENCE;
        }

        StringBuilder combined = new StringBuilder();
        boolean anyFound = false;

        for (QueryTemplate template : DOMAIN_QUERIES) {
            String result = search(template.format(domain));
            if (!INSUFFICIENT_EVIDENCE.equals(result)) {
                anyFound = true;
                combined.append("### ").append(template.label()).append('\n')
                        .append(result).append("\n\n");
            }
        }

        return anyFound ? combined.toString().trim() : INSUFFICIENT_EVIDENCE;
    }

    // -------------------------------------------------------------------------
    // Internals
    // -------------------------------------------------------------------------

    /** Builds the Tavily POST request entity with JSON headers and body. */
    private HttpEntity<Map<String, Object>> buildRequest(String query) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("api_key", apiKey);
        body.put("query", query);
        body.put("search_depth", searchDepth);
        body.put("max_results", maxResults);
        body.put("include_answer", true);
        body.put("include_raw_content", false);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    /**
     * Returns the highest-relevance, sufficiently-scored snippet, or {@code null}
     * when every result is missing content or below the relevance threshold.
     */
    private String bestSnippet(List<TavilyResult> results) {
        if (results == null || results.isEmpty()) {
            return null;
        }
        List<TavilyResult> ranked = new ArrayList<>(results);
        ranked.sort((a, b) -> Double.compare(b.score(), a.score()));
        for (TavilyResult result : ranked) {
            if (result.score() >= MIN_RELEVANCE_SCORE
                    && result.content() != null && !result.content().isBlank()) {
                return result.content().trim();
            }
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Supporting types
    // -------------------------------------------------------------------------

    /** A labelled query template; {@link #format(String)} injects the domain. */
    private record QueryTemplate(String label, String pattern) {
        String format(String domain) {
            return pattern.formatted(domain);
        }
    }

    /** Subset of the Tavily {@code /search} response consumed by this tool. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TavilyResponse(
            String answer,
            List<TavilyResult> results
    ) {}

    /** A single Tavily search result. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TavilyResult(
            String title,
            String url,
            String content,
            @JsonProperty("score") double score
    ) {}
}
