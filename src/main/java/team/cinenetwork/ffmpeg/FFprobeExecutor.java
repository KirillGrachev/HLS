package team.cinenetwork.ffmpeg;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import team.cinenetwork.options.AppOptions;
import team.cinenetwork.utils.CommandExecutor;

import java.io.IOException;
import java.util.List;

/**
 * Запуск <b>ffprobe</b>: получение метаданных исходника в JSON.
 *
 * <p>Отделён от {@link FFmpegExecutor} при рефакторинге: probe выполняется до расчёта
 * профиля кодирования и не должен от него зависеть (раньше для обхода создавали
 * «null»-executor и гоняли ffprobe дважды).
 */
@Slf4j
@RequiredArgsConstructor
public class FFprobeExecutor {

    private final AppOptions options;

    /**
     * @return сырой вывод ffprobe (JSON: format + все потоки, включая субтитры)
     * @throws IOException при ошибке запуска или ненулевом exit-коде
     */
    public @NotNull String probeMediaInfo() throws IOException {

        List<String> command = List.of(
                "-v", "quiet",
                "-print_format", "json",
                "-show_format",
                "-show_streams",
                options.getInput().toString()
        );

        log.debug("Executing media probe command: {}", String.join(" ", command));

        return CommandExecutor.executeAndCaptureOutput(options.getFfprobe(), command);

    }
}
