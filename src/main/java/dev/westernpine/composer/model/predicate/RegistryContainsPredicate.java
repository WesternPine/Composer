package dev.westernpine.composer.model.predicate;

import dev.westernpine.composer.api.Engine;
import dev.westernpine.composer.api.Payload;
import dev.westernpine.composer.api.Predicate;
import dev.westernpine.composer.api.Registry;
import dev.westernpine.composer.api.Resolver;
import dev.westernpine.composer.model.payload.PayloadKeys;
import dev.westernpine.composer.utilities.ArgsUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class RegistryContainsPredicate implements Predicate {

    private static final Logger LOGGER = LoggerFactory.getLogger(RegistryContainsPredicate.class);

    private final Engine engine;
    private final Map<String, Object> arguments;

    public RegistryContainsPredicate(Engine engine, Map<String, Object> arguments) {
        this.engine = engine;
        this.arguments = arguments;
    }

    @Override
    public boolean evaluate(Payload payload) {
        if (payload != null && payload.isCancelled()) {
            LOGGER.debug("RegistryContainsPredicate returning false because payload is cancelled");
            return false;
        }
        Registry registry = engine != null ? engine.getRegistry() : null;
        if (registry == null) {
            LOGGER.warn("RegistryContainsPredicate cannot evaluate because registry is unavailable");
            return false;
        }

        Optional<String> keyOptional = extractKey(payload)
                .or(() -> ArgsUtility.readString(arguments, "key"));
        if (keyOptional.isEmpty() || keyOptional.filter(String::isBlank).isPresent()) {
            LOGGER.warn("RegistryContainsPredicate requires a non-empty key");
            return false;
        }
        String key = keyOptional.orElseThrow();

        Optional<Class<?>> requestedType = resolveRequestedType();
        Optional<Object> stored = requestedType
                .flatMap(type -> registry.get(key, type).map(Object.class::cast))
                .or(() -> registry.get(key));
        if (stored.isEmpty()) {
            LOGGER.debug("Registry key '{}' is absent", key);
            return false;
        }

        Object actual = stored.get();
        Optional<Object> expected = extractExpectedValue(payload);
        if (expected.isEmpty() && arguments != null && arguments.containsKey("value")) {
            Object raw = arguments.get("value");
            expected = convertValue(raw, actual != null ? actual.getClass() : null);
        }

        if (expected.isPresent()) {
            boolean matches = Objects.equals(actual, expected.orElseThrow());
            LOGGER.debug("RegistryContainsPredicate comparing actual '{}' with expected '{}' for key '{}': {}", actual, expected.get(), key, matches);
            return matches;
        }

        LOGGER.debug("RegistryContainsPredicate found value for key '{}' with no expected comparison", key);
        return true;
    }

    private Optional<String> extractKey(Payload payload) {
        if (payload == null) {
            return Optional.empty();
        }
        return payload.get(PayloadKeys.REGISTRY_KEY, String.class);
    }

    private Optional<Object> extractExpectedValue(Payload payload) {
        if (payload == null) {
            return Optional.empty();
        }
        return payload.get(PayloadKeys.REGISTRY_VALUE);
    }

    private Optional<Object> convertValue(Object value, Class<?> targetType) {
        if (targetType == null) {
            return Optional.ofNullable(value);
        }
        Optional<?> converted = ArgsUtility.read(value, targetType);
        if (converted.isEmpty()) {
            LOGGER.warn("Unable to convert expected value '{}' to type {}", value, targetType.getName());
        }
        return converted.isPresent() ? converted.map(Object.class::cast) : Optional.ofNullable(value);
    }

    private Optional<Class<?>> resolveRequestedType() {
        if (arguments == null) {
            return Optional.empty();
        }
        Optional<String> typeNameOptional = ArgsUtility.readString(arguments, "type");
        if (typeNameOptional.isEmpty()) {
            return Optional.empty();
        }
        String typeName = typeNameOptional.get();
        if (typeName.isBlank()) {
            return Optional.empty();
        }
        Resolver resolver = engine != null ? engine.getResolver() : null;
        if (resolver != null) {
            try {
                Class<?> resolved = resolver.resolve(typeName);
                LOGGER.trace("Resolved requested registry type '{}' via engine resolver", typeName);
                return Optional.of(resolved);
            } catch (ClassNotFoundException ex) {
                LOGGER.warn("Engine resolver could not find type '{}'", typeName, ex);
            }
        }
        try {
            Class<?> resolved = Class.forName(typeName);
            LOGGER.trace("Resolved requested registry type '{}' via Class.forName", typeName);
            return Optional.of(resolved);
        } catch (ClassNotFoundException ex) {
            LOGGER.warn("Unable to resolve requested registry type '{}'", typeName, ex);
            return Optional.empty();
        }
    }
}
