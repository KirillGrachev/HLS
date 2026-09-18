package team.cinenetwork.ffmpeg.builder.impl;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Контракт стратегии сборки команды FFmpeg (паттерн Strategy).
 *
 * <p>Каждый сценарий обработки (ступень лестницы, аудио-рендишен, субтитры, постер,
 * MP4) — отдельный класс с одним методом {@link #build()}: новый сценарий не трогает
 * существующие (OCP), а каждую команду можно тестировать изолированно.
 */
public interface FFmpegCommandStrategy {

    /**
     * Собирает полный список аргументов ffmpeg (без имени исполняемого файла).
     *
     * @return список аргументов в порядке передачи процессу
     */
    @NotNull List<String> build();

}
