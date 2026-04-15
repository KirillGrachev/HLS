package team.cinenetwork.processor.impl.playlist;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import team.cinenetwork.model.VideoInfo;
import team.cinenetwork.options.AppOptions;
import team.cinenetwork.processor.impl.artifact.ArtifactGenerator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;

@Slf4j
public class PlaylistGenerator implements ArtifactGenerator {

    private final AppOptions options;
    private final VideoInfo videoInfo;
    private final double aspectRatio;

    public PlaylistGenerator(@NotNull AppOptions options,
                             VideoInfo videoInfo) {

        this.options = options;
        this.videoInfo = videoInfo;
        this.aspectRatio = parseAspectRatio(options.getRatio());

    }

    @Override
    public void generate() throws IOException {

        if (options.isAudioOnly() || videoInfo.getVideoStream() == null) {
            log.debug("Skipping master playlist generation - no video stream");
            return;
        }

        Path playlistPath = Path.of(options.getOutput().toString(),
                options.getHlsMasterPlaylist());

        Files.createDirectories(playlistPath.getParent());

        StringBuilder content = new StringBuilder();
        content.append("#EXTM3U\n#EXT-X-VERSION:5\n");

        appendVideoVariants(content);

        Files.writeString(playlistPath, content.toString(),
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);

    }

    private void appendVideoVariants(StringBuilder content) {

        for (int i = 0; i < options.getVideoWidths().size(); i++) {

            if (shouldSkipVariant(i)) continue;
            appendVariantEntry(content, i);

        }
    }

    private void appendVariantEntry(@NotNull StringBuilder content,
                                    int index) {
        content.append(buildVariantLine(index));
        content.append(buildPlaylistPath(index)).append("\n");
    }

    private boolean shouldSkipVariant(int index) {
        return options.getVideoBaseBitrates().get(index) == 0;
    }

    private @NotNull String buildVariantLine(int index) {

        int width = options.getVideoWidths().get(index);
        int height = calculateHeight(width);

        double frameRate = videoInfo.getVideoStream().getCalculatedFrameRate()
                .orElseThrow(() -> new IllegalStateException("Frame rate not found"));

        return String.format(
                "#EXT-X-STREAM-INF:BANDWIDTH=%d,RESOLUTION" +
                        "=%dx%d,FRAME-RATE=%.3f,AUDIO=\"audio\",NAME=\"%s\"\n",
                calculateTotalBitrate(index),
                width, height, frameRate,
                options.getVideoNames().get(index)
        );

    }

    private @NotNull String buildPlaylistPath(int index) {
        return String.format("video/%s/playlist.m3u8", options.getVideoNames().get(index));
    }

    private int calculateHeight(int width) {
        return (int) (width / aspectRatio);
    }

    private int calculateTotalBitrate(int index) {

        int videoBitrate = options.getVideoBaseBitrates().get(index);
        int audioBitrate = Optional.ofNullable(videoInfo.getAudioStream())
                .map(a -> options.getAudioBitrate())
                .orElse(0);

        return videoBitrate + audioBitrate;

    }

    private double parseAspectRatio(@NotNull String ratio) {

        String[] parts = ratio.split(":");

        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid aspect ratio format: " + ratio);
        }

        try {
            return Double.parseDouble(parts[0]) / Double.parseDouble(parts[1]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid aspect ratio numbers", e);
        }

    }

    @Override
    public boolean isEnabled() {
        return !options.isPlaylistDisable();
    }
}