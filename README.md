# Composer

Composer is a single-module Java 17 workflow and rules engine that you can run as a standalone service or embed inside another JVM application. It combines workflow loading, event dispatching, predicate evaluation, and action execution behind a small, composable API surface while staying aggressively lazy about expensive work. Workflows are parsed into lightweight metadata; class loading and instantiation only happen when an event actually needs them.

## Table of contents
- [At a glance](#at-a-glance)
- [Quick start](#quick-start)
  - [Build the project](#build-the-project)
  - [Run the standalone engine](#run-the-standalone-engine)
  - [Repository layout](#repository-layout)
- [Core concepts](#core-concepts)
  - [Engine lifecycle](#engine-lifecycle)
  - [Workflows](#workflows)
  - [Payloads](#payloads)
  - [Predicates](#predicates)
  - [Actions](#actions)
  - [Bindings and the event bus](#bindings-and-the-event-bus)
  - [Registry](#registry)
- [Workflow sources and loaders](#workflow-sources-and-loaders)
  - [Configuring `sources.json`](#configuring-sourcesjson)
  - [JSON file loader behaviour](#json-file-loader-behaviour)
  - [Initializers](#initializers)
- [Example workflow definition](#example-workflow-definition)
- [Embedding Composer in another application](#embedding-composer-in-another-application)
  - [Bootstrapping the engine](#bootstrapping-the-engine)
  - [Publishing events](#publishing-events)
  - [Consuming registry values](#consuming-registry-values)
- [Extending Composer](#extending-composer)
  - [Custom predicates and actions](#custom-predicates-and-actions)
  - [Custom workflow loaders](#custom-workflow-loaders)
- [Standalone runtime behaviour](#standalone-runtime-behaviour)
- [Troubleshooting and tips](#troubleshooting-and-tips)

## At a glance
Composer exposes a deliberately small set of primitives:

| Primitive | Purpose | Key implementation |
| --- | --- | --- |
| `Engine` | Aggregates the runtime subsystems (resolver, loader factory, interpreter, registry, event bus, timer). | [`DefaultEngine`](src/main/java/dev/westernpine/composer/app/DefaultEngine.java) |
| `Interpreter` | Keeps the active workflow catalogue in memory, subscribes workflow bindings to the event bus, and lazily orchestrates predicate/action execution so only the required classes are touched. | [`DefaultInterpreter`](src/main/java/dev/westernpine/composer/runtime/interpreter/DefaultInterpreter.java) |
| `WorkflowLoaderFactory` | Caches loaders by source id and reflectively instantiates them using the class name recorded on each source. | [`DefaultWorkflowLoaderFactory`](src/main/java/dev/westernpine/composer/runtime/factory/loader/DefaultWorkflowLoaderFactory.java) |
| `EventBus` | Simple synchronous publish/subscribe transport that honours cancellation flags and listener priority. | [`DefaultEventBus`](src/main/java/dev/westernpine/composer/runtime/eventbus/DefaultEventBus.java) |
| `Registry` | Thread-safe key/value store with optional TTL support for passing state between actions, predicates, and external code. | [`DefaultRegistry`](src/main/java/dev/westernpine/composer/runtime/registry/DefaultRegistry.java) |
| `Payload` | Mutable event envelope shared by predicates and actions during workflow execution. | [`DefaultPayload`](src/main/java/dev/westernpine/composer/model/payload/DefaultPayload.java) |

## Quick start

### Build the project
```bash
mvn -B clean verify
```

### Run the standalone engine
```bash
java -jar target/composer-0.1.0-SNAPSHOT-shaded.jar
```
The runnable JAR boots [`ComposerApplication`](src/main/java/dev/westernpine/composer/app/ComposerApplication.java), guarantees that a [`sources.json`](sources.json) file exists, constructs a default engine, and immediately calls `Engine#initialize()` to discover workflow sources and schedule monitors.

### Repository layout
```
src/main/java/dev/westernpine/composer/
├── api/                # Public interfaces (engine, factories, registry, payload, event bus, loaders)
├── app/                # Default engine implementation and CLI bootstrap
├── model/              # Immutable workflow models, predicates/actions/initializers, payload/event keys
├── runtime/            # Runtime subsystems (event bus, interpreter, factories, registry, resolver, loader)
└── utilities/          # Helper classes for file IO, constructor reflection, and argument coercion
```

## Core concepts

### Engine lifecycle
1. **Build an engine** using [`DefaultEngineBuilder`](src/main/java/dev/westernpine/composer/app/DefaultEngineBuilder.java). Any subsystem you do not override is populated with Composer defaults (timer, resolver, event bus, factories, registry).
2. **Load workflow sources** by invoking `engine.initialize()`. Initialization walks every configured [`WorkflowSource`](src/main/java/dev/westernpine/composer/model/config/WorkflowSource.java), builds a payload for the source, and runs loader initializers when their predicates pass. The engine only captures metadata at this stage—class resolution is deferred so startup never blocks on class loading.
3. **Subscribe workflow bindings** via [`DefaultInterpreter`](src/main/java/dev/westernpine/composer/runtime/interpreter/DefaultInterpreter.java). When workflows are added, each [`WorkflowBinding`](src/main/java/dev/westernpine/composer/model/workflow/WorkflowBinding.java) subscribes to the event bus.
4. **Publish payloads** to the event bus (manually or from workflow monitors). The interpreter evaluates predicates and executes actions against the payload when workflow conditions pass.

### Workflows
A workflow (`model/workflow/Workflow`) contains:
- Metadata: `id`, `version`
- `workflowPredicates`: ordered list of predicate definitions executed before actions.
- `workflowActions`: ordered list of actions executed when all predicates return `true`.
- `workflowBindings`: bindings describing which topics to subscribe to and at which priority.

Each predicate or action is stored as an identifier (typically a fully qualified class name) plus an optional argument map. Composer deliberately keeps these identifiers dormant until the interpreter actually needs them; factories only resolve and instantiate the concrete classes at execution time, never while merely parsing workflow text.

### Payloads
Payloads are mutable envelopes that travel with each event. Built-in keys help actions and predicates share context.

| Key | Type | Produced by | Typical consumer |
| --- | --- | --- | --- |
| `workflow.id` | `String` | `DefaultEngine#initialize()` and loader monitors | Registry-aware actions/predicates | 
| `workflow.source` | [`WorkflowSource`](src/main/java/dev/westernpine/composer/model/config/WorkflowSource.java) | Loader initializers and monitors | Source predicates, monitor actions |
| `registry.key` | `String` | Registry aware actions/predicates or external publishers | Registry actions/predicates |
| `registry.value` | `Object` | Registry aware actions/predicates or external publishers | Registry actions/predicates |
| `registry.ttl` | `Duration` | Registry aware actions/predicates or external publishers | [`RegistrySetAction`](src/main/java/dev/westernpine/composer/model/action/RegistrySetAction.java) |

Create payloads via `new DefaultPayload(engine)` and add attributes with fluent `.with(key, value)`.

### Predicates
Predicates decide whether actions should run. Composer ships with the following implementations:

| Identifier (`WorkflowPredicate#id`) | Purpose | Arguments |
| --- | --- | --- |
| `dev.westernpine.composer.model.predicate.AllPredicate` | Returns `true` only when all nested predicates pass. | `innerPredicates` array supplied in workflow definition. |
| `dev.westernpine.composer.model.predicate.OrPredicate` | Returns `true` when any nested predicate passes. | `innerPredicates` array supplied in workflow definition. |
| `dev.westernpine.composer.model.predicate.RegistryContainsPredicate` | Checks that a registry key exists (optionally with a matching value). | `key` (String, optional when provided via payload), `value` (any, optional), `type` (class name to coerce value). |
| `dev.westernpine.composer.model.predicate.RegistryMissingPredicate` | Returns `true` when a registry key is absent. | `key` (String, optional when provided via payload). |
| `dev.westernpine.composer.model.predicate.WorkflowSourceAvailablePredicate` | Ensures that a workflow source can be resolved by the loader factory before scheduling monitors. | none |

Predicates receive the current payload; if the payload is cancelled, evaluation short-circuits.

### Actions
Actions run after predicates succeed. Available built-ins include:

| Identifier (`WorkflowAction#id`) | Purpose | Arguments |
| --- | --- | --- |
| `dev.westernpine.composer.model.action.RegistrySetAction` | Writes a value to the registry (with optional TTL) or removes the key when the value is `null`. | `key` (String), `value` (any), `ttl` (ISO-8601 duration string, number of milliseconds, or `Duration`). |
| `dev.westernpine.composer.model.action.RegistryRemoveAction` | Removes a key from the registry. | `key` (String). |
| `dev.westernpine.composer.model.action.RegistryClearAction` | Clears every entry from the registry. | none |
| `dev.westernpine.composer.model.action.RegistryPopulateFieldAction` | Reads a registry value and writes it into a field on the current payload instance via reflection. | `key` (String), `field` (String). |
| `dev.westernpine.composer.model.action.ScheduleWorkflowSourceMonitorAction` | Schedules a repeating timer task that keeps workflows from a source synchronized with disk. | `interval` (long milliseconds, defaults to 5000). |

Actions should honour `Payload#isCancelled()` and `Registry` interactions are mediated through `Engine#getRegistry()`.

### Bindings and the event bus
Bindings declare which event topics a workflow listens to. Composer’s event bus:
- Stores listeners ordered by priority (descending). Higher priority subscribers execute first.
- Skips listeners that opt **not** to ignore cancelled payloads.
- Returns a `UUID` for every subscription so bindings can unsubscribe cleanly.

Publish events with `EventBus#publish(topic, payload)`; subscribers receive the same mutable payload instance.

Composer reserves the following event keys (see [`EventKeys`](src/main/java/dev/westernpine/composer/model/event/EventKeys.java)):

| Topic | Emitted when |
| --- | --- |
| `workflow.added` | Interpreter registers a workflow and binds its events. |
| `workflow.removed` | Interpreter deregisters a workflow and unsubscribes bindings. |

Your application can define arbitrary additional topics for gameplay, telemetry, or automation events.

### Registry
The registry is a concurrent map with optional expiration per key. Key capabilities include:
- `Registry#set(key, value, ttl)` schedules a `TimerTask` to evict the value when the TTL elapses.
- `Registry#get(key, type)` performs type-safe retrieval and throws when the stored value cannot be cast to the requested type.
- `Registry#clear()` cancels pending TTL tasks and wipes the map.

These semantics are implemented by [`DefaultRegistry`](src/main/java/dev/westernpine/composer/runtime/registry/DefaultRegistry.java) using the engine’s shared `Timer` for TTL handling.

## Workflow sources and loaders

### Configuring `sources.json`
`sources.json` lists the workflow sources Composer should load. Each entry matches the [`WorkflowSource`](src/main/java/dev/westernpine/composer/model/config/WorkflowSource.java) record:

```json
{
  "version": "v1",
  "workflowSources": [
    {
      "id": "dev.westernpine.composer.runtime.loader.JsonFileWorkflowLoader",
      "uri": "./workflows",
      "username": null,
      "password": null,
      "data": null
    }
  ]
}
```

- `id` must be unique; the runtime uses it to request loaders from the factory, seed payloads, and subscribe workflow monitors. The packaged CLI seeds this value with the loader's fully qualified class name because there is no separate logical `type` field in [`WorkflowSource`](src/main/java/dev/westernpine/composer/model/config/WorkflowSource.java).
- `uri`, `username`, `password`, and `data` are loader-specific configuration knobs.

The default workflow loader factory bypasses the resolver entirely; it calls `Class.forName` on the configured class name, instantiates the loader with `(Engine, WorkflowSource)`, and caches the instance under the same `id` for future lookups.

### JSON file loader behaviour
[`JsonFileWorkflowLoader`](src/main/java/dev/westernpine/composer/runtime/loader/JsonFileWorkflowLoader.java) is the default loader seeded by `ComposerApplication`:
- Loads workflows from `<uri>/<workflowId>.json`.
- Persists workflows back to disk via `WorkflowLoader#save`.
- Returns workflow versions for change detection.
- Lists all workflow identifiers under the `uri` directory.
- Exposes loader-specific initializers stored in `<uri>/initializers/*.json`.

### Initializers
Initializers (`model/workflow/Initializer`) are mini-workflows evaluated during `Engine#initialize()` to bootstrap loader-specific automation. The JSON loader seeds an initializer that:
1. Evaluates `WorkflowSourceAvailablePredicate` to ensure the source can be resolved.
2. Runs `ScheduleWorkflowSourceMonitorAction` to poll the source directory and keep workflows synchronized.

You can add more initializer files inside `<uri>/initializers/` to run additional predicates and actions at startup.

## Example workflow definition
Below is an illustrative workflow JSON compatible with the default loader. It listens for a custom `pvp.kill` topic, writes the first kill streak it sees into the registry, and mirrors the stored value back onto the payload for downstream consumers.

```json
{
  "id": "pvp-kill-tracker",
  "version": "1.0.0",
  "workflowPredicates": [
    {
      "id": "dev.westernpine.composer.model.predicate.RegistryMissingPredicate",
      "args": { "key": "match.current" }
    }
  ],
  "workflowActions": [
    {
      "id": "dev.westernpine.composer.model.action.RegistrySetAction",
      "args": {
        "key": "match.current"
      }
    },
    {
      "id": "dev.westernpine.composer.model.action.RegistryPopulateFieldAction",
      "args": {
        "key": "match.current",
        "field": "killCount"
      }
    }
  ],
  "workflowBindings": [
    {
      "id": "pvp.kill.listener",
      "event": "pvp.kill",
      "environment": "prod",
      "priority": 100,
      "enabled": true,
      "ignoreCancelled": false
    }
  ]
}
```

> **How it works:** The publisher that emits the `pvp.kill` event should place the desired value into the payload using `PayloadKeys.REGISTRY_VALUE`. `RegistrySetAction` will fall back to that payload attribute when the `value` argument is omitted, while `RegistryPopulateFieldAction` copies the stored value into a `killCount` field on the payload object.

## Embedding Composer in another application

### Bootstrapping the engine
Instantiate Composer with your own configuration, optionally providing custom subsystems:

```java
import dev.westernpine.composer.app.DefaultEngineBuilder;
import dev.westernpine.composer.api.Engine;
import dev.westernpine.composer.model.config.DefaultEngineConfig;
import dev.westernpine.composer.model.config.WorkflowSource;

Engine engine = new DefaultEngineBuilder(new DefaultEngineConfig(
        "v1",
        List.of(new WorkflowSource(
                "dev.westernpine.composer.runtime.loader.JsonFileWorkflowLoader",
                "./workflows",
                null,
                null,
                null))))
        .build();
engine.initialize();
```

You can override timer, resolver, event bus, loader factory, interpreter, binding factory, predicate factory, action factory, or registry using the fluent setters exposed by [`EngineBuilder`](src/main/java/dev/westernpine/composer/api/EngineBuilder.java).

### Publishing events
Once workflows are loaded, publish payloads through the event bus:

```java
import dev.westernpine.composer.api.EventBus;
import dev.westernpine.composer.model.payload.DefaultPayload;
import dev.westernpine.composer.model.payload.PayloadKeys;

DefaultPayload payload = new DefaultPayload(engine)
        .with("match.current", 10)
        .with(PayloadKeys.REGISTRY_KEY, "match.current");

EventBus bus = engine.getEventBus();
bus.publish("pvp.kill", payload);
```

Subscribers execute synchronously; if an action calls `payload.cancel()`, subsequent listeners that do **not** ignore cancellations are skipped.

### Consuming registry values
The registry doubles as a lightweight shared state cache:

```java
engine.getRegistry().set("match.current", 1);
engine.getRegistry().set("session.timeout", Instant.now().plus(Duration.ofMinutes(30)), Duration.ofMinutes(30));

Optional<Integer> current = engine.getRegistry().get("match.current", Integer.class);
```

Use TTLs for expiring telemetry or session data. Removing a key cancels its scheduled eviction task automatically.

## Extending Composer

### Custom predicates and actions
Implement `Predicate` or `Action`, provide a public constructor, and make the class discoverable to the resolver (by default `Class#forName`). Composer will only touch these classes when an event actually needs them, so cold starts stay fast even as the catalogue grows. Factories look for constructors in the following order:
1. `(Engine, WorkflowPredicate)` or `(Engine, WorkflowAction)`
2. `(Engine, Map<String, Object>)`
3. `(Engine)`
4. No-args constructor

For example, a predicate that checks server population:

```java
public final class ServerPopulationPredicate implements Predicate {
    private final Engine engine;
    private final Map<String, Object> args;

    public ServerPopulationPredicate(Engine engine, Map<String, Object> args) {
        this.engine = engine;
        this.args = args;
    }

    @Override
    public boolean evaluate(Payload payload) {
        int threshold = ArgsUtility.readInteger(args, "minPlayers").orElse(0);
        return engine.getRegistry().get("population", Integer.class)
                .map(count -> count >= threshold)
                .orElse(false);
    }
}
```

### Custom workflow loaders
Create a class that implements [`WorkflowLoader`](src/main/java/dev/westernpine/composer/api/WorkflowLoader.java). Provide a constructor compatible with the default loader factory (typically `(Engine, WorkflowSource)`). Register your loader by pointing a `WorkflowSource` at the loader's fully qualified class name so that the default factory can call `Class.forName` on it when the engine requests the loader.

Override the loader factory via `EngineBuilder#setWorkflowLoaderFactory(...)` if you need bespoke instantiation logic.

## Standalone runtime behaviour
Running the shaded JAR performs the following steps:
1. Ensures `sources.json` exists, seeding it with a `JsonFileWorkflowLoader` entry whose `id` is the loader's class name and whose `uri` defaults to `File.separator + "workflows" + File.separator`.
2. Builds a default engine with standard subsystems (resolver, event bus, interpreter, registry, loader factory, predicate/action factories, timer).
3. Calls `engine.initialize()`, which:
   - Emits a payload per workflow source with `workflow.id` and `workflow.source` populated.
   - Executes loader initializers (e.g., `ScheduleWorkflowSourceMonitorAction`) to start polling for workflow changes.
   - Leaves predicate and action classes unloaded until an event actually triggers them, keeping the boot sequence light.
4. Leaves the process running so timer tasks and event subscriptions can react to external updates.

Add workflows to the configured directory to have them auto-loaded; the monitor action compares loader versions and refreshes the interpreter when files change.

## Troubleshooting and tips
- If predicates or actions are not found, confirm their class names are resolvable by [`Resolver`](src/main/java/dev/westernpine/composer/api/Resolver.java). Override the resolver when running in environments with custom class loading.
- Use `ignoreCancelled=true` on bindings that must always run even when earlier actions cancel the payload.
- TTL-driven registry entries require the engine’s `Timer`; supply your own timer when embedding in a container that manages scheduled executors.
- Keep workflow IDs compliant with `Workflow.VALID_NAME_REGEX` (`^[a-zA-Z0-9_-]*$`).
