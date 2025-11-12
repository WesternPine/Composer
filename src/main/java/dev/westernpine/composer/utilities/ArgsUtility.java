package dev.westernpine.composer.utilities;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ArgsUtility {

    private static final Logger LOGGER = LoggerFactory.getLogger(ArgsUtility.class);

    private ArgsUtility() {
    }

    public static Optional<Boolean> readBoolean(Map<String, Object> arguments, String key) {
        return read(arguments, key, Boolean.class);
    }

    public static Optional<Byte> readByte(Map<String, Object> arguments, String key) {
        return read(arguments, key, Byte.class);
    }

    public static Optional<Short> readShort(Map<String, Object> arguments, String key) {
        return read(arguments, key, Short.class);
    }

    public static Optional<Integer> readInteger(Map<String, Object> arguments, String key) {
        return read(arguments, key, Integer.class);
    }

    public static Optional<Long> readLong(Map<String, Object> arguments, String key) {
        return read(arguments, key, Long.class);
    }

    public static Optional<Float> readFloat(Map<String, Object> arguments, String key) {
        return read(arguments, key, Float.class);
    }

    public static Optional<Double> readDouble(Map<String, Object> arguments, String key) {
        return read(arguments, key, Double.class);
    }

    public static Optional<Character> readCharacter(Map<String, Object> arguments, String key) {
        return read(arguments, key, Character.class);
    }

    public static Optional<String> readString(Map<String, Object> arguments, String key) {
        return read(arguments, key, String.class);
    }

    public static <T> Optional<T> read(Map<String, Object> arguments, String key, Class<T> targetType) {
        if (arguments == null || key == null || targetType == null) {
            LOGGER.debug("Cannot read argument. arguments={}, key={}, targetType={}", arguments, key, targetType);
            return Optional.empty();
        }
        if (!arguments.containsKey(key)) {
            LOGGER.trace("Arguments did not contain key '{}'", key);
            return Optional.empty();
        }
        Object value = arguments.get(key);
        LOGGER.trace("Reading key '{}' as {} with value {}", key, targetType.getSimpleName(), value);
        return read(value, targetType);
    }

    // We use this function as a sanitization function before comparing types. This also accomplishes boxing as needed.
    public static <T> Optional<T> read(Object value, Class<T> targetType) {
        if (value == null) {
            LOGGER.trace("Attempted to read null value as {}", targetType);
            return Optional.empty();
        }
        Class<?> boxedType = box(targetType);
        Optional<Object> converted = convertValue(value, boxedType);
        if (converted.isEmpty()) {
            LOGGER.debug("Unable to convert value '{}' to type {}", value, boxedType.getName());
            return Optional.empty();
        }
        try {
            Object castValue = boxedType.cast(converted.orElseThrow());
            @SuppressWarnings("unchecked")
            T typedValue = (T) castValue;
            LOGGER.trace("Successfully converted value '{}' to type {}", value, boxedType.getName());
            return Optional.of(typedValue);
        } catch (ClassCastException ex) {
            LOGGER.error("Failed to cast value '{}' to type {}", value, boxedType.getName(), ex);
            return Optional.empty();
        }
    }

    private static Optional<Object> convertValue(Object value, Class<?> targetType) {
        if (targetType.isInstance(value)) {
            LOGGER.trace("Value '{}' is already instance of {}", value, targetType.getName());
            return Optional.of(value);
        }
        if (Objects.equals(targetType, String.class)) {
            LOGGER.trace("Converting value '{}' to String", value);
            return Optional.of(String.valueOf(value));
        }
        if (Objects.equals(targetType, Boolean.class)) {
            return convertBoolean(value).map(Boolean.class::cast);
        }
        if (Objects.equals(targetType, Character.class)) {
            return convertCharacter(value).map(Character.class::cast);
        }
        if (Number.class.isAssignableFrom(targetType)) {
            @SuppressWarnings("unchecked")
            Class<? extends Number> numberType = (Class<? extends Number>) targetType;
            return convertNumber(value, numberType);
        }
        return Optional.empty();
    }

    private static Optional<Boolean> convertBoolean(Object value) {
        if (value instanceof Boolean booleanValue) {
            return Optional.of(booleanValue);
        }
        if (value instanceof Number numberValue) {
            LOGGER.trace("Converting numeric value '{}' to Boolean", numberValue);
            return Optional.of(numberValue.intValue() != 0 ? Boolean.TRUE : Boolean.FALSE);
        }
        if (value instanceof String stringValue) {
            String trimmed = stringValue.trim();
            if (trimmed.isEmpty()) {
                return Optional.empty();
            }
            if ("1".equals(trimmed)) {
                return Optional.of(Boolean.TRUE);
            }
            if ("0".equals(trimmed)) {
                return Optional.of(Boolean.FALSE);
            }
            return Optional.of(Boolean.valueOf(trimmed));
        }
        if (value instanceof Character characterValue) {
            char c = characterValue.charValue();
            if (c == '1') {
                return Optional.of(Boolean.TRUE);
            }
            if (c == '0') {
                return Optional.of(Boolean.FALSE);
            }
            if (c == 't' || c == 'T' || c == 'y' || c == 'Y') {
                return Optional.of(Boolean.TRUE);
            }
            if (c == 'f' || c == 'F' || c == 'n' || c == 'N') {
                return Optional.of(Boolean.FALSE);
            }
        }
        return Optional.empty();
    }

    private static Optional<Character> convertCharacter(Object value) {
        if (value instanceof Character characterValue) {
            return Optional.of(characterValue);
        }
        if (value instanceof Number numberValue) {
            LOGGER.trace("Converting numeric value '{}' to Character", numberValue);
            return Optional.of(Character.valueOf((char) numberValue.intValue()));
        }
        if (value instanceof String stringValue) {
            String trimmed = stringValue.trim();
            if (trimmed.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(Character.valueOf(trimmed.charAt(0)));
        }
        if (value instanceof Boolean booleanValue) {
            return Optional.of(booleanValue ? '1' : '0');
        }
        return Optional.empty();
    }

    private static Optional<Object> convertNumber(Object value, Class<? extends Number> targetType) {
        if (value instanceof Number numberValue) {
            LOGGER.trace("Converting number '{}' to type {}", numberValue, targetType.getName());
            return convertNumberFromNumber(numberValue, targetType);
        }
        if (value instanceof String stringValue) {
            String trimmed = stringValue.trim();
            if (trimmed.isEmpty()) {
                return Optional.empty();
            }
            try {
                if (Objects.equals(targetType, Byte.class)) {
                    return Optional.of(Byte.valueOf(trimmed));
                }
                if (Objects.equals(targetType, Short.class)) {
                    return Optional.of(Short.valueOf(trimmed));
                }
                if (Objects.equals(targetType, Integer.class)) {
                    return Optional.of(Integer.valueOf(trimmed));
                }
                if (Objects.equals(targetType, Long.class)) {
                    return Optional.of(Long.valueOf(trimmed));
                }
                if (Objects.equals(targetType, Float.class)) {
                    return Optional.of(Float.valueOf(trimmed));
                }
                if (Objects.equals(targetType, Double.class)) {
                    return Optional.of(Double.valueOf(trimmed));
                }
            } catch (NumberFormatException ignored) {
                return Optional.empty();
            }
        }
        if (value instanceof Character characterValue) {
            LOGGER.trace("Converting character '{}' to number type {}", characterValue, targetType.getName());
            return convertNumberFromNumber(Integer.valueOf(characterValue.charValue()), targetType);
        }
        if (value instanceof Boolean booleanValue) {
            LOGGER.trace("Converting boolean '{}' to number type {}", booleanValue, targetType.getName());
            return convertNumberFromNumber(booleanValue ? Integer.valueOf(1) : Integer.valueOf(0), targetType);
        }
        return Optional.empty();
    }

    private static Optional<Object> convertNumberFromNumber(Number value, Class<? extends Number> targetType) {
        if (Objects.equals(targetType, Byte.class)) {
            return Optional.of(Byte.valueOf(value.byteValue()));
        }
        if (Objects.equals(targetType, Short.class)) {
            return Optional.of(Short.valueOf(value.shortValue()));
        }
        if (Objects.equals(targetType, Integer.class)) {
            return Optional.of(Integer.valueOf(value.intValue()));
        }
        if (Objects.equals(targetType, Long.class)) {
            return Optional.of(Long.valueOf(value.longValue()));
        }
        if (Objects.equals(targetType, Float.class)) {
            return Optional.of(Float.valueOf(value.floatValue()));
        }
        if (Objects.equals(targetType, Double.class)) {
            return Optional.of(Double.valueOf(value.doubleValue()));
        }
        return Optional.empty();
    }

    private static Class<?> box(Class<?> type) {
        if (type == null) {
            return Object.class;
        }
        if (!type.isPrimitive()) {
            return type;
        }
        LOGGER.trace("Boxing primitive type {}", type.getName());
        if (type == boolean.class) {
            return Boolean.class;
        }
        if (type == byte.class) {
            return Byte.class;
        }
        if (type == short.class) {
            return Short.class;
        }
        if (type == int.class) {
            return Integer.class;
        }
        if (type == long.class) {
            return Long.class;
        }
        if (type == float.class) {
            return Float.class;
        }
        if (type == double.class) {
            return Double.class;
        }
        if (type == char.class) {
            return Character.class;
        }
        return type;
    }
}
