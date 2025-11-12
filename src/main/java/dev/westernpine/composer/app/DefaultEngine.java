package dev.westernpine.composer.app;

import dev.westernpine.composer.api.*;
import dev.westernpine.composer.model.payload.DefaultPayload;
import dev.westernpine.composer.model.payload.PayloadKeys;
import dev.westernpine.composer.model.workflow.Initializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Timer;

public class DefaultEngine implements Engine {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultEngine.class);

    EngineConfig engineConfig;
    Timer timer;
    Resolver resolver;
    EventBus eventBus;
    WorkflowLoaderFactory workflowLoaderFactory;
    Interpreter interpreter;
    BindingFactory bindingFactory;
    PredicateFactory predicateFactory;
    ActionFactory actionFactory;
    Registry registry;

    DefaultEngine(){}

    @Override
    public EngineConfig getConfig(){
        return engineConfig;
    }

    @Override
    public Timer getTimer() {
        return timer;
    }

    @Override
    public Resolver getResolver() {
        return resolver;
    }

    @Override
    public EventBus getEventBus() {
        return eventBus;
    }

    @Override
    public WorkflowLoaderFactory getWorkflowLoaderFactory() {
        return workflowLoaderFactory;
    }

    @Override
    public Interpreter getInterpreter() {
        return interpreter;
    }

    @Override
    public BindingFactory getBindingFactory() {
        return bindingFactory;
    }

    @Override
    public PredicateFactory getPredicateFactory() {
        return predicateFactory;
    }

    @Override
    public ActionFactory getActionFactory() {
        return actionFactory;
    }

    @Override
    public Registry getRegistry() {
        return registry;
    }

    @Override
    public void initialize() {
        if (engineConfig == null || engineConfig.workflowSources() == null) {
            LOGGER.warn("Engine configuration is missing or contains no workflow sources. Skipping initialization.");
            return;
        }

        LOGGER.info("Initializing engine with {} workflow sources", engineConfig.workflowSources().size());
        engineConfig
                .workflowSources()
                .forEach(source -> {
                    if (source == null) {
                        LOGGER.debug("Encountered null workflow source. Skipping initialization for that entry.");
                        return;
                    }

                    LOGGER.info("Initializing workflow source '{}' ", source.id());
                    DefaultPayload payload = new DefaultPayload(this)
                            .with(PayloadKeys.WORKFLOW_SOURCE, source)
                            .with(PayloadKeys.WORKFLOW_ID, source.id());

                    List<Initializer> initializers;
                    try {
                        initializers = getWorkflowLoaderFactory()
                                .get(source.id())
                                .map(WorkflowLoader::getInitializers)
                                .orElse(List.of());
                    } catch (Exception e) {
                        LOGGER.error(
                                "Failed to load initializers for workflow source '{}'", source.id(), e);
                        return;
                    }

                    if (initializers.isEmpty()) {
                        LOGGER.debug("No initializers found for workflow source '{}'.", source.id());
                        return;
                    }

                    initializers
                            .stream()
                            .filter(Objects::nonNull)
                            .forEach(initializer -> {
                                List<Predicate> predicates = Optional
                                        .ofNullable(initializer.conditions())
                                        .orElse(List.of())
                                        .stream()
                                        .map(predicate -> getInterpreter().getPredicate(predicate))
                                        .flatMap(Optional::stream)
                                        .toList();

                                boolean all = true;
                                for (Predicate predicate : predicates) {
                                    try {
                                        boolean result = predicate.evaluate(payload);
                                        LOGGER.debug(
                                                "Predicate {} evaluated to {} for workflow source '{}'",
                                                predicate.getClass().getName(),
                                                result,
                                                source.id());
                                        if (!result) {
                                            all = false;
                                            break;
                                        }
                                    } catch (Exception e) {
                                        LOGGER.error(
                                                "Predicate {} threw while evaluating for workflow source '{}'",
                                                predicate.getClass().getName(),
                                                source.id(),
                                                e);
                                        all = false;
                                        break;
                                    }
                                }

                                if(!all) {
                                    LOGGER.debug("Initializer conditions not met for workflow source '{}'. Skipping actions.", source.id());
                                    return;
                                }

                                List<Action> actions = Optional
                                        .ofNullable(initializer.actions())
                                        .orElse(List.of())
                                        .stream()
                                        .map(action -> getInterpreter().getAction(action))
                                        .flatMap(Optional::stream)
                                        .toList();

                                actions.forEach(action -> {
                                    LOGGER.info(
                                            "Executing action {} for workflow source '{}'",
                                            action.getClass().getName(),
                                            source.id());
                                    try {
                                        action.execute(payload);
                                    } catch (Exception e) {
                                        LOGGER.error(
                                                "Action {} threw while executing for workflow source '{}'",
                                                action.getClass().getName(),
                                                source.id(),
                                                e);
                                    }
                                });
                            });
                });
        LOGGER.info("Engine initialization complete");
    }
}

