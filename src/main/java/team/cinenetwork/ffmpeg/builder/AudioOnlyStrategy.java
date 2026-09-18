package team.cinenetwork.ffmpeg.builder;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import team.cinenetwork.exception.ErrorCode;
import team.cinenetwork.exception.ProcessingException;
import team.cinenetwork.ffmpeg.builder.impl.AbstractFFmpegStrategy;
import team.cinenetwork.model.AudioStream;
import team.cinenetwork.model.VideoInfo;
import team.cinenetwork.options.AppOptions;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Режим audio-only ({@code --audio-only}): один HLS-поток без видео.
 *
 * <p>Сценарий: подкасты/OST-сборники или исходники, где видео не нужно. Мастер-плейлист
 * в этом режиме не строится (видео-лестницы нет), результат — самостоятельный плейлист
 * в корне вывода.
 */
@Slf4j
public class AudioOnlyStrategy extends AbstractFFmpegStrategy {

    public AudioOnlyStrategy(AppOptions options, VideoInfo videoInfo) {
        super(options, videoInfo);
    }

    @Override
    public @NotNull List<String> build() {

        AudioStream audio = videoInfo.findAudioStream(options.getAudioStream())
                .orElseThrow(() -> ProcessingException.of(ErrorCode.AUDIO_STREAM_NOT_FOUND,
                        "Audio stream " + options.getAudioStream() + " not found"));

        ensureDirectory(options.getOutput());
        log.info("Building audio-only command for language: {}", audio.getLanguage());

        Path segmentPattern = options.getOutput().resolve("audio_%03d.ts");
        Path playlistPath = options.getOutput().resolve("playlist.m3u8");

        List<String> args = new ArrayList<>(inputArgs());
        args.add("-vn");
        args.addAll(audioEncodeArgs(audio));
        args.addAll(hlsOutputArgs(segmentPattern, playlistPath));

        return args;

    }
}
