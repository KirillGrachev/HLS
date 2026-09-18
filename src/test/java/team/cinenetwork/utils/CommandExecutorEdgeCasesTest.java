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
 * Дополнительное покрытие {@link CommandExecutor}: переполнение хвоста вывода
 * и прерывание потока во время ожидания процесса.
 */
class CommandExecutorEdgeCasesTest {

    @Test
    void outputTailKeepsOnlyLastLines() {

        // 30 строк вывода: в контекст ошибки должны попасть только последние 20
        ProcessingException e = assertThrows(ProcessingException.class,
                () -> CommandExecutor.executeAndCaptureOutput("sh",
                        List.of("-c", "for i in $(seq 1 30); do echo line-$i; done; exit 5")));

        String tail = String.valueOf(e.getContext().get("outputTail"));
        assertTrue(tail.contains("line-30"));
        assertTrue(tail.contains("line-11"));
        assertTrue(!tail.contains("line-10"));  // старое вытеснено из кольца

    }

    @Test
    void streamingModeLogsLiveLines() throws IOException {

        // построчный режим: лямбда логирования реально выполняется на каждой строке
        CommandExecutor.executeAndStreamOutput("sh", List.of("-c", "echo one; echo two; exit 0"));

    }

    @Test
    void interruptedThreadOnCapturePathBecomesExecutionInterrupted() {

        Thread.currentThread().interrupt();

        try {

            ProcessingException e = assertThrows(ProcessingException.class,
                    () -> CommandExecutor.executeAndCaptureOutput("sh", List.of("-c", "true")));
            assertEquals(ErrorCode.EXECUTION_INTERRUPTED, e.getCode());

        } finally {
            Thread.interrupted();
        }

    }

    @Test
    void interruptedThreadBecomesExecutionInterrupted() {

        // взводим флаг прерывания заранее: process.waitFor() бросит InterruptedException
        Thread.currentThread().interrupt();

        try {

            ProcessingException e = assertThrows(ProcessingException.class,
                    () -> CommandExecutor.executeAndStreamOutput("sh", List.of("-c", "true")));
            assertEquals(ErrorCode.EXECUTION_INTERRUPTED, e.getCode());

        } finally {
            // снимаем флаг, чтобы не задеть остальные тесты
            Thread.interrupted();
        }

    }
}
