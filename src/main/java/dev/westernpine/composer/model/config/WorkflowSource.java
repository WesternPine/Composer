package dev.westernpine.composer.model.config;

public record WorkflowSource (String id,
                              String uri,
                              String username,
                              String password,
                              String data) {
}
