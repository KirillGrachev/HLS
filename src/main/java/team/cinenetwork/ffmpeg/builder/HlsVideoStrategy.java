package team.cinenetwork.ffmpeg.builder;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import team.cinenetwork.ffmpeg.builder.impl.AbstractFFmpegStrategy;
import team.cinenetwork.ffmpeg.exceptions.ErrorCode;
import team.cinenetwork.ffmpeg.exceptions.Exception;
import team.cinenetwork.model.VideoStream;
import team.cinenetwork.options.AppOptions;
import team.cinenetwork.model.VideoInfo;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
public class HlsVideoStrategy extends AbstractFFmpegStrategy {

    private final int variantIndex;

    public HlsVideoStrategy(AppOptions options,
                            VideoInfo videoInfo,
                            int variantIndex
    ) {
        super(options, videoInfo);
        this.variantIndex = variantIndex;
    }

    @Override
    public @NotNull List<String> build() {

        List<String> args = new ArrayList<>();

        VideoStream video = videoInfo.getVideoStream();
        int targetWidth = options.getVideoWidths().get(variantIndex);

        args.add("-i");
        args.add(options.getInput().toString());

        if (targetWidth > 0) {

            int[] resolution = calculateHlsResolution(variantIndex);
            int bitrate = calculateVideoBitrate(variantIndex);

            args.addAll(List.of(
                    "-map", "0:" + video.getStreamIndex(),
                    "-c:v", options.getVideoCodecs().get(variantIndex),
                    "-profile:v", extractProfileLevel(options.getVideoProfiles()
                            .get(variantIndex), true),
                    "-level:v", extractProfileLevel(options.getVideoProfiles()
                            .get(variantIndex), false),
                    "-b:v", bitrate + "k",
                    "-maxrate:v", bitrate + "k",
                    "-bufsize:v", (int) (bitrate * 1.5) + "k",
                    "-vf", buildVideoFilter(resolution)
            ));

            if (variantIndex < options.getVideoPresets().size()) {
                args.addAll(List.of("-preset",
                        options.getVideoPresets().get(variantIndex)));
            }
        }

        if (shouldIncludeAudio(targetWidth)) {
            args.addAll(buildAudioArgs(Optional.empty()));
        }

        args.addAll(buildHlsOutputArgs(variantIndex));

        log.debug("Built HLS command for variant {}: {}", variantIndex,
                String.join(" ", args));

        return args;

    }

    private int[] calculateHlsResolution(int variantIndex) {

        int width = options.getVideoWidths().get(variantIndex);
        int[] original = {videoInfo.getVideoStream().getFrameWidth(),
                videoInfo.getVideoStream().getFrameHeight()};

        return calculateResolution(original, width, Integer.MAX_VALUE);

    }

    private @NotNull String buildVideoFilter(int @NotNull [] resolution) {
        return String.join(",", List.of(
                "scale=" + resolution[0] + ":" + resolution[1],
                "format=yuv420p"
        ));
    }

    private int calculateVideoBitrate(int variantIndex) {

        int sourceBitrate = videoInfo.getVideoStream().getBitrateKbps();
        List<Integer> baseBitrates = new ArrayList<>(options.getVideoBaseBitrates());

        baseBitrates.sort((a, b) -> Integer.compare(b, a));

        int base = (sourceBitrate > baseBitrates.getFirst() || sourceBitrate <= 0)
                ? baseBitrates.getFirst()
                : sourceBitrate;

        double factor = options.getVideoQualityFactors().get(variantIndex);

        return (int) Math.round(base * factor);

    }

    private boolean shouldIncludeAudio(int targetWidth) {
        return videoInfo.getAudioStream() != null
                && !options.isAudioDisable()
                && (!options.isNoAudio() || targetWidth == 0);
    }

    private @NotNull List<String> buildHlsOutputArgs(int variantIndex) {

        Path segmentsPath = ensureDirectory(
                options.getOutput().resolve(getQualityFolderName(variantIndex)));

        String segmentPattern = segmentsPath.resolve("%03d.ts").toString();
        String playlistPath = segmentsPath.resolve("playlist.m3u8").toString();

        return List.of(
                "-f", "hls",
                "-hls_time", String.valueOf(options.getHlsTime()),
                "-g", String.valueOf(calculateKeyframeInterval()),
                "-keyint_min", String.valueOf(calculateKeyframeInterval()),
                "-hls_playlist_type", "vod",
                "-hls_list_size", "0",
                "-hls_segment_type", options.getHlsType().name(),
                "-hls_base_url", options.getHlsSegmentPrefix(),
                "-hls_segment_filename", segmentPattern,
                playlistPath
        );
    }

    private @NotNull String getQualityFolderName(int variantIndex) {

        int width = options.getVideoWidths().get(variantIndex);

        if (width == 0) throw Exception.of(ErrorCode.ZERO_WIDTH,
                "Zero width variant"
        );

        double ratio = parseRatio(options.getRatio());
        int height = (int) (width / ratio);

        return "video/" + height + "p";

    }
}
