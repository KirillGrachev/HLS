package team.cinenetwork.utils;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

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
            throw new IOException("Command execution interrupted", e);

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
            throw new IOException("Command streaming interrupted", e);

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
            throw new IllegalArgumentException("Command must not be null or empty");
        }

        if (arguments == null) {
            throw new IllegalArgumentException("Arguments list must not be null");
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
            throws IOException {

        if (exitCode != 0) {

            throw new IOException(String.format(
                    "Command failed with exit code %d. Error context: %s",
                    exitCode,
                    errorContext
            ));

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