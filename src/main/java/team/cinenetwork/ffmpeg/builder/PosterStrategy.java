package team.cinenetwork.ffmpeg.builder;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import team.cinenetwork.ffmpeg.builder.impl.AbstractFFmpegStrategy;
import team.cinenetwork.model.VideoStream;
import team.cinenetwork.options.AppOptions;
import team.cinenetwork.model.VideoInfo;

import java.nio.file.Path;
import java.util.List;

@Slf4j
public class PosterStrategy extends AbstractFFmpegStrategy {

    private final int variantIndex;

    public PosterStrategy(AppOptions options,
                          VideoInfo videoInfo,
                          int variantIndex
    ) {
        super(options, videoInfo);
        this.variantIndex = variantIndex;
    }

    @Override
    public @NotNull List<String> build() {

        VideoStream video = videoInfo.getVideoStream();
        String seek = parseSeekPosition(options.getPosterSeek(),
                videoInfo.getDuration());

        int targetWidth = options.getVideoWidths().get(variantIndex);

        if (targetWidth == 0) {
            throw new IllegalArgumentException(
                    "Cannot generate poster for zero-width variant"
            );
        }

        int[] original = {video.getFrameWidth(), video.getFrameHeight()};
        int[] resolution = calculateResolution(original, targetWidth, targetWidth);

        Path previewDir = ensureDirectory(
                options.getOutput().getParent().resolve("preview"));

        String qualityName = getQualityFolderName(variantIndex).split("/")[1];
        Path previewPath = previewDir.resolve(qualityName + ".jpg");

        log.info("Generating poster for variant {} at {}x{}", variantIndex,
                resolution[0], resolution[1]);

        String filter = String.join(",", List.of(
                "select=eq(pict_type\\,I)",
                "scale=" + resolution[0] + ":" + resolution[1]
        ));

        return List.of(
                "-ss", seek,
                "-i", options.getInput().toString(),
                "-map", "0:" + video.getStreamIndex(),
                "-frames:v", "1",
                "-vf", filter,
                "-qscale:v", "1",
                previewPath.toString()
        );
    }

    private @NotNull String getQualityFolderName(int variantIndex) {

        int width = options.getVideoWidths().get(variantIndex);
        double ratio = parseRatio(options.getRatio());

        int height = (int) (width / ratio);

        return "video/" + height + "p";

    }
}