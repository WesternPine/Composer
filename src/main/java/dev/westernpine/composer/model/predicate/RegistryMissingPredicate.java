package dev.westernpine.composer.model.predicate;

import dev.westernpine.composer.api.Engine;
import dev.westernpine.composer.api.Payload;
import dev.westernpine.composer.api.Predicate;
import dev.westernpine.composer.api.Registry;
import dev.westernpine.composer.model.payload.PayloadKeys;
import dev.westernpine.composer.utilities.ArgsUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;

public final class RegistryMissingPredicate implements Predicate {

    private static final Logger LOGGER = LoggerFactory.getLogger(RegistryMissingPredicate.class);

    private final Engine engine;
    private final Map<String, Object> arguments;

    public RegistryMissingPredicate(Engine engine, Map<String, Object> arguments) {
        this.engine = engine;
        this.arguments = arguments;
    }

    @Override
    public boolean evaluate(Payload payload) {
        if (payload != null && payload.isCancelled()) {
            LOGGER.debug("RegistryMissingPredicate returning false because payload is cancelled");
            return false;
        }
        Registry registry = engine != null ? engine.getRegistry() : null;
        if (registry == null) {
            LOGGER.warn("RegistryMissingPredicate cannot evaluate because registry is unavailable");
            return false;
        }

        String key = extractKey(payload).orElseGet(() -> ArgsUtility.readString(arguments, "key").orElse(null));
        if (key == null || key.isBlank()) {
            LOGGER.warn("RegistryMissingPredicate requires a non-empty key");
            return false;
        }

        boolean missing = registry.get(key).isEmpty();
        LOGGER.debug("RegistryMissingPredicate evaluated key '{}' missing={}", key, missing);
        return missing;
    }

    private Optional<String> extractKey(Payload payload) {
        if (payload == null) {
            return Optional.empty();
        }
        return payload.get(PayloadKeys.REGISTRY_KEY, String.class);
    }
}
