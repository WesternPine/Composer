package dev.westernpine.composer.runtime.factory.action;

import dev.westernpine.composer.api.Action;
import dev.westernpine.composer.api.ActionFactory;
import dev.westernpine.composer.api.Engine;
import dev.westernpine.composer.api.Resolver;
import dev.westernpine.composer.model.workflow.WorkflowAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class DefaultActionFactory implements ActionFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultActionFactory.class);

    private final Engine engine;
    private final Map<WorkflowAction, Action> actionCache;

    public DefaultActionFactory(Engine engine) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.actionCache = new ConcurrentHashMap<>();
    }

    @Override
    public Optional<Action> create(WorkflowAction workflowAction) {
        WorkflowAction nonNullAction = Objects.requireNonNull(workflowAction, "workflowAction");
        Action cachedAction = actionCache.get(nonNullAction);
        if (cachedAction != null) {
            LOGGER.debug("Returning cached action instance for '{}'", nonNullAction.id());
            return Optional.of(cachedAction);
        }

        Optional<Action> createdAction = instantiateAction(nonNullAction);
        createdAction.ifPresent(action -> {
            LOGGER.info("Caching new action '{}' for workflow '{}'", action.getClass().getName(), nonNullAction.id());
            actionCache.put(nonNullAction, action);
        });
        return createdAction;
    }

    private Optional<Action> instantiateAction(WorkflowAction workflowAction) {
        try {
            Resolver resolver = engine.getResolver();
            Class<?> clazz = resolver.resolve(workflowAction.id());

            if (!Action.class.isAssignableFrom(clazz)) {
                LOGGER.warn("Resolved type '{}' does not implement Action for workflow '{}'", clazz.getName(), workflowAction.id());
                return Optional.empty();
            }

            Constructor<?>[] constructors = clazz.getConstructors();

            for (Constructor<?> constructor : constructors) {
                Class<?>[] parameterTypes = constructor.getParameterTypes();
                if (parameterTypes.length == 2
                        && parameterTypes[0] == Engine.class
                        && WorkflowAction.class.isAssignableFrom(parameterTypes[1])) {
                    LOGGER.debug("Instantiating action '{}' with (Engine, WorkflowAction) constructor", clazz.getName());
                    return Optional.of((Action) constructor.newInstance(engine, workflowAction));
                }
            }

            for (Constructor<?> constructor : constructors) {
                Class<?>[] parameterTypes = constructor.getParameterTypes();
                if (parameterTypes.length == 2
                        && parameterTypes[0] == Engine.class
                        && Map.class.isAssignableFrom(parameterTypes[1])) {
                    Map<String, Object> arguments = workflowAction.args() == null
                            ? Map.of()
                            : workflowAction.args();
                    LOGGER.debug("Instantiating action '{}' with (Engine, Map) constructor", clazz.getName());
                    return Optional.of((Action) constructor.newInstance(engine, arguments));
                }
            }

            for (Constructor<?> constructor : constructors) {
                Class<?>[] parameterTypes = constructor.getParameterTypes();
                if (parameterTypes.length == 1 && parameterTypes[0] == Engine.class) {
                    LOGGER.debug("Instantiating action '{}' with (Engine) constructor", clazz.getName());
                    return Optional.of((Action) constructor.newInstance(engine));
                }
            }

            Constructor<?> noArgsConstructor = clazz.getConstructor();
            LOGGER.debug("Instantiating action '{}' with no-args constructor", clazz.getName());
            return Optional.of((Action) noArgsConstructor.newInstance());
        } catch (NoSuchMethodException ignored) {
            LOGGER.warn("No suitable constructor found when creating action '{}'", workflowAction.id());
            return Optional.empty();
        } catch (Exception e) {
            LOGGER.error("Failed to instantiate action '{}'", workflowAction.id(), e);
            return Optional.empty();
        }
    }
}
