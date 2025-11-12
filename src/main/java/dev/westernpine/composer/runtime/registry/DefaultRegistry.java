package dev.westernpine.composer.runtime.registry;

import dev.westernpine.composer.api.Engine;
import dev.westernpine.composer.api.Registry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

public class DefaultRegistry implements Registry {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultRegistry.class);

    private final Engine engine;
    private final Map<String, RegistryEntry> entries;

    public DefaultRegistry(Engine engine) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.entries = new ConcurrentHashMap<>();
    }

    @Override
    public Optional<Object> get(String key) {
        LOGGER.trace("Fetching value from registry key '{}'", key);
        return get(key, Object.class);
    }

    @Override
    public <T> Optional<T> get(String key, Class<T> type) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(type, "type");
        RegistryEntry entry = entries.get(key);
        if (entry == null) {
            LOGGER.debug("Registry miss for key '{}'", key);
            return Optional.empty();
        }
        if (entry.isExpired()) {
            LOGGER.debug("Registry entry for key '{}' expired", key);
            remove(key);
            return Optional.empty();
        }
        Object value = entry.getValue();
        if (!type.isInstance(value)) {
            LOGGER.error(
                    "Type mismatch when retrieving key '{}'. Expected {}, found {}",
                    key,
                    type.getName(),
                    value.getClass().getName());
            return Optional.empty();
        }
        LOGGER.trace("Registry hit for key '{}'", key);
        return Optional.of(type.cast(value));
    }

    @Override
    public void set(String key, Object value) {
        set(key, value, null);
    }

    @Override
    public void set(String key, Object value, Duration ttl) {
        Objects.requireNonNull(key, "key");
        RegistryEntry existing = entries.remove(key);
        if (existing != null) {
            LOGGER.debug("Replacing existing registry entry for key '{}'", key);
            existing.cancel();
        }
        if (value == null) {
            LOGGER.debug("Clearing registry key '{}' because value is null", key);
            return;
        }
        if (ttl != null && (ttl.isZero() || ttl.isNegative())) {
            LOGGER.warn("Ignoring registry set for key '{}' due to invalid TTL {}", key, ttl);
            return;
        }
        RegistryEntry entry = new RegistryEntry(value, ttl);
        entries.put(key, entry);
        LOGGER.info("Stored value in registry for key '{}'{}", key, ttl != null ? " with TTL " + ttl : "");
        if (ttl != null) {
            Timer timer = Objects.requireNonNull(engine.getTimer(), "engine timer");
            TimerTask task = new TimerTask() {
                @Override
                public void run() {
                    LOGGER.debug("TTL expired for registry key '{}'", key);
                    entries.compute(key, (k, current) -> current == entry ? null : current);
                }
            };
            entry.attach(task);
            timer.schedule(task, ttl.toMillis());
        }
    }

    @Override
    public boolean remove(String key) {
        Objects.requireNonNull(key, "key");
        RegistryEntry entry = entries.remove(key);
        if (entry == null) {
            LOGGER.debug("Attempted to remove non-existent registry key '{}'", key);
            return false;
        }
        entry.cancel();
        LOGGER.info("Removed registry key '{}'", key);
        return true;
    }

    @Override
    public void clear() {
        LOGGER.info("Clearing registry ({} entries)", entries.size());
        entries.values().forEach(RegistryEntry::cancel);
        entries.clear();
    }

    private static final class RegistryEntry {
        private final Object value;
        private final long expiresAt;
        private TimerTask timerTask;

        private RegistryEntry(Object value, Duration ttl) {
            this.value = value;
            this.expiresAt = ttl == null ? -1 : System.currentTimeMillis() + ttl.toMillis();
        }

        private Object getValue() {
            return value;
        }

        private boolean isExpired() {
            return expiresAt != -1 && System.currentTimeMillis() >= expiresAt;
        }

        private void attach(TimerTask timerTask) {
            this.timerTask = timerTask;
        }

        private void cancel() {
            if (timerTask != null) {
                timerTask.cancel();
            }
        }
    }
}
