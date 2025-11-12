package dev.westernpine.composer.model.predicate;

import dev.westernpine.composer.api.Engine;
import dev.westernpine.composer.api.Payload;
import dev.westernpine.composer.api.Predicate;
import dev.westernpine.composer.api.PredicateFactory;
import dev.westernpine.composer.model.workflow.WorkflowPredicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public final class AllPredicate implements Predicate {

    private static final Logger LOGGER = LoggerFactory.getLogger(AllPredicate.class);

    private final Engine engine;
    private final WorkflowPredicate definition;

    public AllPredicate(Engine engine, WorkflowPredicate definition) {
        this.engine = engine;
        this.definition = definition;
    }

    @Override
    public boolean evaluate(Payload payload) {
        if (definition == null) {
            LOGGER.debug("AllPredicate has no definition; returning true");
            return true;
        }
        List<WorkflowPredicate> innerDefinitions = definition.innerPredicates();
        if (innerDefinitions == null || innerDefinitions.isEmpty()) {
            LOGGER.debug("AllPredicate has no inner predicates; returning true");
            return true;
        }
        PredicateFactory predicateFactory = engine != null ? engine.getPredicateFactory() : null;
        if (predicateFactory == null) {
            LOGGER.warn("AllPredicate cannot evaluate because predicate factory is unavailable");
            return true;
        }
        LOGGER.trace("AllPredicate evaluating {} inner predicates", innerDefinitions.size());
        for (WorkflowPredicate innerDefinition : innerDefinitions) {
            if (innerDefinition == null) {
                LOGGER.debug("AllPredicate encountered null inner predicate; skipping");
                continue;
            }
            boolean matches = predicateFactory
                    .create(innerDefinition)
                    .map(createdPredicate -> createdPredicate.evaluate(payload))
                    .orElse(true);
            LOGGER.debug("AllPredicate evaluated inner predicate '{}' result={}", innerDefinition.id(), matches);
            if (!matches) {
                LOGGER.debug("AllPredicate returning false due to predicate '{}'", innerDefinition.id());
                return false;
            }
        }
        LOGGER.debug("AllPredicate returning true after evaluating all predicates");
        return true;
    }
}
