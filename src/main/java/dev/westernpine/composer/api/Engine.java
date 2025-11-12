package dev.westernpine.composer.api;

import java.util.Timer;

public interface Engine {

    EngineConfig getConfig();

    Timer getTimer();

    Resolver getResolver();

    EventBus getEventBus();

    WorkflowLoaderFactory getWorkflowLoaderFactory();

    Interpreter getInterpreter();

    BindingFactory getBindingFactory();

    PredicateFactory getPredicateFactory();

    ActionFactory getActionFactory();

    Registry getRegistry();

    void initialize();

}
