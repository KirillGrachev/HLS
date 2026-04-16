package team.cinenetwork;

import lombok.extern.slf4j.Slf4j;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import picocli.CommandLine;

import team.cinenetwork.ffmpeg.FFmpegExecutor;
import team.cinenetwork.ffmpeg.exceptions.ErrorCode;
import team.cinenetwork.model.VideoInfo;
import team.cinenetwork.options.AppOptions;
import team.cinenetwork.processor.*;

import team.cinenetwork.processor.impl.artifact.ArtifactGenerator;
import team.cinenetwork.processor.impl.hls.HlsGenerator;
import team.cinenetwork.processor.impl.meta.MetadataParser;
import team.cinenetwork.processor.impl.playlist.PlaylistGenerator;
import team.cinenetwork.processor.impl.poster.PosterGenerator;

import java.util.List;

@Slf4j
public class Main {

    public static void main(String[] args) {

        AppOptions options = parseCommandLine(args);
        if (options == null) return;

        try {

            VideoProcessor processor = createVideoProcessor(options);
            processor.process();

            log.info("Video processing completed successfully");

        } catch (team.cinenetwork.ffmpeg.exceptions.Exception e) {

            log.error("Processing failed: {}", e.getMessage());
            log.debug("Failure details:", e);

            System.exit(1);

        }
    }

    private static @Nullable AppOptions parseCommandLine(String[] args) {

        try {

            AppOptions options = new AppOptions();
            new CommandLine(options).parseArgs(args);

            return options;

        } catch (Exception e) {

            log.error("Invalid command line arguments: {}", e.getMessage());
            new CommandLine(new AppOptions()).usage(System.out);

            return null;

        }
    }

    @NotNull
    private static VideoProcessor createVideoProcessor(AppOptions options)
            throws team.cinenetwork.ffmpeg.exceptions.Exception {

        try {

            // Инициализация зависимостей
            MetadataParser metadataParser = new MetadataParser();
            OptionsNormalizer normalizer = new OptionsNormalizer();

            // Получение метаданных видео
            FFmpegExecutor ffmpegExecutor = new FFmpegExecutor(options, null);
            VideoInfo videoInfo = metadataParser.parse(ffmpegExecutor.probeMediaInfo());

            // Переинициализация FFmpegExecutor с полученными метаданными
            ffmpegExecutor = new FFmpegExecutor(options, videoInfo);

            // Инициализация генераторов артефактов
            List<ArtifactGenerator> generators = List.of(
                    new PosterGenerator(options, videoInfo),
                    new HlsGenerator(options, videoInfo),
                    new PlaylistGenerator(options, videoInfo)
            );

            return new VideoProcessor(
                    options, ffmpegExecutor,
                    normalizer, metadataParser,
                    generators
            );

        } catch (Exception e) {
            throw team.cinenetwork.ffmpeg.exceptions.Exception.of(ErrorCode.PROCESSOR_INITIALIZATION_FAILED,
                    "Processor initialization failed", e);
        }
    }
}