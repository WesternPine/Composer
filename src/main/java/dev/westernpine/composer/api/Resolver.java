package dev.westernpine.composer.api;

public interface Resolver {

    public Class<?> resolve(String clazzName) throws ClassNotFoundException;

}
