package dev.westernpine.composer.model.config;

import dev.westernpine.composer.api.EngineConfig;

import java.util.List;

public record DefaultEngineConfig(String version, List<WorkflowSource> workflowSources) implements EngineConfig {
}
