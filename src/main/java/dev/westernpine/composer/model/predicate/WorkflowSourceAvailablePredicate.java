package dev.westernpine.composer.model.predicate;

import dev.westernpine.composer.api.Engine;
import dev.westernpine.composer.api.Payload;
import dev.westernpine.composer.api.Predicate;
import dev.westernpine.composer.api.WorkflowLoader;
import dev.westernpine.composer.model.config.WorkflowSource;
import dev.westernpine.composer.model.payload.PayloadKeys;
import dev.westernpine.composer.model.workflow.WorkflowPredicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public final class WorkflowSourceAvailablePredicate implements Predicate {

    private static final Logger LOGGER = LoggerFactory.getLogger(WorkflowSourceAvailablePredicate.class);

    private final Engine engine;

    @SuppressWarnings("unused")
    private final WorkflowPredicate definition;

    public WorkflowSourceAvailablePredicate(Engine engine, WorkflowPredicate definition) {
        this.engine = engine;
        this.definition = definition;
    }

    @Override
    public boolean evaluate(Payload payload) {
        if (payload == null) {
            LOGGER.warn("WorkflowSourceAvailablePredicate evaluated with null payload");
            return false;
        }

        if (payload.isCancelled()) {
            LOGGER.debug("WorkflowSourceAvailablePredicate returning false because payload is cancelled");
            return false;
        }

        if (engine == null) {
            LOGGER.warn("WorkflowSourceAvailablePredicate cannot evaluate because engine is null");
            return false;
        }

        WorkflowSource source = payload.get(PayloadKeys.WORKFLOW_SOURCE, WorkflowSource.class).orElse(null);
        if (source == null) {
            LOGGER.warn("WorkflowSourceAvailablePredicate could not retrieve workflow source from payload");
            return false;
        }

        String sourceId = source.id();
        if (sourceId == null || sourceId.isBlank()) {
            LOGGER.warn("WorkflowSourceAvailablePredicate requires a valid source id");
            return false;
        }

        Optional<WorkflowLoader> loaderOptional = Optional.empty();
        if (engine.getWorkflowLoaderFactory() != null) {
            loaderOptional = engine.getWorkflowLoaderFactory().get(sourceId);
        }
        if (loaderOptional.isEmpty()) {
            LOGGER.info("No workflow loader available for source '{}'. Predicate returns false.", sourceId);
            return false;
        }

        LOGGER.debug("Workflow source '{}' is available", sourceId);
        return true;
    }
}

