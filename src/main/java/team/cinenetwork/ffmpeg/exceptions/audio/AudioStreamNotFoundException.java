package team.cinenetwork.ffmpeg.exceptions.audio;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AudioStreamNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    @Nullable
    private final String streamIdentifier;

    public AudioStreamNotFoundException(@NotNull String message) {
        this(message, null, null);
    }

    public AudioStreamNotFoundException(@NotNull String message,
                                        @Nullable Throwable cause) {
        this(message, null, cause);
    }

    public AudioStreamNotFoundException(@NotNull String message,
                                        @Nullable String streamIdentifier) {
        this(message, streamIdentifier, null);
    }

    public AudioStreamNotFoundException(
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