package dev.westernpine.composer.runtime.loader;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.westernpine.composer.api.Engine;
import dev.westernpine.composer.api.WorkflowLoader;
import dev.westernpine.composer.model.action.ScheduleWorkflowSourceMonitorAction;
import dev.westernpine.composer.model.config.WorkflowSource;
import dev.westernpine.composer.model.predicate.WorkflowSourceAvailablePredicate;
import dev.westernpine.composer.model.workflow.Initializer;
import dev.westernpine.composer.model.workflow.Workflow;
import dev.westernpine.composer.model.workflow.WorkflowAction;
import dev.westernpine.composer.model.workflow.WorkflowPredicate;
import dev.westernpine.composer.utilities.file.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FilenameFilter;
import java.util.*;

public class JsonFileWorkflowLoader implements WorkflowLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger(JsonFileWorkflowLoader.class);

    private final Engine engine;
    private WorkflowSource source;
    private final Gson gson;

    public JsonFileWorkflowLoader(Engine engine, WorkflowSource source) {
        this.engine = engine;
        this.source = source;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    @Override
    public Optional<Workflow> load(String id) throws Exception {
        File file = new File(source.uri(), id.endsWith(".json") ? id : id + ".json");
        LOGGER.debug("Loading workflow '{}' from {}", id, file.getAbsolutePath());
        if(!file.exists()) {
            LOGGER.warn("Workflow file '{}' does not exist", file.getAbsolutePath());
            return Optional.empty();
        }

        String content = FileUtils.read(file);
        Workflow workflow = gson.fromJson(content, Workflow.class);
        LOGGER.info("Loaded workflow '{}' version '{}'", workflow != null ? workflow.getId() : id, workflow != null ? workflow.getVersion() : "unknown");
        return Optional.ofNullable(workflow);
    }

    @Override
    public void save(Workflow workflow) throws Exception {
        File file = new File(source.uri(), workflow.getId().endsWith(".json") ? workflow.getId() : workflow.getId() + ".json");
        LOGGER.debug("Saving workflow '{}' to {}", workflow.getId(), file.getAbsolutePath());
        if(!file.exists()) {
            LOGGER.info("Workflow file {} does not exist. Creating new file.", file.getAbsolutePath());
            FileUtils.create(file);
        }
        FileUtils.save(file, gson.toJson(workflow));
        LOGGER.info("Workflow '{}' saved successfully", workflow.getId());
    }

    @Override
    public Optional<String> getVersion(String id) throws Exception {
        Optional<String> version = load(id).map(Workflow::getVersion);
        LOGGER.debug("Resolved version '{}' for workflow '{}'", version.orElse("unknown"), id);
        return version;
    }

    @Override
    public List<String> getAllSourceWorkflows() throws Exception {
        File file = new File(source.uri());
        if(!file.exists() || !file.isDirectory()) {
            LOGGER.warn("Workflow directory '{}' is missing or not a directory", file.getAbsolutePath());
            return List.of();
        }
        List<String> workflows = Arrays
                .stream(Objects.requireNonNull(file.listFiles()))
                .map(File::getName)
                .filter(name -> name.endsWith(".json"))
                .map(name -> String.join(".", Arrays.copyOfRange(name.split("\\."), 0, name.split("\\.").length-1)))
                .toList();
        LOGGER.debug("Found {} workflow definition(s) in {}", workflows.size(), file.getAbsolutePath());
        return workflows;
    }

    @Override
    public List<Initializer> getInitializers() {
        File initializerDirectory = new File(source.uri(), "initializers");

        if (!initializerDirectory.exists()) {
            if (initializerDirectory.mkdirs()) {
                LOGGER.info("Created initializer directory at {}", initializerDirectory.getAbsolutePath());
                createDefaultInitializer(initializerDirectory);
            } else {
                LOGGER.warn("Failed to create initializer directory at {}", initializerDirectory.getAbsolutePath());
            }
        }

        if (!initializerDirectory.isDirectory()) {
            LOGGER.warn("Initializer path {} is not a directory", initializerDirectory.getAbsolutePath());
            return List.of();
        }

        File[] initializerFiles = initializerDirectory.listFiles(jsonFileFilter());
        if (initializerFiles == null || initializerFiles.length == 0) {
            LOGGER.debug("No initializer files found in {}", initializerDirectory.getAbsolutePath());
            return List.of();
        }

        List<Initializer> initializers = new ArrayList<>();
        for (File initializerFile : initializerFiles) {
            try {
                LOGGER.debug("Loading initializer from {}", initializerFile.getAbsolutePath());
                String content = FileUtils.read(initializerFile);
                Initializer initializer = gson.fromJson(content, Initializer.class);
                if (initializer != null) {
                    LOGGER.trace("Loaded initializer {}", initializer);
                    initializers.add(initializer);
                }
            } catch (Exception ex) {
                LOGGER.error("Failed to load initializer from {}", initializerFile.getAbsolutePath(), ex);
            }
        }

        return List.copyOf(initializers);
    }

    private void createDefaultInitializer(File initializerDirectory) {
        File defaultInitializer = new File(initializerDirectory, "default.json");
        if (defaultInitializer.exists()) {
            LOGGER.debug("Default initializer already exists at {}", defaultInitializer.getAbsolutePath());
            return;
        }

        WorkflowPredicate availabilityPredicate = new WorkflowPredicate(
                WorkflowSourceAvailablePredicate.class.getName(),
                Map.of(),
                List.of());

        WorkflowAction monitorAction = new WorkflowAction(
                ScheduleWorkflowSourceMonitorAction.class.getName(),
                Map.of("interval", 5000L));

        Initializer initializer = new Initializer(
                List.of(availabilityPredicate),
                List.of(monitorAction));
        try {
            FileUtils.save(defaultInitializer, gson.toJson(initializer));
            LOGGER.info("Created default initializer at {}", defaultInitializer.getAbsolutePath());
        } catch (Exception ex) {
            LOGGER.error("Failed to create default initializer at {}", defaultInitializer.getAbsolutePath(), ex);
        }
    }

    private FilenameFilter jsonFileFilter() {
        return (dir, name) -> name != null && name.toLowerCase().endsWith(".json");
    }
}
