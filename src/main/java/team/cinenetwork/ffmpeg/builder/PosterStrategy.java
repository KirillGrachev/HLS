package team.cinenetwork.ffmpeg.builder;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import team.cinenetwork.exception.ErrorCode;
import team.cinenetwork.exception.ProcessingException;
import team.cinenetwork.ffmpeg.builder.impl.AbstractFFmpegStrategy;
import team.cinenetwork.model.VideoInfo;
import team.cinenetwork.model.VideoStream;
import team.cinenetwork.options.AppOptions;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Команда постера: один превью-кадр из исходника.
 *
 * <p>Рефакторинг: раньше постер генерировался на каждую ступень лестницы в отдельную
 * папку рядом с выводом, а опция {@code --poster-filename} игнорировалась. Теперь постер
 * ровно один и лежит там, куда указал пользователь: {@code <out>/preview.jpg}
 * (артефакт с продуктовой инфографики).
 *
 * <p>Кадр берётся с ближайшего ключевого кадра к seek-позиции
 * ({@code select=eq(pict_type,I)}) — быстро и всегда «чистая» картинка.
 */
@Slf4j
public class PosterStrategy extends AbstractFFmpegStrategy {

    private final int posterWidth;

    public PosterStrategy(AppOptions options,
                          VideoInfo videoInfo,
                          int posterWidth) {
        super(options, videoInfo);
        this.posterWidth = posterWidth;
    }

    @Override
    public @NotNull List<String> build() {

        VideoStream video = requireVideoStream();

        if (posterWidth <= 0) {
            throw ProcessingException.of(ErrorCode.ZERO_WIDTH,
                    "Cannot generate poster for zero width");
        }

        String seek = parseSeekPosition(options.getPosterSeek(), videoInfo.getDuration());
        int[] resolution = scaledResolution(posterWidth);
        Path posterPath = options.getOutput().resolve(options.getPosterFilename());

        ensureDirectory(posterPath.getParent());
        log.info("Generating poster at {}x{} (seek {}s)", resolution[0], resolution[1], seek);

        List<String> args = new ArrayList<>(inputArgsWithSeek(seek));
        args.addAll(List.of(
                "-map", "0:" + video.getStreamIndex(),
                "-frames:v", "1",
                "-vf", buildFilter(resolution),
                "-qscale:v", "2",
                posterPath.toString()
        ));

        return args;

    }

    private @NotNull String buildFilter(int @NotNull [] resolution) {

        return String.join(",", List.of(
                "select=eq(pict_type\\,I)",
                "scale=" + resolution[0] + ":" + resolution[1]
        ));

    }
}
