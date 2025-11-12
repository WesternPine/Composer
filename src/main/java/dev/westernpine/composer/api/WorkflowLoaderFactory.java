package dev.westernpine.composer.api;

import java.util.Optional;

public interface WorkflowLoaderFactory {

    public Optional<WorkflowLoader> get(String id);

}
