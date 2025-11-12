package dev.westernpine.composer.model.subscriber;

import dev.westernpine.composer.api.Payload;

import java.util.UUID;
import java.util.function.Consumer;

public record Subscriber(UUID uuid, int priority, boolean ignoreCancelled, Consumer<Payload> listener) {
}
