package dev.westernpine.composer.runtime.loader;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.westernpine.composer.api.Engine;
import dev.westernpine.composer.api.WorkflowLoader;
import dev.westernpine.composer.model.config.WorkflowSource;
import dev.westernpine.composer.model.workflow.Workflow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A {@link WorkflowLoader} implementation backed by a MySQL database.
 *
 * <p>The loader keeps all database interactions as lazy as possible by only opening connections when
 * work is requested. During construction the loader will optimistically attempt to create the schema,
 * but any failure is silently ignored so the caller is not blocked on start up. Every interaction with
 * the database ensures the schema exists and recreates it on-demand if the backing table was removed.</p>
 */
public class MySqlWorkflowLoader implements WorkflowLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger(MySqlWorkflowLoader.class);

    private static final String DEFAULT_TABLE_NAME = "composer_workflows";
    private static final String TABLE_NOT_FOUND_STATE = "42S02";

    private final WorkflowSource source;
    private final Gson gson;

    private final Object schemaLock = new Object();
    private final AtomicBoolean schemaInitialized = new AtomicBoolean(false);

    private String tableName;
    private List<String> schemaStatements;

    public MySqlWorkflowLoader(Engine engine, WorkflowSource source) {
        Objects.requireNonNull(engine, "engine");
        this.source = Objects.requireNonNull(source, "workflow source");
        this.gson = new Gson();
        parseSourceConfiguration();
        tryInitializeSchema();
    }

    @Override
    public Optional<Workflow> load(String id) throws Exception {
        LOGGER.debug("Loading workflow '{}' from MySQL table '{}'", id, tableName);
        return executeWithSchema(() -> {
            try (Connection connection = getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "SELECT definition FROM " + tableName + " WHERE id = ?")) {
                statement.setString(1, id);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        LOGGER.debug("No workflow found in table '{}' with id '{}'", tableName, id);
                        return Optional.empty();
                    }
                    String json = resultSet.getString("definition");
                    Workflow workflow = gson.fromJson(json, Workflow.class);
                    LOGGER.info("Loaded workflow '{}' version '{}' from database", workflow.getId(), workflow.getVersion());
                    return Optional.ofNullable(workflow);
                }
            }
        });
    }

    @Override
    public void save(Workflow workflow) throws Exception {
        Objects.requireNonNull(workflow, "workflow");
        LOGGER.debug("Persisting workflow '{}' to MySQL table '{}'", workflow.getId(), tableName);
        executeWithSchema(() -> {
            try (Connection connection = getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "INSERT INTO " + tableName + " (id, version, definition) VALUES (?, ?, ?) " +
                                 "ON DUPLICATE KEY UPDATE version = VALUES(version), definition = VALUES(definition)")) {
                statement.setString(1, workflow.getId());
                statement.setString(2, workflow.getVersion());
                statement.setString(3, gson.toJson(workflow));
                statement.executeUpdate();
                LOGGER.info("Workflow '{}' persisted to database", workflow.getId());
            }
        });
    }

    @Override
    public Optional<String> getVersion(String id) throws Exception {
        LOGGER.debug("Retrieving version for workflow '{}'", id);
        return executeWithSchema(() -> {
            try (Connection connection = getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "SELECT version FROM " + tableName + " WHERE id = ?")) {
                statement.setString(1, id);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        LOGGER.debug("No workflow found when retrieving version for id '{}'", id);
                        return Optional.empty();
                    }
                    return Optional.ofNullable(resultSet.getString("version"));
                }
            }
        });
    }

    @Override
    public List<String> getAllSourceWorkflows() throws Exception {
        LOGGER.debug("Fetching all workflow ids from table '{}'", tableName);
        return executeWithSchema(() -> {
            try (Connection connection = getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "SELECT id FROM " + tableName + " ORDER BY id")) {
                try (ResultSet resultSet = statement.executeQuery()) {
                    List<String> workflows = new ArrayList<>();
                    while (resultSet.next()) {
                        workflows.add(resultSet.getString("id"));
                    }
                    LOGGER.info("Retrieved {} workflow id(s) from table '{}'", workflows.size(), tableName);
                    return List.copyOf(workflows);
                }
            }
        });
    }

    private <T> T executeWithSchema(SqlSupplier<T> supplier) throws Exception {
        try {
            ensureSchemaInitialized();
            return supplier.get();
        } catch (SQLException ex) {
            String state = ex.getSQLState();
            LOGGER.error("SQL exception while executing workflow loader operation", ex);
            if (state != null && TABLE_NOT_FOUND_STATE.equalsIgnoreCase(state)) {
                LOGGER.warn("Workflow table '{}' missing. Reinitializing schema.", tableName);
                schemaInitialized.set(false);
                ensureSchemaInitialized();
                return supplier.get();
            }
            throw ex;
        }
    }

    private void executeWithSchema(SqlRunnable runnable) throws Exception {
        executeWithSchema(() -> {
            runnable.run();
            return Boolean.TRUE;
        });
    }

    private void parseSourceConfiguration() {
        this.tableName = DEFAULT_TABLE_NAME;
        List<String> statements = new ArrayList<>();

        if (source.data() != null && !source.data().isBlank()) {
            try {
                JsonObject config = JsonParser.parseString(source.data()).getAsJsonObject();
                if (config.has("table")) {
                    tableName = config.get("table").getAsString();
                    LOGGER.debug("Configured workflow table name '{}'", tableName);
                }
                if (config.has("schemaStatements")) {
                    JsonArray array = config.getAsJsonArray("schemaStatements");
                    for (JsonElement element : array) {
                        if (element.isJsonPrimitive()) {
                            statements.add(element.getAsString());
                        }
                    }
                }
            } catch (Exception ex) {
                LOGGER.error("Failed to parse workflow source configuration JSON", ex);
            }
        }

        if (statements.isEmpty()) {
            statements.add(defaultSchemaStatement(tableName));
        }

        List<String> normalizedStatements = new ArrayList<>();
        for (String statement : statements) {
            if (statement != null && !statement.isBlank()) {
                normalizedStatements.add(statement.trim());
            }
        }

        this.schemaStatements = normalizedStatements.isEmpty()
                ? List.of(defaultSchemaStatement(tableName))
                : Collections.unmodifiableList(normalizedStatements);
        LOGGER.debug("Using {} schema statement(s) for table '{}'", this.schemaStatements.size(), tableName);
    }

    private void tryInitializeSchema() {
        try {
            ensureSchemaInitialized();
        } catch (Exception ex) {
            LOGGER.warn("Initial schema creation attempt failed", ex);
        }
    }

    private void ensureSchemaInitialized() throws Exception {
        if (schemaInitialized.get()) {
            return;
        }

        synchronized (schemaLock) {
            if (schemaInitialized.get()) {
                return;
            }

            try (Connection connection = getConnection(); Statement statement = connection.createStatement()) {
                LOGGER.info("Initializing schema for table '{}'", tableName);
                for (String schemaStatement : schemaStatements) {
                    LOGGER.trace("Executing schema statement: {}", schemaStatement);
                    statement.execute(schemaStatement);
                }
                schemaInitialized.set(true);
                LOGGER.info("Schema initialization complete for table '{}'", tableName);
            }
        }
    }

    private Connection getConnection() throws SQLException {
        if (source.uri() == null || source.uri().isBlank()) {
            throw new IllegalStateException("Workflow source URI must be provided for MySQL loader.");
        }
        String username = source.username();
        String password = source.password();
        LOGGER.trace("Obtaining database connection to '{}'", source.uri());
        if (username == null && password == null) {
            return DriverManager.getConnection(source.uri());
        }
        return DriverManager.getConnection(source.uri(), username, password);
    }

    private String defaultSchemaStatement(String table) {
        return "CREATE TABLE IF NOT EXISTS " + table + " (" +
                "id VARCHAR(255) PRIMARY KEY, " +
                "version VARCHAR(64), " +
                "definition LONGTEXT NOT NULL, " +
                "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP" +
                ")";
    }

    private interface SqlSupplier<T> {
        T get() throws Exception;
    }

    private interface SqlRunnable {
        void run() throws Exception;
    }
}
