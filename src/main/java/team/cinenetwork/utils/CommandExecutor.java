package team.cinenetwork.utils;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.NotNull;
import team.cinenetwork.ffmpeg.exceptions.ErrorCode;
import team.cinenetwork.ffmpeg.exceptions.Exception;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@UtilityClass
public class CommandExecutor {

    public String executeAndCaptureOutput(String command, List<String> arguments)
            throws IOException {

        validateInput(command, arguments);

        List<String> fullCommand = buildFullCommand(command, arguments);
        logCommandExecution("Executing command", fullCommand);

        Process process = startProcess(fullCommand);

        try (InputStream processOutput = process.getInputStream()) {

            String output = IOUtils.toString(processOutput, StandardCharsets.UTF_8);

            int exitCode = waitForProcessCompletion(process);
            validateExitCode(exitCode, output);

            return output;

        } catch (InterruptedException e) {

            handleThreadInterruption();
            throw Exception.of(ErrorCode.EXECUTION_INTERRUPTED,
                    "Command execution interrupted", e);

        }
    }

    public void executeAndStreamOutput(String command, List<String> arguments)
            throws IOException {

        validateInput(command, arguments);

        List<String> fullCommand = buildFullCommand(command, arguments);
        logCommandExecution("Executing command with live output",
                fullCommand);

        Process process = startProcess(fullCommand);

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(),
                        StandardCharsets.UTF_8))) {

            streamProcessOutput(command, reader);
            int exitCode = waitForProcessCompletion(process);
            validateExitCode(exitCode, "Command produced error output");

        } catch (InterruptedException e) {

            handleThreadInterruption();
            throw Exception.of(ErrorCode.EXECUTION_INTERRUPTED,
                    "Command streaming interrupted", e);

        }
    }

    private @NotNull List<String> buildFullCommand(String command, List<String> arguments) {

        List<String> fullCommand = new ArrayList<>();

        fullCommand.add(command);
        fullCommand.addAll(arguments);

        return fullCommand;

    }

    private void validateInput(String command, List<String> arguments) {

        if (command == null || command.trim().isEmpty()) {
            throw Exception.of(ErrorCode.COMMAND_EXECUTION_FAILED,
                    "Command must not be null or empty");
        }

        if (arguments == null) {
            throw Exception.of(ErrorCode.INVALID_COMMAND_ARGUMENTS,
                    "Arguments list must not be null");
        }

    }

    private @NotNull Process startProcess(List<String> command)
            throws IOException {
        return new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();
    }

    private void logCommandExecution(String prefix, List<String> command) {
        log.info("{}: {}", prefix, String.join(" ", command));
    }

    private int waitForProcessCompletion(@NotNull Process process)
            throws InterruptedException {
        return process.waitFor();
    }

    private void validateExitCode(int exitCode, String errorContext)
            throws Exception {

        if (exitCode != 0) {

            throw Exception.of(ErrorCode.COMMAND_EXECUTION_FAILED,
                    String.format("Command failed with exit code %d. Context: %s",
                            exitCode, errorContext), Map.of(
                            "exitCode", exitCode,
                            "context", errorContext
                    ), null
            );

        }
    }

    private void streamProcessOutput(String command, @NotNull BufferedReader reader)
            throws IOException {

        String outputLine;
        while ((outputLine = reader.readLine()) != null) {
            log.info("[{}] {}", command, outputLine);
        }

    }

    private void handleThreadInterruption() {

        Thread.currentThread().interrupt();
        log.warn("Current thread was interrupted during command execution");

    }
}