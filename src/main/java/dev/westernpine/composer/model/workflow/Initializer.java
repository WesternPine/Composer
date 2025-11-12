package dev.westernpine.composer.model.workflow;

import java.util.List;

public record Initializer(List<WorkflowPredicate> conditions, List<WorkflowAction> actions) {
}
