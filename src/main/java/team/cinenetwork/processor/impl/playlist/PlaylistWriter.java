package team.cinenetwork.processor.impl.playlist;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Запись содержимого плейлиста на диск.
 *
 * <p>Общий для мастер-плейлиста и плейлистов субтитров: единственное место,
 * отвечающее за «положить строку в файл, создав папки».
 *
 * <p>Писатель пишет полученное содержимое как есть и не трактует пустую строку
 * как «пропустить»: решение «писать или нет» принимает вызывающий (билдеры
 * возвращают {@code Optional}). Прежний sentinel «пустое содержимое = пропуск»
 * дублировал проверку в двух слоях и маскировал ошибку: случайно опустошённый
 * контент молча не доезжал до диска.
 */
@Slf4j
@RequiredArgsConstructor
public class PlaylistWriter {

    public void write(@NotNull String content,
                      @NotNull Path outputPath)
            throws IOException {

        Files.createDirectories(outputPath.getParent());
        Files.writeString(outputPath, content,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);

        log.info("Playlist written to {}", outputPath);

    }
}
