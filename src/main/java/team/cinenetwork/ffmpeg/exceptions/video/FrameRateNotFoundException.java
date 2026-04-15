package team.cinenetwork.ffmpeg.exceptions.video;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FrameRateNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    @Nullable
    private final Integer streamIndex;

    public FrameRateNotFoundException(@NotNull String message) {
        this(message, null, null);
    }

    public FrameRateNotFoundException(@NotNull String message,
                                      @Nullable Throwable cause) {
        this(message, null, cause);
    }

    public FrameRateNotFoundException(@NotNull String message,
                                      @Nullable Integer streamIndex) {
        this(message, streamIndex, null);
    }

    public FrameRateNotFoundException(
            @NotNull String message,
            @Nullable Integer streamIndex,
            @Nullable Throwable cause) {
        super(message, cause);
        this.streamIndex = streamIndex;
    }

    @Nullable
    public Integer getStreamIndex() {
        return streamIndex;
    }
}