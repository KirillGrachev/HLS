package team.cinenetwork.ffmpeg.exceptions;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serial;
import java.util.Collections;
import java.util.Map;

public class Exception extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull private final ErrorCode code;
    @NotNull private final Map<String, Object> context;

    private Exception(
            @NotNull ErrorCode code,
            @NotNull String message,
            @Nullable Map<String, Object> context,
            @Nullable Throwable cause
    ) {
        super(message, cause);
        this.code = code;
        this.context = context == null ? Collections.emptyMap()
                : Collections.unmodifiableMap(context);
    }

    public static @NotNull Exception of(@NotNull ErrorCode code) {
        return new Exception(code, code.getDefaultMessage(), null, null);
    }

    public static @NotNull Exception of(@NotNull ErrorCode code,
                                        @NotNull String message) {
        return new Exception(code, message, null, null);
    }

    public static @NotNull Exception of(@NotNull ErrorCode code,
                                        @Nullable Map<String, Object> context) {
        return new Exception(code, code.getDefaultMessage(), context, null);
    }

    public static @NotNull Exception of(@NotNull ErrorCode code, @NotNull Throwable cause) {
        return new Exception(code, code.getDefaultMessage(), null, cause);
    }

    public static @NotNull Exception of(@NotNull ErrorCode code,
                                        @Nullable Map<String, Object> context,
                                        @Nullable Throwable cause) {
        return new Exception(code, code.getDefaultMessage(), context, cause);
    }

    public static @NotNull Exception of(@NotNull ErrorCode code,
                                        @NotNull String message,
                                        @Nullable Map<String, Object> context,
                                        @Nullable Throwable cause) {
        return new Exception(code, message, context, cause);
    }

    @NotNull public ErrorCode getCode() { return code; }
    @NotNull public Map<String, Object> getContext() { return context; }
    @NotNull public ErrorCategory getCategory() { return code.getCategory(); }

    @Nullable
    public <T> T getContextValue(@NotNull String key, @NotNull Class<T> type) {
        Object value = context.get(key);
        return type.isInstance(value) ? type.cast(value) : null;
    }

    @Override
    public String toString() {
        return String.format("Exception{code=%s, category=%s, message='%s', context=%s}",
                code, code.getCategory(), getMessage(), context);
    }
}