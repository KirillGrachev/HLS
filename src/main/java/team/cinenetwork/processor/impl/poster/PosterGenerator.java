package team.cinenetwork.processor.impl.poster;

import team.cinenetwork.ffmpeg.type.ProcessingType;
import team.cinenetwork.options.AppOptions;
import team.cinenetwork.ffmpeg.FFmpegExecutor;
import team.cinenetwork.model.VideoInfo;
import team.cinenetwork.processor.impl.artifact.ArtifactGenerator;

import java.io.IOException;

public class PosterGenerator implements ArtifactGenerator {

    private final AppOptions options;
    private final FFmpegExecutor ffmpeg;

    public PosterGenerator(AppOptions options, VideoInfo videoInfo) {

        this.options = options;
        this.ffmpeg = new FFmpegExecutor(options, videoInfo);

    }

    @Override
    public void generate() throws IOException {
        ffmpeg.executeMediaProcessing(ProcessingType.POSTER);
    }

    @Override
    public boolean isEnabled() {
        return options.isPosterEnabled();
    }
}