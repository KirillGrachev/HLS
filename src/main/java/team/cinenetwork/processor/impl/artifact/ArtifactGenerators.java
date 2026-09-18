package team.cinenetwork.processor.impl.artifact;

import org.jetbrains.annotations.NotNull;
import team.cinenetwork.ffmpeg.FFmpegExecutor;
import team.cinenetwork.model.VideoInfo;
import team.cinenetwork.options.AppOptions;
import team.cinenetwork.processor.EncodingProfile;
import team.cinenetwork.processor.impl.audio.AudioRenditionGenerator;
import team.cinenetwork.processor.impl.hls.HlsGenerator;
import team.cinenetwork.processor.impl.playlist.PlaylistGenerator;
import team.cinenetwork.processor.impl.poster.PosterGenerator;
import team.cinenetwork.processor.impl.subtitle.SubtitleGenerator;

import java.util.List;

/**
 * Composition root артефактов: единственное место, где определён набор и порядок этапов.
 *
 * <p>Порядок важен: мастер-плейлист строится последним, когда все рендишены
 * (видео, аудио, субтитры) уже лежат на диске и их URI известны.
 */
public final class ArtifactGenerators {

    private ArtifactGenerators() {
    }

    public static @NotNull List<ArtifactGenerator> createAll(@NotNull AppOptions options,
                                                             @NotNull VideoInfo videoInfo,
                                                             @NotNull EncodingProfile profile,
                                                             @NotNull FFmpegExecutor ffmpegExecutor) {

        return List.of(
                new PosterGenerator(options, videoInfo, ffmpegExecutor),
                new HlsGenerator(options, videoInfo, ffmpegExecutor),
                new AudioRenditionGenerator(options, videoInfo, ffmpegExecutor),
                new SubtitleGenerator(options, videoInfo, ffmpegExecutor),
                new PlaylistGenerator(options, videoInfo, profile)
        );

    }
}
