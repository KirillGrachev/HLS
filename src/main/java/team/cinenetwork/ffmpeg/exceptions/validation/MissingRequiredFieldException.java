package team.cinenetwork.ffmpeg.exceptions.validation;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MissingRequiredFieldException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    @Nullable
    private final String fieldName;

    public MissingRequiredFieldException(@NotNull String message) {
        this(message, null, null);
    }

    public MissingRequiredFieldException(@NotNull String message,
                                         @Nullable Throwable cause) {
        this(message, null, cause);
    }

    public MissingRequiredFieldException(@NotNull String message,
                                         @Nullable String fieldName) {
        this(message, fieldName, null);
    }

    public MissingRequiredFieldException(
            @NotNull String message,
            @Nullable String fieldName,
            @Nullable Throwable cause) {
        super(message, cause);
        this.fieldName = fieldName;
    }

    @Nullable
    public String getFieldName() {
        return fieldName;
    }
}