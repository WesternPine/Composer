package dev.westernpine.composer.api;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

public interface Payload {

    Engine engine();

    default Map<String, Object> attributes() {
        return Collections.emptyMap();
    }

    default boolean has(String key) {
        if (key == null) {
            return false;
        }
        return attributes().containsKey(key);
    }

    default Optional<Object> get(String key) {
        if (key == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(attributes().get(key));
    }

    default <T> Optional<T> get(String key, Class<T> type) {
        if (key == null || type == null) {
            return Optional.empty();
        }
        return get(key).filter(type::isInstance).map(type::cast);
    }

    boolean isCancelled();

    void setCancelled(boolean cancelled);

    default void cancel() {
        setCancelled(true);
    }
}
