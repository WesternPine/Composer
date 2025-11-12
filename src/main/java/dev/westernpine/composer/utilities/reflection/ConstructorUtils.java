package dev.westernpine.composer.utilities.reflection;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConstructorUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConstructorUtils.class);

    /**
     * Finds the best matching constructor for the given class and parameter types.
     * @param targetClass The class to find a constructor for
     * @param parameterTypes The desired parameter types in order of preference
     * @return The best matching constructor, or {@link Optional#empty()} if none found
     */
    public static Optional<Constructor<?>> findBestMatchingConstructor(Class<?> targetClass, Class<?>... parameterTypes) {
        Constructor<?>[] constructors = Objects.requireNonNull(targetClass, "targetClass").getConstructors();

        // If no constructors, try to get the default one
        if (constructors.length == 0) {
            try {
                return Optional.of(targetClass.getDeclaredConstructor());
            } catch (NoSuchMethodException e) {
                return Optional.empty();
            }
        }

        // First try to find an exact match
        for (Constructor<?> constructor : constructors) {
            if (Arrays.equals(constructor.getParameterTypes(), parameterTypes)) {
                return Optional.of(constructor);
            }
        }

        // If no exact match, try to find constructor with subset of parameters
        // (ordered from most parameters to least)
        for (int len = parameterTypes.length - 1; len >= 0; len--) {
            for (Constructor<?> constructor : constructors) {
                Class<?>[] constructorParams = constructor.getParameterTypes();
                if (constructorParams.length == len) {
                    boolean matches = true;
                    for (int i = 0; i < len; i++) {
                        if (!containsType(parameterTypes, constructorParams[i])) {
                            matches = false;
                            break;
                        }
                    }
                    if (matches) {
                        return Optional.of(constructor);
                    }
                }
            }
        }

        // If still no match, try no-args constructor
        try {
            return Optional.of(targetClass.getConstructor());
        } catch (NoSuchMethodException e) {
            return Optional.empty();
        }
    }

    private static boolean containsType(Class<?>[] types, Class<?> searchType) {
        for (Class<?> type : types) {
            if (type.equals(searchType)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Creates an instance using the best matching constructor
     * @param targetClass The class to instantiate
     * @param parameters Array of actual parameters to pass to constructor
     * @return The new instance wrapped in an {@link Optional}, or empty if unable to create
     */
    public static Optional<Object> createInstance(Class<?> targetClass, Object...parameters) {
        Class<?>[] parameterTypes = Arrays.stream(parameters)
                .map(Objects::requireNonNull)
                .map(Object::getClass)
                .toArray(Class<?>[]::new);
        try {
            Optional<Constructor<?>> constructor = findBestMatchingConstructor(targetClass, parameterTypes);
            if (constructor.isEmpty()) {
                LOGGER.warn("No matching constructor found for class {} with parameter types {}", targetClass.getName(), Arrays.toString(parameterTypes));
                return Optional.empty();
            }

            Class<?>[] actualParamTypes = constructor.get().getParameterTypes();
            Object[] actualParams = new Object[actualParamTypes.length];

            // Match parameters to constructor parameter types
            for (int i = 0; i < actualParamTypes.length; i++) {
                for (int j = 0; j < parameterTypes.length; j++) {
                    if (actualParamTypes[i].equals(parameterTypes[j])) {
                        actualParams[i] = parameters[j];
                        break;
                    }
                }
            }

            return Optional.of(constructor.get().newInstance(actualParams));
        } catch (Exception e) {
            LOGGER.error("Failed to create instance of {}", targetClass.getName(), e);
            return Optional.empty();
        }
    }
}
