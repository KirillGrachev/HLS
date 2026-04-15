package team.cinenetwork.processor.impl.playlist;

import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import team.cinenetwork.ffmpeg.exceptions.ErrorCode;
import team.cinenetwork.ffmpeg.exceptions.Exception;
import team.cinenetwork.model.VideoInfo;
import team.cinenetwork.options.AppOptions;

import java.util.Optional;

@RequiredArgsConstructor
public class MasterPlaylistBuilder {

    private final AppOptions options;
    private final VideoInfo videoInfo;

    public @NotNull String build() {

        if (options.isAudioOnly()
                || videoInfo.getVideoStream() == null) {
            return "";
        }

        StringBuilder content = new StringBuilder();
        content.append("#EXTM3U\n#EXT-X-VERSION:5\n");

        for (int i = 0; i < options.getVideoWidths().size(); i++) {
            if (shouldSkipVariant(i)) continue;
            appendVariantEntry(content, i);
        }

        return content.toString();

    }

    private boolean shouldSkipVariant(int index) {
        return options.getVideoBaseBitrates().get(index) == 0;
    }

    private void appendVariantEntry(@NotNull StringBuilder content, int index) {
        content.append(buildVariantLine(index));
        content.append(buildPlaylistPath(index)).append("\n");
    }

    private @NotNull String buildVariantLine(int index) {

        int width = options.getVideoWidths().get(index);
        int height = calculateHeight(width);
        double frameRate = getFrameRate();

        return String.format(
                "#EXT-X-STREAM-INF:BANDWIDTH=%d,RESOLUTION=%dx%d," +
                        "FRAME-RATE=%.3f,AUDIO=\"audio\",NAME=\"%s\"\n",
                calculateTotalBitrate(index),
                width, height, frameRate,
                options.getVideoNames().get(index)
        );
    }

    private @NotNull String buildPlaylistPath(int index) {
        return String.format("video/%s/playlist.m3u8", options.getVideoNames().get(index));
    }

    private int calculateHeight(int width) {
        double ratio = parseAspectRatio(options.getRatio());
        return (int) (width / ratio);
    }

    private int calculateTotalBitrate(int index) {

        int videoBitrate = options.getVideoBaseBitrates().get(index);
        int audioBitrate = Optional.ofNullable(videoInfo.getAudioStream())
                .map(a -> options.getAudioBitrate())
                .orElse(0);

        return videoBitrate + audioBitrate;

    }

    private double getFrameRate() {
        return videoInfo.getVideoStream()
                .getCalculatedFrameRate()
                .orElseThrow(() -> Exception.of(ErrorCode.FRAME_RATE_NOT_FOUND,
                        "Frame rate not found"));
    }

    private double parseAspectRatio(@NotNull String ratio) {

        String[] parts = ratio.split(":");
        if (parts.length != 2) {
            throw Exception.of(ErrorCode.INVALID_ASPECT_RATIO,
                    "Invalid aspect ratio: " + ratio);
        }

        return Double.parseDouble(parts[0]) / Double.parseDouble(parts[1]);

    }
}