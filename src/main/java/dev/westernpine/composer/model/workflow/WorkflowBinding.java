package dev.westernpine.composer.model.workflow;

import com.fasterxml.jackson.annotation.JsonIgnore;
import dev.westernpine.composer.api.Binding;

import java.util.UUID;

public class WorkflowBinding implements Binding {
    private String id;
    private String event;
    private String environment;
    private int priority;
    private boolean enabled;
    private boolean ignoreCancelled;
    @JsonIgnore
    private UUID subscriberId;

    public WorkflowBinding(String id, String event, String environment, int priority, boolean enabled, boolean ignoreCancelled) {
        this.id = id;
        this.event = event;
        this.environment = environment;
        this.priority = priority;
        this.enabled = enabled;
        this.ignoreCancelled = ignoreCancelled;
    }

    public String getId() {
        return id;
    }

    public String getEvent() {
        return event;
    }

    public String getEnvironment() {
        return environment;
    }

    public int getPriority() {
        return priority;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isIgnoreCancelled() {
        return ignoreCancelled;
    }

    public UUID getSubscriberId() {
        return subscriberId;
    }

    public void setSubscriberId(UUID subscriberId) {
        this.subscriberId = subscriberId;
    }

    public void setIgnoreCancelled(boolean ignoreCancelled) {
        this.ignoreCancelled = ignoreCancelled;
    }

    @Override
    public boolean ignoreCancelled() {
        return ignoreCancelled;
    }
}
