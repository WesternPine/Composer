package dev.westernpine.composer.app;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.westernpine.composer.api.Engine;
import dev.westernpine.composer.api.EngineConfig;
import dev.westernpine.composer.model.config.DefaultEngineConfig;
import dev.westernpine.composer.model.config.WorkflowSource;
import dev.westernpine.composer.utilities.file.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;

public final class ComposerApplication {

  private static final Logger LOGGER = LoggerFactory.getLogger(ComposerApplication.class);

  public static void main(String[] args) throws Exception {
    LOGGER.info("Starting Composer application");
    Gson gson = new GsonBuilder().setPrettyPrinting().create();
    File configFile = new File("sources.json");
    if(!configFile.exists()) {
      LOGGER.info("Configuration file {} not found, creating default configuration", configFile.getAbsolutePath());
      WorkflowSource defaultFileSource = new WorkflowSource("dev.westernpine.composer.runtime.loader.JsonFileWorkflowLoader", File.separator + "workflows" + File.separator, null, null, null);
      EngineConfig engineConfig = new DefaultEngineConfig("v1", List.of(defaultFileSource));
      String json = gson.toJson(engineConfig);
      FileUtils.create(configFile);
      FileUtils.save(configFile, json);
    }
    LOGGER.debug("Loading engine configuration from {}", configFile.getAbsolutePath());
    EngineConfig engineConfig = gson.fromJson(FileUtils.read(configFile), DefaultEngineConfig.class);
    LOGGER.info("Building engine using loaded configuration");
    DefaultEngineBuilder builder = new DefaultEngineBuilder(engineConfig);
    Engine engine = builder.build();
    LOGGER.info("Initializing engine");
    engine.initialize();
    LOGGER.info("Composer application started successfully");
  }

}
