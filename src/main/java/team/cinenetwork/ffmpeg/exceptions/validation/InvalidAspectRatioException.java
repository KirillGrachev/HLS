package team.cinenetwork.ffmpeg.exceptions.validation;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class InvalidAspectRatioException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    @Nullable
    private final String invalidValue;

    public InvalidAspectRatioException(@NotNull String message) {
        this(message, null, null);
    }

    public InvalidAspectRatioException(@NotNull String message,
                                       @Nullable Throwable cause) {
        this(message, null, cause);
    }

    public InvalidAspectRatioException(@NotNull String message,
                                       @Nullable String invalidValue) {
        this(message, invalidValue, null);
    }

    public InvalidAspectRatioException(
            @NotNull String message,
            @Nullable String invalidValue,
            @Nullable Throwable cause) {
        super(message, cause);
        this.invalidValue = invalidValue;
    }

    @Nullable
    public String getInvalidValue() {
        return invalidValue;
    }
}