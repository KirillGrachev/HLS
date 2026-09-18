package team.cinenetwork.utils;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import team.cinenetwork.exception.ErrorCode;
import team.cinenetwork.exception.ProcessingException;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Файловые операции пайплайна: подготовка выходной директории и чистка временных файлов.
 */
@Slf4j
@UtilityClass
public class FileUtil {

    /**
     * Готовит выходную директорию: не существует — создаёт (вместе с родительскими);
     * существует и {@code overwriteExisting} — переиспользует; существует и перезапись
     * запрещена — бросает {@code DIRECTORY_EXISTS}, чтобы молча не уничтожить чужие данные.
     *
     * @param directoryPath    путь выходной директории
     * @param overwriteExisting разрешено ли переиспользовать существующую директорию
     * @throws IOException при ошибке создания директории
     */
    public void prepareOutputDirectory(Path directoryPath,
                                       boolean overwriteExisting)
            throws IOException {

        validatePathNotNull(directoryPath);

        if (Files.exists(directoryPath)) {
            handleExistingDirectory(directoryPath, overwriteExisting);
        } else {
            createNewDirectory(directoryPath);
        }

    }

    /**
     * Удаляет обычные файлы по glob-шаблону (например {@code "_*"} — временные файлы FFmpeg);
     * поддиректории не трогает.
     *
     * @param targetDirectory  директория для чистки
     * @param filenamePattern  glob-шаблон имён файлов
     * @throws IOException при ошибке чтения директории
     */
    public void deleteFilesByPattern(Path targetDirectory,
                                     String filenamePattern)
            throws IOException {

        validatePathNotNull(targetDirectory);

        if (!Files.exists(targetDirectory)) {
            log.warn("Attempted to delete files in non-existent directory: {}", targetDirectory);
            return;
        }

        validateIsDirectory(targetDirectory);

        try (DirectoryStream<Path> directoryStream =
                     Files.newDirectoryStream(targetDirectory, filenamePattern)) {
            deleteMatchingFiles(directoryStream);
        }

    }

    /* ---------- Внутренние шаги ---------- */

    private void handleExistingDirectory(Path path, boolean overwrite) {

        if (overwrite) {
            log.info("Directory already exists. Overwrite is enabled, reusing: {}", path);
        } else {
            throw ProcessingException.of(ErrorCode.DIRECTORY_EXISTS, String.format(
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

    private void deleteSingleFile(Path filePath) throws IOException {

        log.debug("Deleting temporary file: {}", filePath);
        Files.deleteIfExists(filePath);

    }

    /* ---------- Валидации ---------- */

    private void validatePathNotNull(Path path) {
        if (path == null) {
            throw ProcessingException.of(ErrorCode.NULL_PATH_PROVIDED, "Path cannot be null");
        }
    }

    private void validateIsDirectory(Path path) {
        if (!Files.isDirectory(path)) {
            throw ProcessingException.of(ErrorCode.INVALID_DIRECTORY_PATH,
                    "Specified path is not a directory: " + path);
        }
    }
}
