package team.cinenetwork.ffmpeg.exceptions.validation;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class NoMatchingBitrateException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    @Nullable
    private final Integer targetWidth;

    @Nullable
    private final Integer variantIndex;

    public NoMatchingBitrateException(@NotNull String message) {
        this(message, null, null, null);
    }

    public NoMatchingBitrateException(@NotNull String message,
                                      @Nullable Throwable cause) {
        this(message, null, null, cause);
    }

    public NoMatchingBitrateException(
            @NotNull String message,
            @Nullable Integer targetWidth,
            @Nullable Integer variantIndex) {
        this(message, targetWidth, variantIndex, null);
    }

    public NoMatchingBitrateException(
            @NotNull String message,
            @Nullable Integer targetWidth,
            @Nullable Integer variantIndex,
            @Nullable Throwable cause) {
        super(message, cause);
        this.targetWidth = targetWidth;
        this.variantIndex = variantIndex;
    }

    @Nullable
    public Integer getTargetWidth() {
        return targetWidth;
    }

    @Nullable
    public Integer getVariantIndex() {
        return variantIndex;
    }
}