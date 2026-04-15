package team.cinenetwork.processor.impl.hls;

import team.cinenetwork.options.AppOptions;
import team.cinenetwork.ffmpeg.FFmpegExecutor;
import team.cinenetwork.model.VideoInfo;
import team.cinenetwork.ffmpeg.type.ProcessingType;
import team.cinenetwork.processor.impl.artifact.ArtifactGenerator;

import java.io.IOException;

public class HlsGenerator implements ArtifactGenerator {

    private final AppOptions options;
    private final FFmpegExecutor ffmpeg;

    public HlsGenerator(AppOptions options, VideoInfo videoInfo) {

        this.options = options;
        this.ffmpeg = new FFmpegExecutor(options, videoInfo);

    }

    @Override
    public void generate() throws IOException {
        ffmpeg.executeMediaProcessing(ProcessingType.TRANSCODE);
    }

    @Override
    public boolean isEnabled() {
        return !options.isHlsDisable();
    }
}