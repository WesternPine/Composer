package dev.westernpine.composer.api;

import dev.westernpine.composer.model.workflow.Workflow;
import dev.westernpine.composer.model.workflow.Initializer;

import java.util.Optional;
import java.util.List;

public interface WorkflowLoader {

    public Optional<Workflow> load(String id) throws Exception;

    public void save(Workflow workflow) throws Exception;

    public Optional<String> getVersion(String id) throws Exception;

    public List<String> getAllSourceWorkflows() throws Exception;

    default List<Initializer> getInitializers() {
        return List.of();
    }

}
