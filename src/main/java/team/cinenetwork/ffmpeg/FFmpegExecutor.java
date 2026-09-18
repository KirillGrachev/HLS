package team.cinenetwork.ffmpeg;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import team.cinenetwork.ffmpeg.type.ProcessingType;
import team.cinenetwork.model.VideoInfo;
import team.cinenetwork.options.AppOptions;
import team.cinenetwork.processor.EncodingProfile;
import team.cinenetwork.utils.CommandExecutor;

import java.io.IOException;
import java.util.List;

/**
 * Запуск <b>ffmpeg</b>: выполнение набора команд заданного типа обработки.
 *
 * <p>Сам класс не собирает ни одного аргумента: сборка команд — ответственность
 * стратегий ({@code ffmpeg.builder.*}), выбор нужного набора стратегий —
 * {@link FFmpegCommandCoordinator}. Роль исполнителя — «список → запуск → проверка
 * кода», т.е. SRP в чистом виде.
 */
@Slf4j
public class FFmpegExecutor {

    private final AppOptions options;

    /** Диспетчер стратегий: знает, какие команды нужны для каждого типа обработки. */
    private final FFmpegCommandCoordinator coordinator;

    public FFmpegExecutor(@NotNull AppOptions options,
                          @NotNull VideoInfo videoInfo,
                          @NotNull EncodingProfile profile) {

        this.options = options;
        this.coordinator = new FFmpegCommandCoordinator(options, videoInfo, profile);

    }

    /**
     * Последовательно выполняет все команды указанного этапа обработки.
     *
     * @param type тип обработки (постер, транскод, аудио, субтитры, single-file)
     * @throws IOException если хотя бы одна команда вернула ненулевой exit-код
     *                     (в контексте исключения будет хвост вывода)
     */
    public void executeMediaProcessing(@NotNull ProcessingType type) throws IOException {

        List<List<String>> commands = switch (type) {
            case POSTER -> coordinator.buildPosterCommands();
            case TRANSCODE -> coordinator.buildTranscodeCommands();
            case AUDIO -> coordinator.buildAudioRenditionCommands();
            case SUBTITLES -> coordinator.buildSubtitleCommands();
            case SINGLE_FILE -> coordinator.buildSingleFileCommands();
        };

        if (commands.isEmpty()) {
            log.warn("No FFmpeg commands to run for processing type {}", type);
            return;
        }

        for (List<String> command : commands) {
            log.info("Executing FFmpeg command: {}", String.join(" ", command));
            CommandExecutor.executeAndStreamOutput(options.getFfmpeg(), command);
        }

    }
}
