package dev.westernpine.composer.runtime.factory.loader;

import dev.westernpine.composer.api.Engine;
import dev.westernpine.composer.api.WorkflowLoader;
import dev.westernpine.composer.api.WorkflowLoaderFactory;
import dev.westernpine.composer.model.config.WorkflowSource;
import dev.westernpine.composer.utilities.reflection.ConstructorUtils;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultWorkflowLoaderFactory implements WorkflowLoaderFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(
        DefaultWorkflowLoaderFactory.class
    );

    private final Engine engine;
    private final Map<String, WorkflowLoader> loaders = new HashMap<>();

    public DefaultWorkflowLoaderFactory(Engine engine) {
        this.engine = Objects.requireNonNull(engine, "engine");
    }

    @Override
    public Optional<WorkflowLoader> get(String id) {
        String nonNullId = Objects.requireNonNull(id, "id");
        WorkflowLoader cachedLoader = loaders.get(nonNullId);
        if (cachedLoader != null) {
            LOGGER.debug(
                "Returning cached workflow loader for id '{}'",
                nonNullId
            );
            return Optional.of(cachedLoader);
        }

        return engine
            .getConfig()
            .workflowSources()
            .stream()
            .filter(Objects::nonNull)
            .filter(source -> nonNullId.equals(source.id()))
            .findFirst()
            .flatMap(this::instantiateLoader)
            .map(loader -> {
                LOGGER.info(
                    "Caching new workflow loader '{}' for source id '{}'",
                    loader.getClass().getName(),
                    nonNullId
                );
                loaders.put(nonNullId, loader);
                return loader;
            });
    }

    private Optional<WorkflowLoader> instantiateLoader(WorkflowSource source) {
        try {
            Class<?> sourceClass = Class.forName(source.id());

            if (!WorkflowLoader.class.isAssignableFrom(sourceClass)) {
                LOGGER.warn(
                    "Workflow source '{}' does not implement WorkflowLoader",
                    source.id()
                );
                return Optional.empty();
            }

            Object[] parameters = new Object[] { engine, source };

            LOGGER.debug(
                "Instantiating workflow loader '{}' for source '{}'",
                sourceClass.getName(),
                source.id()
            );
            return ConstructorUtils.createInstance(
                sourceClass,
                parameters,
                Engine.class,
                WorkflowSource.class
            ).map(WorkflowLoader.class::cast);
        } catch (Exception e) {
            LOGGER.error(
                "Failed to instantiate workflow loader for source '{}'",
                source.id(),
                e
            );
            return Optional.empty();
        }
    }
}
