package dev.westernpine.composer.model.action;

import dev.westernpine.composer.api.Action;
import dev.westernpine.composer.api.Engine;
import dev.westernpine.composer.api.Payload;
import dev.westernpine.composer.api.WorkflowLoader;
import dev.westernpine.composer.model.config.WorkflowSource;
import dev.westernpine.composer.model.payload.PayloadKeys;
import dev.westernpine.composer.model.workflow.Workflow;
import dev.westernpine.composer.model.workflow.WorkflowAction;
import dev.westernpine.composer.utilities.ArgsUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;

public final class ScheduleWorkflowSourceMonitorAction implements Action {

    private static final Logger LOGGER = LoggerFactory.getLogger(ScheduleWorkflowSourceMonitorAction.class);

    private final Engine engine;
    private final WorkflowAction workflowAction;

    public ScheduleWorkflowSourceMonitorAction(Engine engine, WorkflowAction workflowAction) {
        this.engine = engine;
        this.workflowAction = workflowAction;
    }

    @Override
    public void execute(Payload payload) {
        if (payload == null) {
            LOGGER.warn("ScheduleWorkflowSourceMonitorAction executed with null payload");
            return;
        }

        if (payload.isCancelled()) {
            LOGGER.debug("ScheduleWorkflowSourceMonitorAction aborted because payload is cancelled");
            return;
        }

        WorkflowSource source = payload.get(PayloadKeys.WORKFLOW_SOURCE, WorkflowSource.class).orElse(null);
        if (engine == null || source == null) {
            LOGGER.warn("ScheduleWorkflowSourceMonitorAction cannot schedule monitor because engine or source is null");
            return;
        }

        String sourceId = source.id();
        if (sourceId == null || sourceId.isBlank()) {
            LOGGER.warn("ScheduleWorkflowSourceMonitorAction requires a valid source id");
            return;
        }

        Timer timer = engine.getTimer();
        if (timer == null) {
            LOGGER.warn("ScheduleWorkflowSourceMonitorAction cannot schedule monitor because engine timer is unavailable");
            return;
        }

        Long interval = ArgsUtility.read(workflowAction.args(), "interval", Long.class).orElse(5000L);
        LOGGER.info("Scheduling workflow source monitor for '{}' with interval {} ms", sourceId, interval);
        engine.getTimer().schedule(getTimerTask(payload, sourceId), 0L, interval);
    }

    public TimerTask getTimerTask(final Payload payload, final String sourceId) {
        return new TimerTask() {
            @Override
            public void run() {
                Engine engine = payload.engine();
                Optional<WorkflowLoader> loaderOptional = Optional.empty();
                if (engine.getWorkflowLoaderFactory() != null) {
                    loaderOptional = engine.getWorkflowLoaderFactory().get(sourceId);
                }
                if (loaderOptional.isEmpty()) {
                    LOGGER.debug("No workflow loader available for source '{}'. Skipping monitor iteration.", sourceId);
                    return;
                }
                loaderOptional.ifPresent(loader -> {
                    try {
                        LOGGER.trace("Running workflow source monitor iteration for '{}'", sourceId);
                        List<String> sourceWorkflows = loader.getAllSourceWorkflows();
                        List<String> loadedWorkflows = engine.getInterpreter().getWorkflows().stream().map(Workflow::getId).toList();

                        List<String> removedWorkflows = sourceWorkflows.stream().filter(workflow -> !loadedWorkflows.contains(workflow)).toList();
                        List<String> addedWorkflows = loadedWorkflows.stream().filter(workflow -> !sourceWorkflows.contains(workflow)).toList();
                        List<String> updatedWorkflows = sourceWorkflows.stream().filter(workflow -> {
                            // True = version discrepancy.
                            // False = same version.
                            try {
                                if (!loadedWorkflows.contains(workflow)) {
                                    return false;
                                }

                                return loader.getVersion(workflow)
                                        .flatMap(version -> engine.getInterpreter()
                                                .getWorkflow(workflow)
                                                .map(Workflow::getVersion)
                                                .map(currentVersion -> !currentVersion.equals(version)))
                                        .orElse(false);
                            } catch (Exception e) {
                                LOGGER.error("Failed to determine workflow version for '{}'", workflow, e);
                                return false;
                            }
                        }).toList();

                        LOGGER.debug("Workflow monitor for '{}' detected {} removed, {} added, {} updated workflows", sourceId, removedWorkflows.size(), addedWorkflows.size(), updatedWorkflows.size());

                        // remove first
                        for (String workflowId : removedWorkflows) {
                            LOGGER.info("Removing workflow '{}' detected as removed from source '{}'", workflowId, sourceId);
                            engine.getInterpreter().removeWorkflow(workflowId);
                        }

                        // then Load unloaded
                        for (String workflowId : addedWorkflows) {
                            LOGGER.info("Loading new workflow '{}' from source '{}'", workflowId, sourceId);
                            Optional<Workflow> workflow = loader.load(workflowId);
                            workflow.ifPresent(value -> engine.getInterpreter().addWorkflow(value));
                        }

                        // then update? (Remove, then re-load)
                        for(String workflowId : updatedWorkflows) {
                            LOGGER.info("Updating workflow '{}' from source '{}'", workflowId, sourceId);
                            engine.getInterpreter().removeWorkflow(workflowId);

                            Optional<Workflow> workflow = loader.load(workflowId);
                            workflow.ifPresent(value -> engine.getInterpreter().addWorkflow(value));
                        }
                    } catch (Exception e) {
                        LOGGER.error("Error while monitoring workflow source '{}'", sourceId, e);
                    }
                });
            }
        };
    }
}

