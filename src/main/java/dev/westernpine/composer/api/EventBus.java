package dev.westernpine.composer.api;

import java.util.UUID;
import java.util.function.Consumer;

public interface EventBus {

    UUID subscribe(String topic, int priority, boolean ignoreCancelled, Consumer<Payload> listener);

    void unsubscribe(String topic, UUID subscriberUuid);

    void publish(String topic, Payload payload);
}
