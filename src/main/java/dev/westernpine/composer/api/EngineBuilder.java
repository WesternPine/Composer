package dev.westernpine.composer.api;

import java.util.Timer;

public interface EngineBuilder {
    Engine getFutureEngineObject();

    EngineBuilder setTimer(Timer timer);

    EngineBuilder setResolver(Resolver resolver);

    EngineBuilder setEventBus(EventBus eventBus);

    EngineBuilder setWorkflowLoaderFactory(WorkflowLoaderFactory workflowLoaderFactory);

    EngineBuilder setInterpreter(Interpreter interpreter);

    EngineBuilder setBindingFactory(BindingFactory bindingFactory);

    EngineBuilder setPredicateFactory(PredicateFactory predicateFactory);

    EngineBuilder setActionFactory(ActionFactory actionFactory);

    EngineBuilder setRegistry(Registry registry);
}
