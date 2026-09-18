package team.cinenetwork.exception;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serial;
import java.util.Collections;
import java.util.Map;

/**
 * Единственное исключение приложения.
 *
 * <p>Раньше класс назывался {@code Exception} и затенял {@code java.lang.Exception} —
 * отсюда полностью квалифицированные имена по всему коду. Переименование в
 * {@code ProcessingException} убрало затенение целиком.
 *
 * <p>Исключение unchecked: сбои FFmpeg/IO неустранимы, они сознательно поднимаются
 * до {@code Main}, который превращает их в код выхода процесса.
 *
 * <p>Создание — только через фабрики {@link #of}:
 * <pre>{@code
 * throw ProcessingException.of(ErrorCode.NO_VIDEO_STREAM);
 * throw ProcessingException.of(ErrorCode.COMMAND_EXECUTION_FAILED,
 *         "ffprobe failed", Map.of("exitCode", 1), cause);
 * }</pre>
 */
public class ProcessingException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Машинный код ошибки (для метрик и логов). */
    @NotNull
    private final ErrorCode code;

    /** Структурированный контекст ошибки: exit-коды, пути, сырые значения. */
    @NotNull
    private final Map<String, Object> context;

    private ProcessingException(@NotNull ErrorCode code,
                                @NotNull String message,
                                @Nullable Map<String, Object> context,
                                @Nullable Throwable cause) {
        super(message, cause);
        this.code = code;
        this.context = context == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(context);
    }

    /* ---------- Фабрики: от частых к полным ---------- */

    public static @NotNull ProcessingException of(@NotNull ErrorCode code) {
        return new ProcessingException(code, code.getDefaultMessage(), null, null);
    }

    public static @NotNull ProcessingException of(@NotNull ErrorCode code,
                                                  @NotNull String message) {
        return new ProcessingException(code, message, null, null);
    }

    public static @NotNull ProcessingException of(@NotNull ErrorCode code,
                                                  @NotNull Throwable cause) {
        return new ProcessingException(code, code.getDefaultMessage(), null, cause);
    }

    public static @NotNull ProcessingException of(@NotNull ErrorCode code,
                                                  @NotNull String message,
                                                  @Nullable Throwable cause) {
        return new ProcessingException(code, message, null, cause);
    }

    public static @NotNull ProcessingException of(@NotNull ErrorCode code,
                                                  @Nullable Map<String, Object> context) {
        return new ProcessingException(code, code.getDefaultMessage(), context, null);
    }

    public static @NotNull ProcessingException of(@NotNull ErrorCode code,
                                                  @NotNull String message,
                                                  @Nullable Map<String, Object> context,
                                                  @Nullable Throwable cause) {
        return new ProcessingException(code, message, context, cause);
    }

    /* ---------- Доступы ---------- */

    /** Код ошибки. */
    public @NotNull ErrorCode getCode() {
        return code;
    }

    /** Неизменяемый контекст ошибки. */
    public @NotNull Map<String, Object> getContext() {
        return context;
    }

    /** Категория ошибки (shortcut до {@code code.getCategory()}). */
    public @NotNull ErrorCategory getCategory() {
        return code.getCategory();
    }

    /**
     * Типобезопасное чтение значения контекста.
     *
     * @param key  ключ контекста, например {@code "exitCode"}
     * @param type ожидаемый тип значения
     * @return значение или {@code null}, если ключа нет или тип не совпал
     */
    public @Nullable <T> T getContextValue(@NotNull String key, @NotNull Class<T> type) {

        Object value = context.get(key);

        return type.isInstance(value) ? type.cast(value) : null;

    }

    @Override
    public String toString() {
        return String.format("ProcessingException{code=%s, category=%s, message='%s', context=%s}",
                code, getCategory(), getMessage(), context);
    }
}
