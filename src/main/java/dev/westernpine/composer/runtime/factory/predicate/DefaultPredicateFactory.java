package dev.westernpine.composer.runtime.factory.predicate;

import dev.westernpine.composer.api.Engine;
import dev.westernpine.composer.api.Predicate;
import dev.westernpine.composer.api.PredicateFactory;
import dev.westernpine.composer.api.Resolver;
import dev.westernpine.composer.model.workflow.WorkflowPredicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class DefaultPredicateFactory implements PredicateFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultPredicateFactory.class);

    private final Engine engine;
    private final Map<WorkflowPredicate, Predicate> predicateCache;

    public DefaultPredicateFactory(Engine engine) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.predicateCache = new ConcurrentHashMap<>();
    }

    @Override
    public Optional<Predicate> create(WorkflowPredicate workflowPredicate) {
        WorkflowPredicate nonNullPredicate = Objects.requireNonNull(workflowPredicate, "workflowPredicate");
        Predicate cachedPredicate = predicateCache.get(nonNullPredicate);
        if (cachedPredicate != null) {
            LOGGER.debug("Returning cached predicate instance for '{}'", nonNullPredicate.id());
            return Optional.of(cachedPredicate);
        }

        Optional<Predicate> createdPredicate = instantiatePredicate(nonNullPredicate);
        createdPredicate.ifPresent(predicate -> {
            LOGGER.info("Caching new predicate '{}' for workflow '{}'", predicate.getClass().getName(), nonNullPredicate.id());
            predicateCache.put(nonNullPredicate, predicate);
        });
        return createdPredicate;
    }

    private Optional<Predicate> instantiatePredicate(WorkflowPredicate workflowPredicate) {
        try {
            Resolver resolver = engine.getResolver();
            Class<?> clazz = resolver.resolve(workflowPredicate.id());

            if (!Predicate.class.isAssignableFrom(clazz)) {
                LOGGER.warn("Resolved type '{}' does not implement Predicate for workflow '{}'", clazz.getName(), workflowPredicate.id());
                return Optional.empty();
            }

            Constructor<?>[] constructors = clazz.getConstructors();

            for (Constructor<?> constructor : constructors) {
                Class<?>[] parameterTypes = constructor.getParameterTypes();
                if (parameterTypes.length == 2
                        && parameterTypes[0] == Engine.class
                        && WorkflowPredicate.class.isAssignableFrom(parameterTypes[1])) {
                    LOGGER.debug("Instantiating predicate '{}' with (Engine, WorkflowPredicate) constructor", clazz.getName());
                    return Optional.of((Predicate) constructor.newInstance(engine, workflowPredicate));
                }
            }

            for (Constructor<?> constructor : constructors) {
                Class<?>[] parameterTypes = constructor.getParameterTypes();
                if (parameterTypes.length == 2
                        && parameterTypes[0] == Engine.class
                        && Map.class.isAssignableFrom(parameterTypes[1])) {
                    Map<String, Object> arguments = workflowPredicate.args() == null
                            ? Map.of()
                            : workflowPredicate.args();
                    LOGGER.debug("Instantiating predicate '{}' with (Engine, Map) constructor", clazz.getName());
                    return Optional.of((Predicate) constructor.newInstance(engine, arguments));
                }
            }

            for (Constructor<?> constructor : constructors) {
                Class<?>[] parameterTypes = constructor.getParameterTypes();
                if (parameterTypes.length == 1 && parameterTypes[0] == Engine.class) {
                    LOGGER.debug("Instantiating predicate '{}' with (Engine) constructor", clazz.getName());
                    return Optional.of((Predicate) constructor.newInstance(engine));
                }
            }

            Constructor<?> noArgsConstructor = clazz.getConstructor();
            LOGGER.debug("Instantiating predicate '{}' with no-args constructor", clazz.getName());
            return Optional.of((Predicate) noArgsConstructor.newInstance());
        } catch (NoSuchMethodException ignored) {
            LOGGER.warn("No suitable constructor found when creating predicate '{}'", workflowPredicate.id());
            return Optional.empty();
        } catch (Exception e) {
            LOGGER.error("Failed to instantiate predicate '{}'", workflowPredicate.id(), e);
            return Optional.empty();
        }
    }
}
