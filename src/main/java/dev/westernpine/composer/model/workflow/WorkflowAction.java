package dev.westernpine.composer.model.workflow;

import java.util.Map;

public record WorkflowAction(String id, Map<String, Object> args) {
}