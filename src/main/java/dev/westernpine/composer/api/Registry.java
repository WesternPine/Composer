package dev.westernpine.composer.api;

import java.time.Duration;
import java.util.Optional;

public interface Registry {

    Optional<Object> get(String key);

    <T> Optional<T> get(String key, Class<T> type);

    void set(String key, Object value);

    void set(String key, Object value, Duration ttl);

    boolean remove(String key);

    void clear();
}
