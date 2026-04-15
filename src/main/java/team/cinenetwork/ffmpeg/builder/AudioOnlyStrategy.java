package team.cinenetwork.ffmpeg.builder;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import team.cinenetwork.ffmpeg.builder.impl.AbstractFFmpegStrategy;
import team.cinenetwork.ffmpeg.exceptions.ErrorCode;
import team.cinenetwork.ffmpeg.exceptions.Exception;
import team.cinenetwork.model.AudioStream;
import team.cinenetwork.options.AppOptions;
import team.cinenetwork.model.VideoInfo;

import java.util.List;

@Slf4j
public class AudioOnlyStrategy extends AbstractFFmpegStrategy {

    public AudioOnlyStrategy(AppOptions options,
                             VideoInfo videoInfo
    ) {
        super(options, videoInfo);
    }

    @Override
    public @NotNull List<String> build() {

        AudioStream audio = videoInfo.findAudioStream(options.getAudioStream())
                .orElseThrow(() -> Exception.of(ErrorCode.AUDIO_STREAM_NOT_FOUND,
                        "Audio stream " + options.getAudioStream() + " not found"));

        ensureDirectory(options.getOutput());

        String segmentPattern = options.getOutput()
                .resolve("audio_%03d.ts")
                .toString();

        log.info("Building audio-only command for language: {}", audio.getLanguage());

        return List.of(
                "-i", options.getInput().toString(),
                "-vn",
                "-map", options.getAudioStream(),
                "-c:a", options.getAudioCodec(),
                "-b:a", options.getAudioBitrate() + "k",
                "-f", "hls",
                "-hls_time", String.valueOf(options.getHlsTime()),
                "-hls_playlist_type", "vod",
                "-hls_segment_type", options.getHlsType().name(),
                "-hls_segment_filename", segmentPattern,
                options.getOutput().resolve("playlist.m3u8").toString()
        );
    }
}