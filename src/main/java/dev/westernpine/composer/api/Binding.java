package dev.westernpine.composer.api;

import java.util.UUID;

public interface Binding {

    String getId();

    String getEvent();

    String getEnvironment();

    int getPriority();

    boolean isEnabled();

    UUID getSubscriberId();

    void setSubscriberId(UUID subscribe);

    boolean ignoreCancelled();
}
