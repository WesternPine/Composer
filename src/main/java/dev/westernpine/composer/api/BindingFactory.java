package dev.westernpine.composer.api;

import dev.westernpine.composer.model.workflow.WorkflowBinding;

public interface BindingFactory {

    public Binding create(WorkflowBinding workflowBinding);

}
