package team.cinenetwork.ffmpeg.exceptions.audio;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AudioOnlyException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    @Nullable
    private final String streamIdentifier;

    public AudioOnlyException(@NotNull String message) {
        this(message, null, null);
    }

    public AudioOnlyException(@NotNull String message,
                              @Nullable Throwable cause) {
        this(message, null, cause);
    }

    public AudioOnlyException(@NotNull String message,
                              @Nullable String streamIdentifier) {
        this(message, streamIdentifier, null);
    }

    public AudioOnlyException(
            @NotNull String message,
            @Nullable String streamIdentifier,
            @Nullable Throwable cause) {
        super(message, cause);
        this.streamIdentifier = streamIdentifier;
    }

    @Nullable
    public String getStreamIdentifier() {
        return streamIdentifier;
    }
}