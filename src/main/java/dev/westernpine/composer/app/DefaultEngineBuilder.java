package dev.westernpine.composer.app;

import dev.westernpine.composer.api.*;
import dev.westernpine.composer.runtime.factory.action.DefaultActionFactory;
import dev.westernpine.composer.runtime.factory.binding.DefaultBindingFactory;
import dev.westernpine.composer.runtime.factory.loader.DefaultWorkflowLoaderFactory;
import dev.westernpine.composer.runtime.factory.predicate.DefaultPredicateFactory;
import dev.westernpine.composer.runtime.eventbus.DefaultEventBus;
import dev.westernpine.composer.runtime.interpreter.DefaultInterpreter;
import dev.westernpine.composer.runtime.resolver.DefaultResolver;
import dev.westernpine.composer.runtime.registry.DefaultRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Timer;

class DefaultEngineBuilder implements EngineBuilder {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultEngineBuilder.class);

    private DefaultEngine engine = new DefaultEngine();

    private EngineConfig engineConfig;

    private Timer timer;
    private Resolver resolver;
    private EventBus eventBus;
    private WorkflowLoaderFactory workflowLoaderFactory;
    private Interpreter interpreter;
    private BindingFactory bindingFactory;
    private PredicateFactory predicateFactory;
    private ActionFactory actionFactory;
    private Registry registry;

    public DefaultEngineBuilder(EngineConfig engineConfig) {
        this.engineConfig = engineConfig;
    }

    @Override
    public Engine getFutureEngineObject() {
        return engine;
    }

    @Override
    public EngineBuilder setTimer(Timer timer) {
        this.timer = timer;
        return this;
    }

    @Override
    public EngineBuilder setResolver(Resolver resolver) {
        this.resolver = resolver;
        return this;
    }

    @Override
    public EngineBuilder setEventBus(EventBus eventBus) {
        this.eventBus = eventBus;
        return this;
    }

    @Override
    public EngineBuilder setWorkflowLoaderFactory(WorkflowLoaderFactory workflowLoaderFactory) {
        this.workflowLoaderFactory = workflowLoaderFactory;
        return this;
    }

    @Override
    public EngineBuilder setInterpreter(Interpreter interpreter) {
        this.interpreter = interpreter;
        return this;
    }

    @Override
    public EngineBuilder setBindingFactory(BindingFactory bindingFactory) {
        this.bindingFactory = bindingFactory;
        return this;
    }

    @Override
    public EngineBuilder setPredicateFactory(PredicateFactory predicateFactory) {
        this.predicateFactory = predicateFactory;
        return this;
    }

    @Override
    public EngineBuilder setActionFactory(ActionFactory actionFactory) {
        this.actionFactory = actionFactory;
        return this;
    }

    @Override
    public EngineBuilder setRegistry(Registry registry) {
        this.registry = registry;
        return this;
    }

    public Engine build() {
        LOGGER.info("Building engine with configured components");
        engine.engineConfig = engineConfig;
        engine.timer = this.timer == null ? new Timer() : this.timer;
        LOGGER.debug("Timer component: {}", this.timer == null ? "default" : this.timer.getClass().getName());
        engine.resolver = this.resolver == null ? new DefaultResolver() : this.resolver;
        LOGGER.debug("Resolver component: {}", engine.resolver.getClass().getName());
        engine.eventBus = this.eventBus == null ? new DefaultEventBus() : this.eventBus;
        LOGGER.debug("EventBus component: {}", engine.eventBus.getClass().getName());
        engine.workflowLoaderFactory = this.workflowLoaderFactory == null ? new DefaultWorkflowLoaderFactory(engine) : this.workflowLoaderFactory;
        LOGGER.debug("WorkflowLoaderFactory component: {}", engine.workflowLoaderFactory.getClass().getName());
        engine.interpreter = this.interpreter == null ? new DefaultInterpreter(engine) : this.interpreter;
        LOGGER.debug("Interpreter component: {}", engine.interpreter.getClass().getName());
        engine.bindingFactory = this.bindingFactory == null ? new DefaultBindingFactory() : this.bindingFactory;
        LOGGER.debug("BindingFactory component: {}", engine.bindingFactory.getClass().getName());
        engine.predicateFactory = this.predicateFactory == null ? new DefaultPredicateFactory(engine) : this.predicateFactory;
        LOGGER.debug("PredicateFactory component: {}", engine.predicateFactory.getClass().getName());
        engine.actionFactory = this.actionFactory == null ? new DefaultActionFactory(engine) : this.actionFactory;
        LOGGER.debug("ActionFactory component: {}", engine.actionFactory.getClass().getName());
        engine.registry = this.registry == null ? new DefaultRegistry(engine) : this.registry;
        LOGGER.debug("Registry component: {}", engine.registry.getClass().getName());
        LOGGER.info("Engine build complete");
        return engine;
    }

}
