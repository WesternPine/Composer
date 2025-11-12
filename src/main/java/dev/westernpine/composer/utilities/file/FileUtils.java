package dev.westernpine.composer.utilities.file;

import java.io.*;
import java.util.Optional;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileUtils.class);

    public static boolean create(File file) throws IOException {
        LOGGER.debug("Creating file at {}", file.getAbsolutePath());
        boolean createdDirectories = file.getParentFile() != null && file.getParentFile().exists();
        if (!createdDirectories && file.getParentFile() != null) {
            createdDirectories = file.getParentFile().mkdirs();
            LOGGER.trace("Created directories for {}: {}", file.getAbsolutePath(), createdDirectories);
        }
        boolean createdFile = file.createNewFile();
        LOGGER.info("File {} created: {}", file.getAbsolutePath(), createdFile);
        return createdFile && (createdDirectories || file.getParentFile() == null);
    }

    public static void save(File file, String content) throws Exception {
        LOGGER.debug("Saving content to {}", file.getAbsolutePath());
        Optional<Exception> failure = asFileOutputStream(file, fos -> {
            try {
                fos.write(content.getBytes());
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        });
        if(failure.isPresent()) {
            LOGGER.error("Failed to save file {}", file.getAbsolutePath(), failure.get());
            throw failure.get();
        }
        LOGGER.info("Successfully saved file {}", file.getAbsolutePath());
    }

    public static String read(File file) throws Exception {
        LOGGER.debug("Reading file {}", file.getAbsolutePath());
        StringBuilder content = new StringBuilder();
        Optional<Exception> failure = asBufferedReader(file, reader -> reader.lines().forEach(line -> content.append(line).append("\r\n")));
        if(failure.isPresent()) {
            LOGGER.error("Failed to read file {}", file.getAbsolutePath(), failure.get());
            throw failure.get();
        }
        LOGGER.info("Successfully read file {}", file.getAbsolutePath());
        return content.toString();
    }

    public static Optional<Exception> asFileOutputStream(File file, Consumer<OutputStream> consumer) {
        try (FileOutputStream fos = new FileOutputStream(file)) {
            consumer.accept(fos);
        } catch (Exception e) {
            LOGGER.error("Exception while handling FileOutputStream for {}", file.getAbsolutePath(), e);
            return Optional.of(e);
        }
        return Optional.empty();
    }

    public static Optional<Exception> asBufferedReader(File file, Consumer<BufferedReader> consumer) {
        try(FileReader fileReader = new FileReader(file)) {
            try (BufferedReader bufferedReader = new BufferedReader(fileReader)) {
                consumer.accept(bufferedReader);
            }
        } catch (Exception e) {
            LOGGER.error("Exception while handling BufferedReader for {}", file.getAbsolutePath(), e);
            return Optional.of(e);
        }
        return Optional.empty();
    }

}
