package dev.westernpine.composer.model.action;

import dev.westernpine.composer.api.Action;
import dev.westernpine.composer.api.Engine;
import dev.westernpine.composer.api.Payload;
import dev.westernpine.composer.api.Registry;
import dev.westernpine.composer.utilities.ArgsUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Optional;

public final class RegistryPopulateFieldAction implements Action {

    private static final Logger LOGGER = LoggerFactory.getLogger(RegistryPopulateFieldAction.class);

    private final Engine engine;
    private final Map<String, Object> arguments;

    public RegistryPopulateFieldAction(Engine engine, Map<String, Object> arguments) {
        this.engine = engine;
        this.arguments = arguments;
    }

    @Override
    public void execute(Payload payload) {
        if (payload == null || payload.isCancelled()) {
            LOGGER.debug("RegistryPopulateFieldAction aborted due to {}", payload == null ? "null payload" : "cancelled payload");
            return;
        }
        Registry registry = engine != null ? engine.getRegistry() : null;
        if (registry == null) {
            LOGGER.warn("RegistryPopulateFieldAction cannot execute because registry is unavailable");
            return;
        }

        String key = ArgsUtility.readString(arguments, "key").orElse(null);
        String fieldName = ArgsUtility.readString(arguments, "field").orElse(null);
        if (key == null || key.isBlank() || fieldName == null || fieldName.isBlank()) {
            LOGGER.warn("RegistryPopulateFieldAction requires non-empty key and field (key='{}', field='{}')", key, fieldName);
            return;
        }

        Optional<Object> valueHolder = registry.get(key);
        if (valueHolder.isEmpty()) {
            LOGGER.debug("RegistryPopulateFieldAction found no value for key '{}'", key);
            return;
        }
        Object value = valueHolder.get();

        Class<?> current = payload.getClass();
        Field field = null;
        while (current != null && field == null) {
            try {
                field = current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        if (field == null) {
            LOGGER.warn("RegistryPopulateFieldAction could not find field '{}' on payload type {}", fieldName, payload.getClass().getName());
            return;
        }

        Class<?> fieldType = field.getType();
        if (value == null && fieldType.isPrimitive()) {
            LOGGER.warn("Cannot assign null value to primitive field '{}' on {}", fieldName, payload.getClass().getName());
            return;
        }
        if (value != null && !fieldType.isAssignableFrom(value.getClass())) {
            boolean compatible = false;
            if (fieldType.isPrimitive()) {
                compatible = (fieldType == boolean.class && value instanceof Boolean)
                    || (fieldType == byte.class && value instanceof Byte)
                    || (fieldType == short.class && value instanceof Short)
                    || (fieldType == int.class && value instanceof Integer)
                    || (fieldType == long.class && value instanceof Long)
                    || (fieldType == float.class && value instanceof Float)
                    || (fieldType == double.class && value instanceof Double)
                    || (fieldType == char.class && value instanceof Character);
            }
            if (!compatible) {
                LOGGER.warn("Value of type {} is not compatible with field '{}' of type {}", value.getClass().getName(), fieldName, fieldType.getName());
                return;
            }
        }

        boolean accessible = field.canAccess(payload);
        if (!accessible) {
            field.setAccessible(true);
        }
        try {
            field.set(payload, value);
            LOGGER.info("Set field '{}' on payload {} using value from registry key '{}'", fieldName, payload.getClass().getName(), key);
        } catch (IllegalAccessException ex) {
            LOGGER.error("Failed to set field '{}' on payload {}", fieldName, payload.getClass().getName(), ex);
        } finally {
            if (!accessible) {
                field.setAccessible(false);
            }
        }
    }
}
