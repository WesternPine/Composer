package dev.westernpine.composer.api;

import dev.westernpine.composer.model.config.WorkflowSource;

import java.util.List;

public interface EngineConfig {

    List<WorkflowSource> workflowSources();

}
