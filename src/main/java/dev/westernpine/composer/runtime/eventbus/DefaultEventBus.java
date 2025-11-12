package dev.westernpine.composer.runtime.eventbus;

import dev.westernpine.composer.api.EventBus;
import dev.westernpine.composer.api.Payload;
import dev.westernpine.composer.model.subscriber.Subscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Consumer;

public class DefaultEventBus implements EventBus {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultEventBus.class);

    private final Map<String, List<Subscriber>> subscribers = new HashMap<>();

    @Override
    public synchronized UUID subscribe(String topic, int priority, boolean ignoreCancelled, Consumer<Payload> listener) {
        UUID uuid = UUID.randomUUID();
        List<Subscriber> listeners = subscribers.computeIfAbsent(topic, key -> new ArrayList<>());
        Subscriber subscriber = new Subscriber(uuid, priority, ignoreCancelled, listener);
        int index = 0;
        while (index < listeners.size() && listeners.get(index).priority() >= priority) {
            index++;
        }
        listeners.add(index, subscriber);
        LOGGER.info("Registered subscriber {} for topic '{}' with priority {} (ignoreCancelled={})", uuid, topic, priority, ignoreCancelled);
        return uuid;
    }

    @Override
    public synchronized void unsubscribe(String topic, UUID subscriberUuid) {
        List<Subscriber> listeners = subscribers.get(topic);
        if (listeners == null) {
            LOGGER.debug("No subscribers found for topic '{}' when attempting to unsubscribe {}", topic, subscriberUuid);
            return;
        }
        listeners.removeIf(subscriber -> subscriber.uuid().equals(subscriberUuid));
        if (listeners.isEmpty()) {
            subscribers.remove(topic);
        }
        LOGGER.info("Unregistered subscriber {} from topic '{}'", subscriberUuid, topic);
    }

    @Override
    public synchronized void publish(String topic, Payload payload) {
        var listeners = subscribers.get(topic);
        if (listeners == null || listeners.isEmpty()) {
            LOGGER.debug("No listeners to publish to for topic '{}'", topic);
            return;
        }
        LOGGER.debug("Publishing event to {} subscriber(s) on topic '{}'", listeners.size(), topic);
        for (Subscriber subscriber : List.copyOf(listeners)) { // copyOf because of synchronization
            if (payload.isCancelled() && !subscriber.ignoreCancelled()) {
                LOGGER.debug("Skipping subscriber {} for topic '{}' because payload is cancelled", subscriber.uuid(), topic);
                continue;
            }
            LOGGER.trace("Delivering event to subscriber {} on topic '{}'", subscriber.uuid(), topic);
            try {
                subscriber.listener().accept(payload);
            } catch (Exception e) {
                LOGGER.error(
                        "Subscriber {} threw while handling topic '{}'",
                        subscriber.uuid(),
                        topic,
                        e);
            }
        }
    }
}
