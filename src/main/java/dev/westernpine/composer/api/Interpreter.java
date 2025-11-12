package dev.westernpine.composer.api;

import dev.westernpine.composer.model.workflow.Workflow;
import dev.westernpine.composer.model.workflow.WorkflowAction;
import dev.westernpine.composer.model.workflow.WorkflowBinding;
import dev.westernpine.composer.model.workflow.WorkflowPredicate;

import java.util.Collection;
import java.util.Optional;

public interface Interpreter {

    Engine getEngine();

    Collection<Workflow> getWorkflows();

    Optional<Workflow> getWorkflow(String id);

    boolean workflowExists(String id);

    void addWorkflow(Workflow workflow);

    void removeWorkflow(String id);

    Optional<Binding> getBinding(WorkflowBinding binding);

    Optional<Predicate> getPredicate(WorkflowPredicate predicate);

    Optional<Action> getAction(WorkflowAction action);
}
