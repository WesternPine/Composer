package dev.westernpine.composer.runtime.resolver;

import dev.westernpine.composer.api.Resolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultResolver implements Resolver {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultResolver.class);
    @Override
    public Class<?> resolve(String clazzName) throws ClassNotFoundException {
        LOGGER.debug("Resolving class '{}'", clazzName);
        Class<?> resolved = Class.forName(clazzName);
        LOGGER.trace("Resolved '{}' to {}", clazzName, resolved);
        return resolved;
    }
}
