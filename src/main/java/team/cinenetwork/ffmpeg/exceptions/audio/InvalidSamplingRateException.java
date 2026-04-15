package team.cinenetwork.ffmpeg.exceptions.audio;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class InvalidSamplingRateException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    @Nullable
    private final Integer samplingRateHz;

    public InvalidSamplingRateException(@NotNull String message) {
        this(message, null, null);
    }

    public InvalidSamplingRateException(@NotNull String message,
                                        @Nullable Throwable cause) {
        this(message, null, cause);
    }

    public InvalidSamplingRateException(@NotNull String message,
                                        @Nullable Integer samplingRateHz) {
        this(message, samplingRateHz, null);
    }

    public InvalidSamplingRateException(
            @NotNull String message,
            @Nullable Integer samplingRateHz,
            @Nullable Throwable cause) {
        super(message, cause);
        this.samplingRateHz = samplingRateHz;
    }

    @Nullable
    public Integer getSamplingRateHz() {
        return samplingRateHz;
    }
}