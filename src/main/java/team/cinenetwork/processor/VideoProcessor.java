package team.cinenetwork.processor;

import team.cinenetwork.processor.impl.meta.MetadataParser;
import team.cinenetwork.utils.FileUtil;

import org.jetbrains.annotations.NotNull;

import team.cinenetwork.ffmpeg.FFmpegExecutor;
import team.cinenetwork.model.VideoInfo;
import team.cinenetwork.options.AppOptions;
import team.cinenetwork.processor.impl.artifact.ArtifactGenerator;

import java.io.IOException;
import java.util.List;

public class VideoProcessor {

    private final AppOptions options;
    private final FFmpegExecutor ffmpegExecutor;
    private final OptionsNormalizer optionsNormalizer;
    private final MetadataParser metadataParser;
    private final List<ArtifactGenerator> artifactGenerators;

    public VideoProcessor(@NotNull AppOptions options,
                          @NotNull FFmpegExecutor ffmpegExecutor,
                          @NotNull OptionsNormalizer normalizer,
                          @NotNull MetadataParser metadataParser,
                          @NotNull List<ArtifactGenerator> generators) {

        this.options = options;
        this.ffmpegExecutor = ffmpegExecutor;
        this.optionsNormalizer = normalizer;
        this.metadataParser = metadataParser;
        this.artifactGenerators = generators;

    }

    public void process() throws VideoProcessingException {

        try {

            prepareOutputDirectory();

            VideoInfo videoInfo = probeVideoMetadata();

            validateMediaStreams(videoInfo);
            normalizeProcessingOptions(videoInfo);

            generateArtifacts();
            cleanTemporaryFiles();

        } catch (Exception e) {
            throw new VideoProcessingException("Video processing failed", e);
        }
    }

    private void prepareOutputDirectory() throws IOException {
        FileUtil.prepareOutputDirectory(
                options.getOutput(),
                options.isOutputOverwrite()
        );
    }

    @NotNull
    private VideoInfo probeVideoMetadata() throws IOException {
        String probeData = ffmpegExecutor.probeMediaInfo();
        return metadataParser.parse(probeData);
    }

    private void validateMediaStreams(@NotNull VideoInfo videoInfo) {
        if (videoInfo.getVideoStream() == null) {
            throw new IllegalStateException("Source contains no video stream");
        }

        if (options.isAudioOnly() && videoInfo.getAudioStream() == null) {
            throw new IllegalStateException("Audio-only mode requires audio stream");
        }
    }

    private void normalizeProcessingOptions(@NotNull VideoInfo videoInfo) {
        optionsNormalizer.normalizeEncodingOptions(options, videoInfo);
    }

    private void generateArtifacts() throws IOException {
        for (ArtifactGenerator generator : artifactGenerators) {
            if (generator.isEnabled()) generator.generate();
        }
    }

    private void cleanTemporaryFiles() throws IOException {
        FileUtil.deleteFilesByPattern(
                options.getOutput(),
                "_*"
        );
    }

    public static class VideoProcessingException extends Exception {
        public VideoProcessingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}