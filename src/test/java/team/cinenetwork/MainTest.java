package team.cinenetwork;

import org.junit.jupiter.api.Test;

import java.security.Permission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Тесты точки входа: справки не завершают процесс, ошибки аргументов и обработки
 * дают документированные коды выхода (2 и 1).
 *
 * <p>{@code System.exit} перехватывается SecurityManager-ловушкой: JVM для тестов
 * запускается с {@code -Djava.security.manager=allow} (см. argLine surefire в pom.xml).
 * SecurityManager deprecated for removal — отсюда {@code @SuppressWarnings("removal")}:
 * альтернативы для проверки кодов выхода in-process на Java 21 нет.
 */
@SuppressWarnings("removal")
class MainTest {

    @Test
    void helpAndVersionDoNotExit() {

        // ни один вызов не должен бросить ExitCalled
        Main.main(new String[]{"--help"});
        Main.main(new String[]{"--version"});

    }

    @Test
    void missingInputExitsWithCode2() {
        assertExitCode(2, () -> Main.main(new String[]{}));
    }

    @Test
    void missingOutputExitsWithCode2() {
        assertExitCode(2, () -> Main.main(new String[]{"--input", "source.mkv"}));
    }

    @Test
    void invalidParameterValueExitsWithCode2() {
        assertExitCode(2, () -> Main.main(new String[]{"--hls-time", "not-a-number"}));
    }

    @Test
    void processingFailureExitsWithCode1() {

        assertExitCode(1, () -> Main.main(new String[]{
                "--input", "source.mkv",
                "--output", "/tmp/main-test-out",
                "--ffprobe", "/definitely/missing/ffprobe"
        }));

    }

    /* ---------- Перехват System.exit ---------- */

    private void assertExitCode(int expected, Runnable action) {

        SecurityManager previous = System.getSecurityManager();
        System.setSecurityManager(new ExitTrap());

        try {

            action.run();
            fail("Ожидали System.exit(" + expected + "), но процесс не попытался завершиться");

        } catch (ExitTrap.ExitCalled exit) {
            assertEquals(expected, exit.status());

        } finally {
            System.setSecurityManager(previous);
        }

    }

    /** SecurityManager-ловушка: всё разрешено, кроме выхода из JVM. */
    private static final class ExitTrap extends SecurityManager {

        /** Сигнал «код пытался вызвать System.exit(status)». */
        static final class ExitCalled extends SecurityException {

            private final int status;

            ExitCalled(int status) {
                super("System.exit(" + status + ")");
                this.status = status;
            }

            int status() {
                return status;
            }
        }

        @Override
        public void checkExit(int status) {
            throw new ExitCalled(status);
        }

        @Override
        public void checkPermission(Permission permission) {
            // разрешаем всё остальное
        }
    }
}
