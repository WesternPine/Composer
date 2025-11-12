package dev.westernpine.composer.model.predicate;

import dev.westernpine.composer.api.Engine;
import dev.westernpine.composer.api.Payload;
import dev.westernpine.composer.api.Predicate;
import dev.westernpine.composer.api.PredicateFactory;
import dev.westernpine.composer.model.workflow.WorkflowPredicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public final class OrPredicate implements Predicate {

    private static final Logger LOGGER = LoggerFactory.getLogger(OrPredicate.class);

    private final Engine engine;
    private final WorkflowPredicate definition;

    public OrPredicate(Engine engine, WorkflowPredicate definition) {
        this.engine = engine;
        this.definition = definition;
    }

    @Override
    public boolean evaluate(Payload payload) {
        if (definition == null) {
            LOGGER.debug("OrPredicate has no definition; returning false");
            return false;
        }
        List<WorkflowPredicate> innerDefinitions = definition.innerPredicates();
        if (innerDefinitions == null || innerDefinitions.isEmpty()) {
            LOGGER.debug("OrPredicate has no inner predicates; returning false");
            return false;
        }
        PredicateFactory predicateFactory = engine != null ? engine.getPredicateFactory() : null;
        if (predicateFactory == null) {
            LOGGER.warn("OrPredicate cannot evaluate because predicate factory is unavailable");
            return false;
        }
        LOGGER.trace("OrPredicate evaluating {} inner predicates", innerDefinitions.size());
        for (WorkflowPredicate innerDefinition : innerDefinitions) {
            if (innerDefinition == null) {
                LOGGER.debug("OrPredicate encountered null inner predicate; skipping");
                continue;
            }
            boolean matched = predicateFactory
                    .create(innerDefinition)
                    .map(createdPredicate -> createdPredicate.evaluate(payload))
                    .orElse(false);
            LOGGER.debug("OrPredicate evaluated inner predicate '{}' result={}", innerDefinition.id(), matched);
            if (matched) {
                LOGGER.debug("OrPredicate returning true due to predicate '{}'", innerDefinition.id());
                return true;
            }
        }
        LOGGER.debug("OrPredicate returning false after evaluating all predicates");
        return false;
    }
}
