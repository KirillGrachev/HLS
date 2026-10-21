package team.cinenetwork.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import team.cinenetwork.exception.ErrorCode;
import team.cinenetwork.exception.ProcessingException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Подготовка выходной директории на настоящей файловой системе.
 *
 * <p>Дополняет {@code FileUtilTest}: проверяет сценарий, при котором по пути
 * вывода лежит обычный файл. Раньше он попадал в ветку «переиспользую
 * существующую директорию», метод рапортовал успех, а падение происходило позже —
 * на первой записи плейлиста, уже после чтения исходника и создания части выходных
 * файлов, поэтому диагностика указывала не на причину.
 */
class FileUtilOutputDirectoryTest {

    @Test
    void createsDirectoryWithParents(@TempDir Path temp) throws IOException {

        Path target = temp.resolve("a").resolve("b").resolve("out");

        FileUtil.prepareOutputDirectory(target, false);

        assertTrue(Files.isDirectory(target));

    }

    @Test
    void reusesExistingDirectoryWhenOverwriteEnabled(@TempDir Path temp) throws IOException {

        Path target = temp.resolve("out");
        Files.createDirectories(target);
        Files.writeString(target.resolve("keep.m3u8"), "#EXTM3U");

        FileUtil.prepareOutputDirectory(target, true);

        assertTrue(Files.isDirectory(target));
        assertTrue(Files.exists(target.resolve("keep.m3u8")),
                "переиспользование не должно удалять содержимое");

    }

    @Test
    void refusesExistingDirectoryWhenOverwriteDisabled(@TempDir Path temp) throws IOException {

        Path target = temp.resolve("out");
        Files.createDirectories(target);

        ProcessingException e = assertThrows(ProcessingException.class,
                () -> FileUtil.prepareOutputDirectory(target, false));

        assertEquals(ErrorCode.DIRECTORY_EXISTS, e.getCode());

    }

    @Test
    void refusesFilePathRegardlessOfOverwriteFlag(@TempDir Path temp) throws IOException {

        Path file = temp.resolve("not-a-directory");
        Files.writeString(file, "occupied");

        // Оба значения флага должны отклоняться: overwrite означает «можно
        // переиспользовать директорию», а не «можно затереть файл».
        for (boolean overwrite : new boolean[]{false, true}) {
            ProcessingException e = assertThrows(ProcessingException.class,
                    () -> FileUtil.prepareOutputDirectory(file, overwrite),
                    "путь-файл принят при overwrite=" + overwrite);

            assertEquals(ErrorCode.INVALID_DIRECTORY_PATH, e.getCode());
            assertTrue(Files.isRegularFile(file), "файл не должен быть изменён");

        }

    }

    @Test
    void rejectsNullPath() {

        ProcessingException e = assertThrows(ProcessingException.class,
                () -> FileUtil.prepareOutputDirectory(null, true));

        assertEquals(ErrorCode.NULL_PATH_PROVIDED, e.getCode());

    }

    @Test
    void deleteFilesByPatternWarnsInsteadOfFailingOnMissingDirectory(@TempDir Path temp)
            throws IOException {

        // Несуществующая директория — нормальный сценарий чистки временных файлов,
        // поэтому она не должна ронять пайплайн.
        FileUtil.deleteFilesByPattern(temp.resolve("absent"), "_*");

        assertTrue(Files.notExists(temp.resolve("absent")));

    }

    @Test
    void deleteFilesByPatternRemovesOnlyMatchingRegularFiles(@TempDir Path temp) throws IOException {

        Files.createDirectories(temp.resolve("sub"));
        Files.writeString(temp.resolve("_temp.ts"), "x");
        Files.writeString(temp.resolve("playlist.m3u8"), "#EXTM3U");
        Files.createDirectories(temp.resolve("_tempdir"));

        FileUtil.deleteFilesByPattern(temp, "_*");

        assertTrue(Files.notExists(temp.resolve("_temp.ts")), "временный файл должен быть удалён");
        assertTrue(Files.exists(temp.resolve("playlist.m3u8")), "плейлист удалён быть не должен");
        assertTrue(Files.isDirectory(temp.resolve("_tempdir")),
                "поддиректории, даже подходящие под шаблон, не удаляются");

    }

    @Test
    void deleteFilesByPatternRejectsFilePath(@TempDir Path temp) throws IOException {

        Path file = temp.resolve("a-file");
        Files.writeString(file, "x");

        ProcessingException e = assertThrows(ProcessingException.class,
                () -> FileUtil.deleteFilesByPattern(file, "_*"));

        assertEquals(ErrorCode.INVALID_DIRECTORY_PATH, e.getCode());

    }

}
