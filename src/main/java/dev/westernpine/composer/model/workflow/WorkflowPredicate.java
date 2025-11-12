package dev.westernpine.composer.model.workflow;

import java.util.List;
import java.util.Map;

public record WorkflowPredicate(String id, Map<String, Object> args, List<WorkflowPredicate> innerPredicates) {
}
