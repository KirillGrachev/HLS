package team.cinenetwork.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import team.cinenetwork.exception.ErrorCode;
import team.cinenetwork.exception.ProcessingException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Тесты {@link FileUtil}: подготовка директорий и чистка по шаблону.
 */
class FileUtilTest {

    @TempDir
    Path tempDir;

    @Test
    void createsMissingDirectory() throws IOException {

        Path target = tempDir.resolve("a/b/c");

        FileUtil.prepareOutputDirectory(target, false);

        assertTrue(Files.isDirectory(target));

    }

    @Test
    void reusesExistingOnlyWithOverwriteFlag() throws IOException {

        Path target = tempDir.resolve("out");
        Files.createDirectories(target);

        // с флагом перезаписи — переиспользуем
        FileUtil.prepareOutputDirectory(target, true);
        assertTrue(Files.isDirectory(target));

        // без флага — защищаем чужие данные
        ProcessingException e = assertThrows(ProcessingException.class,
                () -> FileUtil.prepareOutputDirectory(target, false));
        assertTrue(e.getCode() == ErrorCode.DIRECTORY_EXISTS);

    }

    @Test
    void deleteFilesByPatternTouchesOnlyMatchingRegularFiles() throws IOException {

        Path out = tempDir.resolve("out");
        Files.createDirectories(out.resolve("subdir"));
        Files.writeString(out.resolve("_temp.ts"), "x");
        Files.writeString(out.resolve("playlist.m3u8"), "x");
        Files.writeString(out.resolve("subdir/_nested.ts"), "x");

        FileUtil.deleteFilesByPattern(out, "_*");

        assertFalse(Files.exists(out.resolve("_temp.ts")));
        assertTrue(Files.exists(out.resolve("playlist.m3u8")));
        assertTrue(Files.exists(out.resolve("subdir/_nested.ts")));  // поддиректории не трогаем

    }

    @Test
    void missingDirectoryIsNotAnErrorForCleanup() throws IOException {

        // чистка несуществующей директории — warn, а не падение
        FileUtil.deleteFilesByPattern(tempDir.resolve("nope"), "_*");

    }

    @Test
    void nullPathIsValidationError() {

        ProcessingException e = assertThrows(ProcessingException.class,
                () -> FileUtil.prepareOutputDirectory(null, true));
        assertTrue(e.getCode() == ErrorCode.NULL_PATH_PROVIDED);

    }
}
