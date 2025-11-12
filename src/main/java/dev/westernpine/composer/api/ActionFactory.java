package dev.westernpine.composer.api;

import dev.westernpine.composer.model.workflow.WorkflowAction;

import java.util.Optional;

public interface ActionFactory {

    Optional<Action> create(WorkflowAction workflowAction);
}
