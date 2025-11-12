package dev.westernpine.composer.model.action;

import dev.westernpine.composer.api.Action;
import dev.westernpine.composer.api.Engine;
import dev.westernpine.composer.api.Payload;
import dev.westernpine.composer.api.Registry;
import dev.westernpine.composer.model.payload.PayloadKeys;
import dev.westernpine.composer.utilities.ArgsUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Optional;

public final class RegistrySetAction implements Action {

    private static final Logger LOGGER = LoggerFactory.getLogger(RegistrySetAction.class);

    private final Engine engine;
    private final Map<String, Object> arguments;

    public RegistrySetAction(Engine engine, Map<String, Object> arguments) {
        this.engine = engine;
        this.arguments = arguments;
    }

    @Override
    public void execute(Payload payload) {
        if (payload == null) {
            LOGGER.warn("RegistrySetAction executed with null payload");
            return;
        }
        if (payload.isCancelled()) {
            LOGGER.debug("RegistrySetAction aborted because payload is cancelled");
            return;
        }
        Registry registry = engine != null ? engine.getRegistry() : null;
        if (registry == null) {
            LOGGER.warn("RegistrySetAction cannot execute because registry is unavailable");
            return;
        }

        String key = extractKey(payload).orElseGet(() -> ArgsUtility.readString(arguments, "key").orElse(null));
        if (key == null || key.isBlank()) {
            LOGGER.warn("RegistrySetAction requires a non-empty key");
            return;
        }

        Object value = extractValue(payload).orElseGet(() -> Optional.ofNullable(arguments).map(args -> args.get("value")).orElse(null));
        Duration ttl = extractTtl(payload).orElseGet(() -> readDuration(arguments, "ttl").orElse(null));

        if (value == null) {
            LOGGER.info("RegistrySetAction removing key '{}'", key);
            registry.remove(key);
            return;
        }

        if (ttl != null) {
            LOGGER.info("RegistrySetAction setting key '{}' with TTL {}", key, ttl);
            registry.set(key, value, ttl);
        } else {
            LOGGER.info("RegistrySetAction setting key '{}' with no TTL", key);
            registry.set(key, value);
        }
    }

    private Optional<String> extractKey(Payload payload) {
        if (payload == null) {
            return Optional.empty();
        }
        return payload.get(PayloadKeys.REGISTRY_KEY, String.class);
    }

    private Optional<Object> extractValue(Payload payload) {
        if (payload == null) {
            return Optional.empty();
        }
        return payload.get(PayloadKeys.REGISTRY_VALUE);
    }

    private Optional<Duration> extractTtl(Payload payload) {
        if (payload == null) {
            return Optional.empty();
        }
        return payload.get(PayloadKeys.REGISTRY_TTL, Duration.class);
    }

    private Optional<Duration> readDuration(Map<String, Object> args, String key) {
        if (args == null || key == null) {
            return Optional.empty();
        }
        if (!args.containsKey(key)) {
            return Optional.empty();
        }
        Object value = args.get(key);
        return readDuration(value);
    }

    private Optional<Duration> readDuration(Object raw) {
        if (raw == null) {
            return Optional.empty();
        }
        if (raw instanceof Duration duration) {
            if (duration.isZero() || duration.isNegative()) {
                LOGGER.warn("Provided TTL {} is not positive", duration);
                return Optional.empty();
            }
            return Optional.of(duration);
        }
        if (raw instanceof Number number) {
            long millis = number.longValue();
            if (millis <= 0L) {
                LOGGER.warn("Provided numeric TTL {} must be positive", millis);
                return Optional.empty();
            }
            return Optional.of(Duration.ofMillis(millis));
        }
        if (raw instanceof String string) {
            String trimmed = string.trim();
            if (trimmed.isEmpty()) {
                return Optional.empty();
            }
            try {
                Duration parsed = Duration.parse(trimmed);
                if (parsed.isZero() || parsed.isNegative()) {
                    LOGGER.warn("Provided string TTL {} is not positive", trimmed);
                    return Optional.empty();
                }
                return Optional.of(parsed);
            } catch (DateTimeParseException ignored) {
                try {
                    long millis = Long.parseLong(trimmed);
                    if (millis <= 0L) {
                        LOGGER.warn("Provided TTL string {} is not a positive number", trimmed);
                        return Optional.empty();
                    }
                    return Optional.of(Duration.ofMillis(millis));
                } catch (NumberFormatException ignoredNumber) {
                    LOGGER.warn("Unable to parse TTL value '{}'", trimmed);
                    return Optional.empty();
                }
            }
        }
        return Optional.empty();
    }
}
