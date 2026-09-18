package team.cinenetwork;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import picocli.CommandLine;
import team.cinenetwork.exception.ProcessingException;
import team.cinenetwork.options.AppOptions;
import team.cinenetwork.processor.VideoProcessor;

/**
 * Точка входа приложения.
 *
 * <p>Ответственность класса сознательно минимальна: разобрать аргументы → собрать
 * процессор → запустить → превратить результат в код выхода процесса.
 * Вся логика живёт в соответствующих слоях.
 *
 * <p>Коды выхода (задокументированы в README): 0 — успех (или показ --help/--version);
 * 1 — ошибка обработки (источник, FFmpeg, файловая система); 2 — ошибка аргументов CLI.
 */
@Slf4j
public class Main {

    public static void main(String[] args) {

        AppOptions options = parseCommandLine(args);
        if (options == null) return;

        try {

            VideoProcessor processor = createVideoProcessor(options);
            processor.process();

            log.info("Video processing completed successfully");

        } catch (ProcessingException e) {

            log.error("Processing failed: [{}] {}", e.getCode(), e.getMessage());
            log.debug("Failure details:", e);

            System.exit(1);

        }

    }

    /**
     * Разбирает аргументы и валидирует минимально необходимый набор (вход и выход).
     *
     * @return готовые опции, либо {@code null}, если запрошен help/version
     *         (справка уже напечатана)
     */
    private static @Nullable AppOptions parseCommandLine(String[] args) {

        CommandLine commandLine = new CommandLine(new AppOptions());

        try {

            commandLine.parseArgs(args);

        } catch (CommandLine.ParameterException e) {

            log.error("Invalid command line arguments: {}", e.getMessage());
            commandLine.usage(System.out);

            System.exit(2);

        }

        if (commandLine.isUsageHelpRequested()) {
            commandLine.usage(System.out);
            return null;
        }

        if (commandLine.isVersionHelpRequested()) {
            commandLine.printVersionHelp(System.out);
            return null;
        }

        AppOptions options = commandLine.getCommand();

        if (options.getInput() == null) {
            log.error("Input file is not specified: use --input <file> or a positional argument");
            commandLine.usage(System.out);
            System.exit(2);
        }

        if (options.getOutput() == null) {
            log.error("Output directory is not specified: use --output <dir>");
            commandLine.usage(System.out);
            System.exit(2);
        }

        return options;

    }

    /**
     * Composition root: связывает процессор с зависимостями.
     * Выделен в метод, чтобы в тестах процессор можно было собрать со стабами.
     */
    private static VideoProcessor createVideoProcessor(AppOptions options) {
        return VideoProcessor.create(options);
    }
}
