package team.cinenetwork.exception;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Тесты единственного исключения приложения: все фабрики, контекст, категория.
 */
class ProcessingExceptionTest {

    @Test
    void factoryWithCodeOnlyUsesDefaultMessage() {

        ProcessingException e = ProcessingException.of(ErrorCode.NO_VIDEO_STREAM);

        assertEquals(ErrorCode.NO_VIDEO_STREAM, e.getCode());
        assertEquals(ErrorCode.NO_VIDEO_STREAM.getDefaultMessage(), e.getMessage());
        assertTrue(e.getContext().isEmpty());
        assertNull(e.getCause());

    }

    @Test
    void factoryWithMessageAndContext() {

        ProcessingException e = ProcessingException.of(ErrorCode.COMMAND_EXECUTION_FAILED,
                "ffprobe failed", Map.of("exitCode", 3), null);

        assertEquals("ffprobe failed", e.getMessage());
        assertEquals(3, e.getContextValue("exitCode", Integer.class));
        assertEquals(ErrorCategory.PROCESSING, e.getCategory());

    }

    @Test
    void factoryWithCauseKeepsIt() {

        IOException cause = new IOException("boom");
        ProcessingException e = ProcessingException.of(ErrorCode.VIDEO_PROCESSING_FAILED, cause);

        assertSame(cause, e.getCause());
        assertEquals(ErrorCode.VIDEO_PROCESSING_FAILED.getDefaultMessage(), e.getMessage());

    }

    @Test
    void contextIsImmutableAndTypeSafe() {

        ProcessingException e = ProcessingException.of(ErrorCode.COMMAND_EXECUTION_FAILED,
                Map.of("exitCode", 1));

        // чужой тип и отсутствующий ключ дают null, а не ClassCastException
        assertNull(e.getContextValue("exitCode", String.class));
        assertNull(e.getContextValue("missing", Integer.class));

    }

    @Test
    void toStringContainsCodeAndCategory() {

        String text = ProcessingException.of(ErrorCode.DIRECTORY_EXISTS, "dir exists").toString();

        assertTrue(text.contains("DIRECTORY_EXISTS"));
        assertTrue(text.contains("VALIDATION"));
        assertTrue(text.contains("dir exists"));

    }
}
