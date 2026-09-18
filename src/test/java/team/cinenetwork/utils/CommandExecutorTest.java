package team.cinenetwork.utils;

import org.junit.jupiter.api.Test;
import team.cinenetwork.exception.ErrorCode;
import team.cinenetwork.exception.ProcessingException;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Тесты {@link CommandExecutor}: захват вывода, стриминг, обработка ненулевого exit-кода.
 */
class CommandExecutorTest {

    @Test
    void capturesWholeOutput() throws IOException {

        String output = CommandExecutor.executeAndCaptureOutput("sh", List.of("-c", "echo hello; echo world"));

        assertTrue(output.contains("hello"));
        assertTrue(output.contains("world"));

    }

    @Test
    void nonZeroExitCodeBecomesProcessingExceptionWithTail() {

        ProcessingException e = assertThrows(ProcessingException.class,
                () -> CommandExecutor.executeAndCaptureOutput("sh", List.of("-c", "echo boom; exit 3")));

        assertEquals(ErrorCode.COMMAND_EXECUTION_FAILED, e.getCode());
        assertEquals(3, e.getContextValue("exitCode", Integer.class));
        assertTrue(String.valueOf(e.getContext().get("outputTail")).contains("boom"));

    }

    @Test
    void streamingModeAlsoValidatesExitCode() {

        ProcessingException e = assertThrows(ProcessingException.class,
                () -> CommandExecutor.executeAndStreamOutput("sh", List.of("-c", "exit 7")));

        assertEquals(7, e.getContextValue("exitCode", Integer.class));

    }

    @Test
    void emptyCommandIsValidationError() {

        ProcessingException e = assertThrows(ProcessingException.class,
                () -> CommandExecutor.executeAndCaptureOutput("  ", List.of()));
        assertEquals(ErrorCode.COMMAND_EXECUTION_FAILED, e.getCode());

        ProcessingException e2 = assertThrows(ProcessingException.class,
                () -> CommandExecutor.executeAndCaptureOutput("sh", null));
        assertEquals(ErrorCode.INVALID_COMMAND_ARGUMENTS, e2.getCode());

    }
}
