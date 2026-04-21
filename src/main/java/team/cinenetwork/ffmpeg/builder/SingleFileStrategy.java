package team.cinenetwork.ffmpeg.builder;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import team.cinenetwork.ffmpeg.builder.impl.AbstractFFmpegStrategy;
import team.cinenetwork.model.AudioStream;
import team.cinenetwork.model.VideoInfo;
import team.cinenetwork.options.AppOptions;

import java.nio.file.Path;
import java.util.List;

@Slf4j
public class SingleFileStrategy extends AbstractFFmpegStrategy {

    private final AudioStream targetAudioStream;
    private final String customOutputName;

    public SingleFileStrategy(AppOptions options,
                              VideoInfo videoInfo,
                              AudioStream targetAudioStream,
                              String customOutputName) {
        super(options, videoInfo);
        this.targetAudioStream = targetAudioStream;
        this.customOutputName = customOutputName;
    }

    @Override
    public @NotNull List<String> build() {

        ensureDirectory(options.getOutput());

        Path outputPath = determineOutputPath();
        String language = targetAudioStream.getLanguage() != null
                ? targetAudioStream.getLanguage() : "und";

        log.info("Building single file command for audio language: {}", language);

        return List.of(
                "-i", options.getInput().toString(),
                "-map", "0:" + videoInfo.getVideoStream().getStreamIndex(),
                "-map", "0:" + targetAudioStream.getStreamIndex(),
                "-c", "copy",
                "-metas:a:0", "language=" + language,
                "-f", "mp4",
                "-movflags", "+faststart",
                outputPath.toString()
        );
    }

    private @NotNull Path determineOutputPath() {

        Path outputDir = options.getOutput();

        String baseName;
        if (customOutputName != null && !customOutputName.isEmpty())
            baseName = customOutputName;
        else {

            baseName = options.getInput().getFileName().toString();

            int dotIndex = baseName.lastIndexOf('.');
            if (dotIndex > 0) {
                baseName = baseName.substring(0, dotIndex);
            }

        }

        String lang = targetAudioStream.getLanguage();
        if (lang == null || lang.isEmpty()) lang = "und";

        String safeLang = lang.replaceAll("[^a-zA-Z0-9_-]", "_");

        int trackIndexForLang = 0;
        if (videoInfo.getAudioStreams() != null) {

            for (AudioStream stream : videoInfo.getAudioStreams()) {

                if (stream.getStreamIndex()
                        == targetAudioStream.getStreamIndex())
                    break;

                String otherLang = stream.getLanguage();
                if (otherLang == null) otherLang = "und";

                if (otherLang.equals(lang))
                    trackIndexForLang++;

            }

        }

        String suffix = trackIndexForLang == 0 ? safeLang
                : safeLang + "_" + (trackIndexForLang + 1);
        String finalFileName = baseName + "_" + suffix + ".mp4";

        return outputDir.resolve(finalFileName);

    }
}