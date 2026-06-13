package com.autonomouspm.controller;

import com.autonomouspm.core.CentralOrchestrator;
import com.autonomouspm.core.ProjectState;
import com.autonomouspm.core.ProjectStateEntity;
import com.autonomouspm.core.ProjectStateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * REST controller that exposes the agent pipeline to the React front-end.
 *
 * <p>Spec: {@code specs/modules/central-orchestrator-agent.md §6 File 4}
 *
 * <p>Two endpoints:
 * <ul>
 *   <li>{@code POST /api/pipeline/run} — accepts a JSON body with the user's
 *       project idea, spins up the full agent pipeline in a background thread, and
 *       returns an {@link SseEmitter} so the React client receives one SSE event per
 *       completed agent in real-time.</li>
 *   <li>{@code GET /api/pipeline/{pipelineId}} — retrieves a previously-persisted
 *       {@link ProjectState} from PostgreSQL so the client can reload a past run.</li>
 * </ul>
 *
 * <p><b>Threading:</b> The pipeline is always executed on a background thread via
 * {@link ExecutorService}. The HTTP thread returns the {@code SseEmitter} immediately
 * and is never blocked.
 *
 * <p><b>Timeout:</b> The emitter is configured with a 5-minute (300 000 ms) timeout
 * to accommodate large projects whose five agents may each take up to 60 seconds.
 */
@RestController
@RequestMapping("/api/pipeline")
@CrossOrigin(origins = "*") // React dev server
public class PipelineController {

    private static final Logger log = LoggerFactory.getLogger(PipelineController.class);

    /** Five-minute timeout for the SSE connection (spec §6). */
    private static final long SSE_TIMEOUT_MS = 300_000L;

    private final CentralOrchestrator orchestrator;
    private final ProjectStateRepository stateRepository;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public PipelineController(CentralOrchestrator orchestrator,
                              ProjectStateRepository stateRepository) {
        this.orchestrator = orchestrator;
        this.stateRepository = stateRepository;
    }

    // -------------------------------------------------------------------------
    // POST /api/pipeline/run
    // -------------------------------------------------------------------------

    /**
     * Starts a new pipeline run and streams agent results as SSE events.
     *
     * <p>Request body example: {@code { "idea": "food delivery app" }}
     *
     * <p>The returned {@link SseEmitter} will emit the following named events as
     * each agent completes:
     * <ol>
     *   <li>{@code requirement-complete}</li>
     *   <li>{@code business-complete}</li>
     *   <li>{@code database-complete}</li>
     *   <li>{@code planner-complete}</li>
     *   <li>{@code risk-complete}</li>
     *   <li>{@code pipeline-complete} (terminal — includes the final report)</li>
     * </ol>
     * On failure, a {@code pipeline-failed} event is sent instead.
     *
     * @param request the pipeline request containing the user's project idea
     * @return an {@link SseEmitter} connected to the running pipeline
     */
    @PostMapping(value = "/run", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter runPipeline(@RequestBody PipelineRequest request) {
        log.info("PipelineController – received pipeline run request: idea='{}'",
                truncate(request.idea(), 100));

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        // Lifecycle callbacks — log but never throw.
        emitter.onCompletion(() ->
                log.info("PipelineController – SSE emitter completed."));
        emitter.onTimeout(() ->
                log.warn("PipelineController – SSE emitter timed out after {} ms.", SSE_TIMEOUT_MS));
        emitter.onError(ex ->
                log.error("PipelineController – SSE emitter error: {}", ex.getMessage()));

        // Run the full agent pipeline in a background thread — never block the
        // HTTP thread (spec §6 hard restriction).
        executor.submit(() -> {
            try {
                orchestrator.runPipeline(request.idea(), emitter);
            } catch (Exception e) {
                // Safety net: CentralOrchestrator.runPipeline never throws by
                // contract, but if something truly unexpected happens, complete
                // the emitter with an error so the client is not left hanging.
                log.error("PipelineController – unexpected error from orchestrator: {}",
                        e.getMessage(), e);
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    // -------------------------------------------------------------------------
    // GET /api/pipeline/{pipelineId}
    // -------------------------------------------------------------------------

    /**
     * Retrieves a previously-persisted pipeline result from PostgreSQL.
     *
     * @param pipelineId the unique pipeline run identifier
     * @return the reconstructed {@link ProjectState}, or 404 if not found
     */
    @GetMapping("/{pipelineId}")
    public ResponseEntity<ProjectState> getPipeline(@PathVariable String pipelineId) {
        log.debug("PipelineController – fetching pipeline state for id={}", pipelineId);

        return stateRepository.findById(pipelineId)
                .map(ProjectStateEntity::toState)
                .map(ResponseEntity::ok)
                .orElseGet(() -> {
                    log.warn("PipelineController – pipeline not found: {}", pipelineId);
                    return ResponseEntity.notFound().build();
                });
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Truncates a string for safe log output.
     */
    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return "<null>";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength) + "…";
    }
}
