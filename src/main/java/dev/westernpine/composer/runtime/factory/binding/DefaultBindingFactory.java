package dev.westernpine.composer.runtime.factory.binding;

import dev.westernpine.composer.api.Binding;
import dev.westernpine.composer.api.BindingFactory;
import dev.westernpine.composer.model.workflow.WorkflowBinding;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

public class DefaultBindingFactory implements BindingFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultBindingFactory.class);

    @Override
    public Binding create(WorkflowBinding workflowBinding) {
        WorkflowBinding binding = Objects.requireNonNull(workflowBinding, "workflowBinding");
        LOGGER.debug("Providing workflow binding '{}'", binding.getId());
        return binding; // WorkflowBinding already implements Binding, and is not reliant on any other class to be class-loaded.
    }
}
