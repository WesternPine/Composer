package dev.westernpine.composer.model.action;

import dev.westernpine.composer.api.Action;
import dev.westernpine.composer.api.Engine;
import dev.westernpine.composer.api.Payload;
import dev.westernpine.composer.api.Registry;
import dev.westernpine.composer.model.payload.PayloadKeys;
import dev.westernpine.composer.utilities.ArgsUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;

public final class RegistryRemoveAction implements Action {

    private static final Logger LOGGER = LoggerFactory.getLogger(RegistryRemoveAction.class);

    private final Engine engine;
    private final Map<String, Object> arguments;

    public RegistryRemoveAction(Engine engine, Map<String, Object> arguments) {
        this.engine = engine;
        this.arguments = arguments;
    }

    @Override
    public void execute(Payload payload) {
        if (payload == null) {
            LOGGER.warn("RegistryRemoveAction executed with null payload");
            return;
        }
        if (payload.isCancelled()) {
            LOGGER.debug("RegistryRemoveAction aborted because payload is cancelled");
            return;
        }
        Registry registry = engine != null ? engine.getRegistry() : null;
        if (registry == null) {
            LOGGER.warn("RegistryRemoveAction cannot execute because registry is unavailable");
            return;
        }

        String key = extractKey(payload).orElseGet(() -> ArgsUtility.readString(arguments, "key").orElse(null));
        if (key == null || key.isBlank()) {
            LOGGER.warn("RegistryRemoveAction requires a non-empty key");
            return;
        }

        LOGGER.info("Removing registry key '{}' via RegistryRemoveAction", key);
        registry.remove(key);
    }

    private Optional<String> extractKey(Payload payload) {
        if (payload == null) {
            return Optional.empty();
        }
        return payload.get(PayloadKeys.REGISTRY_KEY, String.class);
    }
}
