package dev.westernpine.composer.model.workflow;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.List;

public class Workflow {

    @JsonIgnore
    public static final String VALID_NAME_REGEX = "^[a-zA-Z0-9_-]*$";

    private String id;
    private String version;

    private List<WorkflowPredicate> workflowPredicates;
    private List<WorkflowAction> workflowActions;
    private List<WorkflowBinding> workflowBindings;

    public Workflow(String id, String version, List<WorkflowPredicate> workflowPredicates, List<WorkflowAction> workflowActions, List<WorkflowBinding> workflowBindings) {
        this.id = id;
        this.version = version;
        this.workflowPredicates = workflowPredicates;
        this.workflowActions = workflowActions;
        this.workflowBindings = workflowBindings;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public List<WorkflowPredicate> getWorkflowPredicates() {
        return workflowPredicates;
    }

    public void setWorkflowPredicates(List<WorkflowPredicate> workflowPredicates) {
        this.workflowPredicates = workflowPredicates;
    }

    public List<WorkflowAction> getWorkflowActions() {
        return workflowActions;
    }

    public void setWorkflowActions(List<WorkflowAction> workflowActions) {
        this.workflowActions = workflowActions;
    }

    public List<WorkflowBinding> getWorkflowBindings() {
        return workflowBindings;
    }

    public void setWorkflowBindings(List<WorkflowBinding> workflowBindings) {
        this.workflowBindings = workflowBindings;
    }
}
