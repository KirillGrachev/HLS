package team.cinenetwork.utils;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
@UtilityClass
public class FileUtil {

    public void prepareOutputDirectory(Path directoryPath,
                                       boolean overwriteExisting)
            throws IOException {

        validatePathNotNull(directoryPath);

        if (directoryExists(directoryPath)) {
            handleExistingDirectory(directoryPath, overwriteExisting);
        } else {
            createNewDirectory(directoryPath);
        }
    }

    public void deleteFilesByPattern(Path targetDirectory,
                                     String filenamePattern)
            throws IOException {

        validatePathNotNull(targetDirectory);
        validateIsDirectory(targetDirectory);

        if (!Files.exists(targetDirectory)) {

            log.warn("Attempted to delete files in non-existent directory: {}",
                    targetDirectory);

            return;

        }

        try (var directoryStream = Files.newDirectoryStream(targetDirectory,
                filenamePattern)) {
            deleteMatchingFiles(directoryStream);
        }

    }

    private boolean directoryExists(Path path) {
        return Files.exists(path);
    }

    private void handleExistingDirectory(Path path,
                                         boolean overwrite)
            throws IOException {

        if (overwrite) {
            log.info("Directory already exists. Overwrite is enabled, reusing: {}", path);
        } else {
            throw new IOException(String.format(
                    "Directory already exists and overwrite is disabled. Path: %s",
                    path
            ));
        }
    }

    private void createNewDirectory(Path path) throws IOException {

        log.debug("Creating new directory structure: {}", path);
        Files.createDirectories(path);

    }

    private void deleteMatchingFiles(@NotNull DirectoryStream<Path> directoryStream)
            throws IOException {

        for (Path filePath : directoryStream) {

            if (Files.isRegularFile(filePath)) {
                deleteSingleFile(filePath);
            }

        }

    }

    private void deleteSingleFile(Path filePath)
            throws IOException {

        log.debug("Deleting file: {}", filePath);
        Files.deleteIfExists(filePath);

    }

    private void validatePathNotNull(Path path) {
        if (path == null) {
            throw new IllegalArgumentException("Path cannot be null");
        }
    }

    private void validateIsDirectory(Path path) {
        if (!Files.isDirectory(path)) {
            throw new IllegalArgumentException(
                    "Specified path is not a directory: " + path
            );
        }
    }
}