package team.cinenetwork.ffmpeg.builder;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import team.cinenetwork.ffmpeg.builder.impl.AbstractFFmpegStrategy;
import team.cinenetwork.model.VideoInfo;
import team.cinenetwork.model.VideoStream;
import team.cinenetwork.options.AppOptions;
import team.cinenetwork.processor.EncodingProfile;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Команда одной «ступени» видео-лестницы (например 720p).
 *
 * <p>Ключевое архитектурное решение: команда кодирует <b>только видео</b>. Аудио и субтитры
 * живут в отдельных рендишенах ({@code EXT-X-MEDIA}) и микшируются плеером самостоятельно —
 * это единственно верная схема для аниме с несколькими озвучками: раньше звук «впекался»
 * в каждый вариант, и сменить язык на лету было невозможно.
 *
 * <p>Выход: {@code <out>/video/<имя варианта>/playlist.m3u8 + сегменты}.
 */
@Slf4j
public class HlsVideoStrategy extends AbstractFFmpegStrategy {

    /** Ступень лестницы: ширина, битрейт, кодек, профиль, пресет, имя. */
    private final EncodingProfile.Variant variant;

    public HlsVideoStrategy(AppOptions options,
                            VideoInfo videoInfo,
                            EncodingProfile.Variant variant) {
        super(options, videoInfo);
        this.variant = variant;
    }

    @Override
    public @NotNull List<String> build() {

        VideoStream video = requireVideoStream();
        List<String> args = new ArrayList<>(inputArgs());

        args.addAll(videoEncodeArgs(video));
        args.addAll(hlsOutputArgs(segmentPattern(), playlistPath()));

        log.debug("Built HLS command for variant {}: {}", variant.name(), String.join(" ", args));

        return args;

    }

    /* ---------- Блок видео-кодирования ---------- */

    private @NotNull List<String> videoEncodeArgs(@NotNull VideoStream video) {

        int[] resolution = scaledResolution(variant.width());
        int bitrateKbps = variant.bitrateKbps();
        int keyframeInterval = calculateKeyframeInterval();

        return List.of(
                "-map", "0:" + video.getStreamIndex(),
                "-c:v", variant.codec(),
                "-profile:v", extractProfileLevel(variant.profileLevel(), true),
                "-level:v", extractProfileLevel(variant.profileLevel(), false),
                "-b:v", bitrateKbps + "k",
                "-maxrate:v", bitrateKbps + "k",
                "-bufsize:v", (int) (bitrateKbps * 1.5) + "k",
                "-g", String.valueOf(keyframeInterval),
                "-keyint_min", String.valueOf(keyframeInterval),
                "-vf", buildVideoFilter(resolution),
                "-preset", variant.preset()
        );

    }

    /** scale под разрешение ступени + yuv420p: без него Safari и старые TV отказываются играть. */
    private @NotNull String buildVideoFilter(int @NotNull [] resolution) {
        return "scale=" + resolution[0] + ":" + resolution[1] + ",format=yuv420p";
    }

    /* ---------- Выходные пути ---------- */

    private @NotNull Path variantDirectory() {
        // Папка именуется по имени варианта (1080p, 720p, ...) —
        // ровно на него ссылается мастер-плейлист.
        return ensureDirectory(options.getOutput().resolve("video").resolve(variant.name()));
    }

    private @NotNull Path segmentPattern() {
        return variantDirectory().resolve("%03d.ts");
    }

    private @NotNull Path playlistPath() {
        return variantDirectory().resolve("playlist.m3u8");
    }
}
