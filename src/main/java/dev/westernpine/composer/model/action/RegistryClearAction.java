package dev.westernpine.composer.model.action;

import dev.westernpine.composer.api.Action;
import dev.westernpine.composer.api.Engine;
import dev.westernpine.composer.api.Payload;
import dev.westernpine.composer.api.Registry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RegistryClearAction implements Action {

    private static final Logger LOGGER = LoggerFactory.getLogger(RegistryClearAction.class);

    private final Engine engine;

    public RegistryClearAction(Engine engine) {
        this.engine = engine;
    }

    @Override
    public void execute(Payload payload) {
        if (payload != null && payload.isCancelled()) {
            LOGGER.debug("RegistryClearAction aborted because payload is cancelled");
            return;
        }
        Registry registry = engine != null ? engine.getRegistry() : null;
        if (registry == null) {
            LOGGER.warn("RegistryClearAction cannot execute because registry is unavailable");
            return;
        }
        LOGGER.info("Clearing registry via RegistryClearAction");
        registry.clear();
    }
}
