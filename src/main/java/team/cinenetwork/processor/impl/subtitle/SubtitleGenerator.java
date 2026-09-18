package team.cinenetwork.processor.impl.subtitle;

import lombok.extern.slf4j.Slf4j;
import team.cinenetwork.ffmpeg.FFmpegExecutor;
import team.cinenetwork.ffmpeg.type.ProcessingType;
import team.cinenetwork.model.VideoInfo;
import team.cinenetwork.options.AppOptions;
import team.cinenetwork.processor.impl.artifact.ArtifactGenerator;
import team.cinenetwork.processor.impl.playlist.PlaylistWriter;
import team.cinenetwork.processor.impl.rendition.SubtitleRendition;
import team.cinenetwork.processor.impl.rendition.SubtitleRenditions;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Вытягивание субтитров в HLS — новый этап пайплайна.
 *
 * <p>Как работает (простыми словами): каждая текстовая дорожка субтитров исходника
 * (SRT/ASS/SSA) конвертируется FFmpeg в формат WebVTT — единственный, который понимает
 * HLS. Рядом с .vtt кладётся мини-плейлист, а сама дорожка публикуется в мастер-плейлисте:
 * в интерфейсе плеера появляется пункт «Субтитры» со списком языков.
 *
 * <p>Шаги этапа:
 * <ol>
 *   <li>планируем рендишены ({@code SubtitleRenditions.resolve}): папки по языкам;</li>
 *   <li>запускаем конвертацию FFmpeg (ProcessingType.SUBTITLES);</li>
 *   <li>пишем оборачивающие плейлисты ({@code SubtitlePlaylistWriter}).</li>
 * </ol>
 */
@Slf4j
public class SubtitleGenerator implements ArtifactGenerator {

    private final AppOptions options;
    private final VideoInfo videoInfo;
    private final FFmpegExecutor ffmpegExecutor;
    private final SubtitlePlaylistWriter playlistWriter;

    public SubtitleGenerator(AppOptions options,
                             VideoInfo videoInfo,
                             FFmpegExecutor ffmpegExecutor) {
        this(options, videoInfo, ffmpegExecutor,
                new SubtitlePlaylistWriter(new PlaylistWriter()));
    }

    private SubtitleGenerator(AppOptions options,
                              VideoInfo videoInfo,
                              FFmpegExecutor ffmpegExecutor,
                              SubtitlePlaylistWriter playlistWriter) {

        this.options = options;
        this.videoInfo = videoInfo;
        this.ffmpegExecutor = ffmpegExecutor;
        this.playlistWriter = playlistWriter;

    }

    @Override
    public void generate() throws IOException {

        List<SubtitleRendition> renditions = SubtitleRenditions.resolve(options, videoInfo);
        if (renditions.isEmpty()) {
            log.warn("Subtitle extraction enabled, but no convertible subtitle tracks found "
                    + "(supported: SRT/ASS/SSA/WebVTT; image-based PGS/DVB are skipped)");
            return;
        }

        ffmpegExecutor.executeMediaProcessing(ProcessingType.SUBTITLES);

        double duration = videoInfo.getDuration();
        for (SubtitleRendition rendition : renditions) {
            playlistWriter.write(rendition, duration);
        }

        log.info("Subtitles extracted: {} track(s) [{}]", renditions.size(), renditions.stream()
                .map(rendition -> rendition.folderName()
                        + " (" + rendition.stream().getDisplayName() + ")")
                .collect(Collectors.joining(", ")));

    }

    @Override
    public boolean isEnabled() {

        return options.isSubsEnabled()
                && !options.isHlsDisable()
                && !options.isAudioOnly()
                && !videoInfo.getSubtitleStreams().isEmpty();

    }

    @Override
    public String name() {
        return "Subtitles (WebVTT)";
    }
}
