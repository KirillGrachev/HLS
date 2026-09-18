package team.cinenetwork.ffmpeg.builder;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import team.cinenetwork.ffmpeg.builder.impl.AbstractFFmpegStrategy;
import team.cinenetwork.model.VideoInfo;
import team.cinenetwork.options.AppOptions;
import team.cinenetwork.processor.impl.rendition.AudioRendition;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Команда одного аудио-рендишена: отдельный HLS-поток с одной дорожкой озвучки.
 *
 * <p>Выход: {@code <out>/audio/<папка языка>/playlist.m3u8 + сегменты}. В мастер-плейлисте
 * рендишен публикуется как {@code #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="audio"} — плеер
 * показывает переключатель дорожек («Japanese / Russian / English»).
 */
@Slf4j
public class AudioRenditionStrategy extends AbstractFFmpegStrategy {

    private final AudioRendition rendition;

    public AudioRenditionStrategy(AppOptions options,
                                  VideoInfo videoInfo,
                                  AudioRendition rendition) {
        super(options, videoInfo);
        this.rendition = rendition;
    }

    @Override
    public @NotNull List<String> build() {

        List<String> args = new ArrayList<>(inputArgs());

        // -vn: видео в аудио-рендишене не нужно
        args.add("-vn");
        args.addAll(audioEncodeArgs(rendition.stream()));
        args.addAll(hlsOutputArgs(segmentPattern(), playlistPath()));

        log.info("Building audio rendition command for track: {} (index {})",
                rendition.stream().getDisplayName(), rendition.stream().getStreamIndex());

        return args;

    }

    private @NotNull Path segmentPattern() {
        return rendition.directory().resolve("%03d.ts");
    }

    private @NotNull Path playlistPath() {
        return rendition.directory().resolve("playlist.m3u8");
    }
}
