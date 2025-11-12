package dev.westernpine.composer.runtime.interpreter;

import dev.westernpine.composer.api.*;
import dev.westernpine.composer.model.event.EventKeys;
import dev.westernpine.composer.model.payload.DefaultPayload;
import dev.westernpine.composer.model.payload.PayloadKeys;
import dev.westernpine.composer.model.workflow.Workflow;
import dev.westernpine.composer.model.workflow.WorkflowAction;
import dev.westernpine.composer.model.workflow.WorkflowBinding;
import dev.westernpine.composer.model.workflow.WorkflowPredicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class DefaultInterpreter implements Interpreter {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultInterpreter.class);

    private static final BiConsumer<Workflow, Payload> EVENT_HANDLER_LOGIC = (workflow, payload) -> {
        Engine engine = payload.engine();
        Interpreter interpreter = engine.getInterpreter();
        LOGGER.debug("Handling event for workflow '{}'", workflow.getId());

        List<Predicate> predicates = Optional
                .ofNullable(workflow.getWorkflowPredicates())
                .orElse(List.of())
                .stream()
                .map(interpreter::getPredicate)
                .flatMap(Optional::stream)
                .toList();

        boolean shouldExecute = true;
        for (Predicate predicate : predicates) {
            try {
                boolean result = predicate.evaluate(payload);
                LOGGER.debug(
                        "Predicate {} evaluated to {} for workflow '{}'",
                        predicate.getClass().getName(),
                        result,
                        workflow.getId());
                if (!result) {
                    shouldExecute = false;
                    break;
                }
            } catch (Exception e) {
                LOGGER.error(
                        "Predicate {} threw while evaluating for workflow '{}'",
                        predicate.getClass().getName(),
                        workflow.getId(),
                        e);
                shouldExecute = false;
                break;
            }
        }
        if(!shouldExecute) {
            LOGGER.debug("Skipping workflow '{}' actions because predicate conditions were not met", workflow.getId());
            return;
        }
        List<Action> actions = Optional
                .ofNullable(workflow.getWorkflowActions())
                .orElse(List.of())
                .stream()
                .map(interpreter::getAction)
                .flatMap(Optional::stream)
                .toList();
        actions.forEach(action -> {
            LOGGER.info("Executing workflow '{}' action {}", workflow.getId(), action.getClass().getName());
            try {
                action.execute(payload);
            } catch (Exception e) {
                LOGGER.error(
                        "Action {} threw while executing for workflow '{}'",
                        action.getClass().getName(),
                        workflow.getId(),
                        e);
            }
        });
    };

    private static final Consumer<Payload> WORKFLOW_ADDED_HANDLER = (payload) -> {
        payload.get(PayloadKeys.WORKFLOW_ID, String.class)
                .flatMap(id -> payload.engine().getInterpreter().getWorkflow(id))
                .ifPresent(workflow -> {
                    Engine engine = payload.engine();
                    Interpreter interpreter = engine.getInterpreter();
                    EventBus eventBus = engine.getEventBus();
                    LOGGER.info("Registering bindings for workflow '{}'", workflow.getId());
                    Optional
                            .ofNullable(workflow.getWorkflowBindings())
                            .orElse(List.of())
                            .stream()
                            .map(interpreter::getBinding)
                            .flatMap(Optional::stream)
                            .forEach(binding -> {
                                UUID subscriberId = eventBus.subscribe(
                                        binding.getEvent(),
                                        binding.getPriority(),
                                        binding.ignoreCancelled(),
                                        innerPayload -> EVENT_HANDLER_LOGIC.accept(workflow, innerPayload));
                                LOGGER.debug("Registered binding '{}' for event '{}' with subscriber id {}", binding.getId(), binding.getEvent(), subscriberId);
                                binding.setSubscriberId(subscriberId);
                            });
                });
    };

    private static final Consumer<Payload> WORKFLOW_REMOVED_HANDLER = (payload) -> {
        payload.get(PayloadKeys.WORKFLOW_ID, String.class)
                .flatMap(id -> payload.engine().getInterpreter().getWorkflow(id))
                .ifPresent(workflow -> {
                    Engine engine = payload.engine();
                    Interpreter interpreter = engine.getInterpreter();
                    EventBus eventBus = engine.getEventBus();
                    LOGGER.info("Removing bindings for workflow '{}'", workflow.getId());
                    Optional
                            .ofNullable(workflow.getWorkflowBindings())
                            .orElse(List.of())
                            .stream()
                            .map(interpreter::getBinding)
                            .flatMap(Optional::stream)
                            .forEach(binding -> {
                                LOGGER.debug("Unsubscribing binding '{}' from event '{}'", binding.getId(), binding.getEvent());
                                eventBus.unsubscribe(binding.getEvent(), binding.getSubscriberId());
                            });
                });
    };

    private final Engine engine;

    private final Map<String, Workflow> workflows;

    private final UUID workflowAddedHandler;
    private final UUID workflowRemovedHandler;

    public DefaultInterpreter(Engine engine) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.workflows = new ConcurrentHashMap<>();

        this.workflowAddedHandler = this.engine.getEventBus()
                .subscribe(EventKeys.WORKFLOW_ADDED, Integer.MAX_VALUE, true, WORKFLOW_ADDED_HANDLER);
        LOGGER.debug("Registered workflow added handler with id {}", this.workflowAddedHandler);
        this.workflowRemovedHandler = this.engine.getEventBus()
                .subscribe(EventKeys.WORKFLOW_REMOVED, Integer.MAX_VALUE, true, WORKFLOW_REMOVED_HANDLER);
        LOGGER.debug("Registered workflow removed handler with id {}", this.workflowRemovedHandler);
    }

    @Override
    public Engine getEngine() {
        return engine;
    }

    @Override
    public Collection<Workflow> getWorkflows() {
        return workflows.values();
    }

    @Override
    public Optional<Workflow> getWorkflow(String id){
        LOGGER.trace("Retrieving workflow '{}'", id);
        return Optional.ofNullable(workflows.get(id));
    }

    @Override
    public boolean workflowExists(String id) {
        boolean exists = workflows.containsKey(id);
        LOGGER.trace("Workflow '{}' exists: {}", id, exists);
        return workflows.containsKey(id);
    }

    @Override
    public void addWorkflow(Workflow workflow) {
        LOGGER.info("Adding workflow '{}'", workflow.getId());
        workflows.put(workflow.getId(), workflow);
        DefaultPayload payload = new DefaultPayload(this.engine).with(PayloadKeys.WORKFLOW_ID, workflow.getId());
        this.engine.getEventBus().publish(EventKeys.WORKFLOW_ADDED, payload);
    }

    @Override
    public void removeWorkflow(String id) {
        LOGGER.info("Removing workflow '{}'", id);
        DefaultPayload payload = new DefaultPayload(this.engine).with(PayloadKeys.WORKFLOW_ID, id);
        this.engine.getEventBus().publish(EventKeys.WORKFLOW_REMOVED, payload);
        workflows.remove(id);
    }

    public UUID getWorkflowAddedHandler() {
        return workflowAddedHandler;
    }

    public UUID getWorkflowRemovedHandler() {
        return workflowRemovedHandler;
    }

    @Override
    public Optional<Binding> getBinding(WorkflowBinding binding) {
        if (binding == null) {
            LOGGER.debug("Requested binding creation with null workflow binding");
            return Optional.empty();
        }
        Optional<Binding> created = Optional.ofNullable(engine.getBindingFactory().create(binding));
        if (created.isEmpty()) {
            LOGGER.warn("Binding factory returned empty result for binding '{}'", binding.getId());
        }
        return created;
    }

    @Override
    public Optional<Predicate> getPredicate(WorkflowPredicate predicate) {
        if (predicate == null) {
            LOGGER.debug("Requested predicate creation with null workflow predicate");
            return Optional.empty();
        }
        Optional<Predicate> created = engine.getPredicateFactory().create(predicate);
        if (created.isEmpty()) {
            LOGGER.warn("Predicate factory returned empty result for predicate '{}'", predicate.id());
        }
        return created;
    }

    @Override
    public Optional<Action> getAction(WorkflowAction action) {
        if (action == null) {
            LOGGER.debug("Requested action creation with null workflow action");
            return Optional.empty();
        }
        Optional<Action> created = engine.getActionFactory().create(action);
        if (created.isEmpty()) {
            LOGGER.warn("Action factory returned empty result for action '{}'", action.id());
        }
        return created;
    }
}
