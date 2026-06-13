package com.autonomouspm.observer;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Central event-logging facility implementing the <b>Observer</b> pattern.
 *
 * <p>Acts as both:
 * <ol>
 *   <li><b>Publisher (Subject)</b> — agents and the orchestrator call
 *       {@link #publish(String, EventType, String)} to broadcast events.</li>
 *   <li><b>Observer aggregator</b> — holds a registry of {@link PipelineEventListener}s
 *       that are notified on every published event.</li>
 * </ol>
 *
 * <p>Registered as a Spring {@code @Component} so it can be injected into any
 * agent or service via constructor injection.
 *
 * <p>Every event is also written to SLF4J at INFO level, providing a built-in
 * audit trail with no extra configuration.
 *
 * <p><b>Thread-safety:</b> The listener list uses {@link CopyOnWriteArrayList}
 * to allow safe registration/deregistration during pipeline execution.
 */
@Component
public class EventLogger {

    private static final Logger log = LoggerFactory.getLogger(EventLogger.class);

    private final List<PipelineEventListener> listeners = new CopyOnWriteArrayList<>();

    // -------------------------------------------------------------------------
    // Listener registration
    // -------------------------------------------------------------------------

    /**
     * Registers a listener to receive all future events.
     *
     * @param listener the observer to register
     */
    public void register(PipelineEventListener listener) {
        listeners.add(listener);
        log.debug("EventLogger – registered listener: {}", listener.getClass().getSimpleName());
    }

    /**
     * Removes a previously registered listener.
     *
     * @param listener the observer to deregister
     */
    public void deregister(PipelineEventListener listener) {
        listeners.remove(listener);
        log.debug("EventLogger – deregistered listener: {}", listener.getClass().getSimpleName());
    }

    // -------------------------------------------------------------------------
    // Event publishing
    // -------------------------------------------------------------------------

    /**
     * Publishes a pipeline event to all registered listeners and to the SLF4J log.
     *
     * @param source  the name of the agent or component publishing the event
     * @param type    the event type (see {@link EventType})
     * @param message a human-readable message (e.g. "Generating ERD...")
     */
    public void publish(String source, EventType type, String message) {
        PipelineEvent event = new PipelineEvent(source, type, message, Instant.now());
        log.info("[{}] {} – {}", source, type, message);
        for (PipelineEventListener listener : listeners) {
            try {
                listener.onEvent(event);
            } catch (Exception ex) {
                // A failing listener must never abort the pipeline.
                log.warn("EventLogger – listener {} threw an exception on event {}: {}",
                        listener.getClass().getSimpleName(), type, ex.getMessage(), ex);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Supporting types
    // -------------------------------------------------------------------------

    /**
     * Immutable value object representing a single pipeline event.
     *
     * @param source      name of the publishing component
     * @param type        categorised event type
     * @param message     human-readable description
     * @param occurredAt  wall-clock timestamp
     */
    public record PipelineEvent(
            String source,
            EventType type,
            String message,
            Instant occurredAt
    ) {}

    /**
     * Functional interface for pipeline event listeners (Observer).
     *
     * <p>Implementations must be side-effect-safe: they must not throw exceptions
     * that propagate back to the publisher. Any thrown exception will be caught and
     * logged by {@link EventLogger} rather than aborting the pipeline.
     */
    @FunctionalInterface
    public interface PipelineEventListener {

        /**
         * Called whenever a new {@link PipelineEvent} is published.
         *
         * @param event the published event
         */
        void onEvent(PipelineEvent event);
    }
}
