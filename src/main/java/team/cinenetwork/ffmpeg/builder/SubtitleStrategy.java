package team.cinenetwork.ffmpeg.builder;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import team.cinenetwork.ffmpeg.builder.impl.AbstractFFmpegStrategy;
import team.cinenetwork.model.VideoInfo;
import team.cinenetwork.options.AppOptions;
import team.cinenetwork.processor.impl.rendition.SubtitleRendition;

import java.util.ArrayList;
import java.util.List;

/**
 * Команда конвертации одной дорожки субтитров в WebVTT.
 *
 * <p>FFmpeg сам конвертирует текстовые форматы (SRT/ASS/SSA/MOV_TEXT) в {@code .vtt};
 * мы лишь указываем маппинг дорожки и целевой файл. Полученный файл затем оборачивается
 * мини-плейлистом ({@code SubtitlePlaylistWriter}) — стандартная схема субтитров в HLS
 * (WebVTT отдельным рендишеном).
 *
 * <p>Выход: {@code <out>/subtitles/<папка языка>/subtitles.vtt}.
 */
@Slf4j
public class SubtitleStrategy extends AbstractFFmpegStrategy {

    private final SubtitleRendition rendition;

    public SubtitleStrategy(AppOptions options,
                            VideoInfo videoInfo,
                            SubtitleRendition rendition) {
        super(options, videoInfo);
        this.rendition = rendition;
    }

    @Override
    public @NotNull List<String> build() {

        ensureDirectory(rendition.directory());

        List<String> args = new ArrayList<>(inputArgs());
        args.addAll(List.of(
                "-map", "0:" + rendition.stream().getStreamIndex(),
                "-c:s", "webvtt",
                rendition.vttFile().toString()
        ));

        log.info("Building subtitle extraction command for track: {} (index {}, codec {})",
                rendition.stream().getDisplayName(),
                rendition.stream().getStreamIndex(),
                rendition.stream().getCodecName());

        return args;

    }
}
