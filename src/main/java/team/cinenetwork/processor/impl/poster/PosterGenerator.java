package team.cinenetwork.processor.impl.poster;

import lombok.RequiredArgsConstructor;
import team.cinenetwork.ffmpeg.FFmpegExecutor;
import team.cinenetwork.ffmpeg.type.ProcessingType;
import team.cinenetwork.model.VideoInfo;
import team.cinenetwork.options.AppOptions;
import team.cinenetwork.processor.impl.artifact.ArtifactGenerator;

import java.io.IOException;

/**
 * Постер (preview.jpg): один превью-кадр эпизода.
 */
@RequiredArgsConstructor
public class PosterGenerator implements ArtifactGenerator {

    private final AppOptions options;
    private final VideoInfo videoInfo;
    private final FFmpegExecutor ffmpegExecutor;

    @Override
    public void generate() throws IOException {
        ffmpegExecutor.executeMediaProcessing(ProcessingType.POSTER);
    }

    @Override
    public boolean isEnabled() {

        return options.isPosterEnabled()
                && !options.isAudioOnly()
                && videoInfo.getVideoStream() != null;

    }

    @Override
    public String name() {
        return "Poster (" + options.getPosterFilename() + ")";
    }
}
