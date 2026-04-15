package team.cinenetwork.ffmpeg.builder.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import team.cinenetwork.ffmpeg.exceptions.ErrorCode;
import team.cinenetwork.ffmpeg.exceptions.Exception;
import team.cinenetwork.model.AudioStream;
import team.cinenetwork.model.VideoInfo;
import team.cinenetwork.options.AppOptions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@RequiredArgsConstructor
public abstract class AbstractFFmpegStrategy implements FFmpegCommandStrategy {

    protected final AppOptions options;
    protected final VideoInfo videoInfo;

    @Override
    public abstract @NotNull List<String> build();

    protected int[] calculateResolution(int @NotNull [] original,
                                                 int targetWidth,
                                        int maxWidth) {

        double ratio = original[0] / (double) original[1];
        targetWidth = Math.min(targetWidth, maxWidth);

        int width = targetWidth;
        int height = (int) (width / ratio);

        return new int[]{width & ~1, height & ~1};

    }

    protected double parseRatio(@NotNull String ratio) {

        String[] parts = ratio.split(":");
        return Double.parseDouble(parts[0]) / Double.parseDouble(parts[1]);

    }

    protected @NotNull String parseSeekPosition(String seek, double duration) {

        Matcher m = Pattern.compile("(\\d+)%|(\\d+)s").matcher(seek);

        if (!m.matches()) throw new IllegalArgumentException("Invalid seek: " + seek);
        if (m.group(1) != null) {

            double percentage = Double.parseDouble(m.group(1)) / 100.0;
            return String.valueOf(Math.round(duration * percentage));

        }

        return m.group(2);

    }

    protected int calculateKeyframeInterval() {

        double fps = videoInfo.getVideoStream()
                .getCalculatedFrameRate()
                .orElseThrow(() -> new IllegalStateException("FPS not available"));

        return (int) Math.ceil(fps * options.getHlsTime());

    }

    protected @NotNull Path ensureDirectory(Path path) {

        try {

            Files.createDirectories(path);
            return path;

        } catch (IOException e) {
            throw new RuntimeException("Cannot create directory: " + path, e);
        }
    }

    protected @NotNull String extractProfileLevel(@NotNull String profileLevel,
                                                  boolean isProfile) {

        String[] parts = profileLevel.split("@");
        return isProfile ? parts[0] : parts.length > 1 ? parts[1] : "main";

    }

    protected @NotNull List<String> buildAudioArgs(
            @NotNull Optional<AudioStream> audioStreamOverride
    ) {

        AudioStream audio = audioStreamOverride
                .or(() -> Optional.ofNullable(videoInfo.getAudioStream()))
                .orElseThrow(() -> Exception.of(ErrorCode.AUDIO_STREAM_NOT_FOUND,
                        "No audio stream available"));

        return List.of(
                "-map", options.getAudioStream(),
                "-c:a", options.getAudioCodec(),
                "-profile:a", options.getAudioProfile(),
                "-b:a", options.getAudioBitrate() + "k",
                "-ar", options.getAudioSampling() != null
                        ? options.getAudioSampling().toString()
                        : String.valueOf(audio.getSamplingRateHz())
        );
    }
}