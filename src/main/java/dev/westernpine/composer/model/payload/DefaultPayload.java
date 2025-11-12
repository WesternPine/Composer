package dev.westernpine.composer.model.payload;

import dev.westernpine.composer.api.Engine;
import dev.westernpine.composer.api.Payload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class DefaultPayload implements Payload {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultPayload.class);

    private final Engine engine;
    private final Map<String, Object> attributes;
    private final Map<String, Object> view;
    private volatile boolean cancelled;

    public DefaultPayload(Engine engine) {
        this(engine, null);
    }

    public DefaultPayload(Engine engine, Map<String, Object> attributes) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.attributes = new ConcurrentHashMap<>();
        if (attributes != null) {
            this.attributes.putAll(attributes);
            LOGGER.trace("Initialized payload with attributes {}", attributes.keySet());
        }
        this.view = Collections.unmodifiableMap(this.attributes);
    }

    @Override
    public Engine engine() {
        return engine;
    }

    @Override
    public Map<String, Object> attributes() {
        return view;
    }

    public DefaultPayload with(String key, Object value) {
        if (key == null) {
            LOGGER.warn("Attempted to set payload attribute with null key");
            return this;
        }
        if (value == null) {
            LOGGER.trace("Removing payload attribute '{}'", key);
            attributes.remove(key);
        } else {
            LOGGER.trace("Setting payload attribute '{}' to {}", key, value);
            attributes.put(key, value);
        }
        return this;
    }

    public DefaultPayload withAll(Map<String, Object> entries) {
        if (entries != null) {
            entries.forEach(this::with);
        }
        return this;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        LOGGER.debug("Setting payload cancelled state to {}", cancelled);
        this.cancelled = cancelled;
    }
}
