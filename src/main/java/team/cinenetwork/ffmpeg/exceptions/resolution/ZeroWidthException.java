package team.cinenetwork.ffmpeg.exceptions.resolution;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ZeroWidthException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    @Nullable
    private final Integer variantIndex;

    public ZeroWidthException(@NotNull String message) {
        this(message, null, null);
    }

    public ZeroWidthException(@NotNull String message,
                              @Nullable Throwable cause) {
        this(message, null, cause);
    }

    public ZeroWidthException(@NotNull String message,
                              @Nullable Integer variantIndex) {
        this(message, variantIndex, null);
    }

    public ZeroWidthException(
            @NotNull String message,
            @Nullable Integer variantIndex,
            @Nullable Throwable cause) {
        super(message, cause);
        this.variantIndex = variantIndex;
    }

    @Nullable
    public Integer getVariantIndex() {
        return variantIndex;
    }
}