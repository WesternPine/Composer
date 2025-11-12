package dev.westernpine.composer.api;

import dev.westernpine.composer.model.workflow.WorkflowPredicate;

import java.util.Optional;

public interface PredicateFactory {

    Optional<Predicate> create(WorkflowPredicate workflowPredicate);

}
