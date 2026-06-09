package com.autonomouspm.controller;

/**
 * Request body for {@code POST /api/pipeline/run}.
 *
 * <p>Spec: {@code specs/modules/central-orchestrator-agent.md §6 File 4}
 *
 * <p>Deserialised from JSON by Jackson, e.g. {@code { "idea": "food delivery app" }}.
 *
 * @param idea the raw project idea string submitted by the user
 */
public record PipelineRequest(String idea) {}
