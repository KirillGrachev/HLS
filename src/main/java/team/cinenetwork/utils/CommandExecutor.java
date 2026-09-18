package team.cinenetwork.utils;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import team.cinenetwork.exception.ErrorCode;
import team.cinenetwork.exception.ProcessingException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Единственная точка запуска внешних процессов (ffmpeg / ffprobe).
 *
 * <p>Починки рефакторинга по сравнению с прошлой версией:
 * <ul>
 *   <li>процесс <b>всегда</b> гасится ({@code finally → destroy()}): раньше при
 *       исключении FFmpeg мог остаться висеть сиротой;</li>
 *   <li>при ненулевом exit-коде в ошибку попадает <b>хвост вывода</b> (последние
 *       {@value #ERROR_TAIL_LINES} строк) — этого достаточно для диагностики
 *       без вываливания мегабайтов логов;</li>
 *   <li>stdout и stderr объединены ({@code redirectErrorStream}), поэтому прогресс
 *       и ошибки FFmpeg не теряются.</li>
 * </ul>
 */
@Slf4j
@UtilityClass
public class CommandExecutor {

    /** Сколько последних строк вывода держать для сообщения об ошибке. */
    private static final int ERROR_TAIL_LINES = 20;

    /**
     * Запускает команду и возвращает весь её вывод одной строкой.
     * Используется для ffprobe (JSON приходит целиком).
     *
     * @param command   исполняемый файл (ffmpeg/ffprobe)
     * @param arguments аргументы команды
     * @return полный stdout команды
     * @throws IOException при ошибке запуска или ненулевом exit-коде
     */
    public String executeAndCaptureOutput(String command,
                                          List<String> arguments)
            throws IOException {

        List<String> fullCommand = prepare(command, arguments);
        logCommandExecution("Executing command", fullCommand);

        Process process = startProcess(fullCommand);

        try {

            StringBuilder output = new StringBuilder();
            Deque<String> tail = readLines(process, line -> output.append(line).append('\n'));

            finish(process, tail);

            return output.toString();

        } catch (InterruptedException e) {
            throw interrupted(e);

        } finally {
            process.destroy();
        }

    }

    /**
     * Запускает команду и логирует её вывод построчно по мере поступления.
     * Используется для ffmpeg: многочасовой транскод виден в логах в реальном времени.
     *
     * @param command   исполняемый файл (ffmpeg/ffprobe)
     * @param arguments аргументы команды
     * @throws IOException при ошибке запуска или ненулевом exit-коде
     */
    public void executeAndStreamOutput(String command,
                                       List<String> arguments)
            throws IOException {

        List<String> fullCommand = prepare(command, arguments);
        logCommandExecution("Executing command with live output", fullCommand);

        Process process = startProcess(fullCommand);

        try {

            Deque<String> tail = readLines(process,
                    line -> log.info("[{}] {}", fullCommand.getFirst(), line));

            finish(process, tail);

        } catch (InterruptedException e) {
            throw interrupted(e);

        } finally {
            process.destroy();
        }

    }

    /* ---------- Внутренние шаги ---------- */

    /** Проверяет вход и собирает полную команду (исполняемый файл + аргументы). */
    private @NotNull List<String> prepare(String command, List<String> arguments) {

        if (command == null || command.isBlank()) {
            throw ProcessingException.of(ErrorCode.COMMAND_EXECUTION_FAILED,
                    "Command must not be null or empty");
        }

        if (arguments == null) {
            throw ProcessingException.of(ErrorCode.INVALID_COMMAND_ARGUMENTS,
                    "Arguments list must not be null");
        }

        List<String> fullCommand = new ArrayList<>(arguments.size() + 1);
        fullCommand.add(command);
        fullCommand.addAll(arguments);

        return fullCommand;

    }

    /** Стартует процесс с объединёнными stdout/stderr. */
    private @NotNull Process startProcess(List<String> fullCommand) throws IOException {

        return new ProcessBuilder(fullCommand)
                .redirectErrorStream(true)
                .start();

    }

    /**
     * Читает вывод процесса построчно до EOF, передавая каждую строку потребителю
     * (лог или аккумулятор); параллельно держит хвост последних строк для диагностики.
     */
    private @NotNull Deque<String> readLines(@NotNull Process process,
                                             @NotNull Consumer<String> consumer)
            throws IOException {

        Deque<String> tail = new ArrayDeque<>(ERROR_TAIL_LINES + 1);

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {

            String line;
            while ((line = reader.readLine()) != null) {
                consumer.accept(line);
                rememberTailLine(tail, line);
            }

        }

        return tail;

    }

    /** Держит в очереди не больше {@link #ERROR_TAIL_LINES} последних строк. */
    private void rememberTailLine(Deque<String> tail, String line) {

        tail.addLast(line);

        if (tail.size() > ERROR_TAIL_LINES) {
            tail.removeFirst();
        }

    }

    /**
     * Ждёт завершения процесса и проверяет exit-код; при ошибке прикладывает
     * хвост вывода в контекст исключения — обычно этого хватает, чтобы понять,
     * на что ругнулся FFmpeg.
     */
    private void finish(@NotNull Process process,
                        @NotNull Deque<String> outputTail)
            throws InterruptedException {

        int exitCode = process.waitFor();

        if (exitCode != 0) {
            throw ProcessingException.of(ErrorCode.COMMAND_EXECUTION_FAILED,
                    String.format("Command failed with exit code %d", exitCode),
                    Map.of(
                            "exitCode", exitCode,
                            "outputTail", String.join("\n", outputTail)
                    ),
                    null);
        }

    }

    /** Восстанавливает флаг прерывания потока и заворачивает InterruptionException в наше исключение. */
    private @NotNull ProcessingException interrupted(InterruptedException e) {

        Thread.currentThread().interrupt();
        log.warn("Current thread was interrupted during command execution");

        return ProcessingException.of(ErrorCode.EXECUTION_INTERRUPTED,
                "Command execution interrupted", e);

    }

    private void logCommandExecution(String prefix, List<String> command) {
        log.info("{}: {}", prefix, String.join(" ", command));
    }
}
