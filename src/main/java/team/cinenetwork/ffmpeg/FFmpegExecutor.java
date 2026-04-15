package team.cinenetwork.ffmpeg;

import lombok.extern.slf4j.Slf4j;

import org.jetbrains.annotations.NotNull;

import team.cinenetwork.ffmpeg.type.ProcessingType;
import team.cinenetwork.model.VideoInfo;
import team.cinenetwork.options.AppOptions;
import team.cinenetwork.utils.CommandExecutor;

import java.io.IOException;
import java.util.List;

@Slf4j
public class FFmpegExecutor {

    private final AppOptions options;
    private final FFmpegCommandCoordinator ffmpegCommandCoordinator;

    public FFmpegExecutor(@NotNull AppOptions options,
                          VideoInfo videoInfo) {
        this.options = options;
        this.ffmpegCommandCoordinator = new FFmpegCommandCoordinator(options,
                videoInfo);
    }

    public String probeMediaInfo() throws IOException {

        List<String> command = ffmpegCommandCoordinator.buildProbeCommand();
        log.debug("Executing media probe command: {}",
                String.join(" ", command));

        return CommandExecutor.executeAndCaptureOutput(options.getFfprobe(),
                command);

    }

    public void executeMediaProcessing(@NotNull ProcessingType type)
            throws IOException {

        List<List<String>> commands = switch (type) {
            case POSTER -> ffmpegCommandCoordinator.buildPosterCommands();
            case TRANSCODE -> ffmpegCommandCoordinator.buildTranscodeCommands();
        };

        for (List<String> command : commands) {

            log.info("Executing FFmpeg command: {}", String.join(" ", command));
            CommandExecutor.executeAndStreamOutput(options.getFfmpeg(), command);

        }
    }
}