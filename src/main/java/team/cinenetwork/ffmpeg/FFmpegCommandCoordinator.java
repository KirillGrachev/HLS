package team.cinenetwork.ffmpeg;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import team.cinenetwork.ffmpeg.builder.*;
import team.cinenetwork.ffmpeg.exceptions.ErrorCode;
import team.cinenetwork.model.AudioStream;
import team.cinenetwork.model.VideoInfo;
import team.cinenetwork.options.AppOptions;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class FFmpegCommandCoordinator {

    private final AppOptions options;
    private final VideoInfo videoInfo;

    public @NotNull List<List<String>> buildTranscodeCommands() {

        List<List<String>> commands = new ArrayList<>();

        if (!options.isAudioOnly()) {

            for (int i = 0; i < options.getVideoWidths().size(); i++) {

                var strategy = new HlsVideoStrategy(options,
                        videoInfo, i);
                commands.add(strategy.build());

            }
        }

        if (options.isAudioOnly()) {

            var strategy = new AudioOnlyStrategy(options,
                    videoInfo);
            commands.add(strategy.build());

        }

        return commands;

    }

    public @NotNull List<List<String>> buildPosterCommands() {

        List<List<String>> commands = new ArrayList<>();

        if (!options.isAudioOnly()) {

            for (int i = 0; i < options.getVideoWidths().size(); i++) {

                int width = options.getVideoWidths().get(i);
                if (width == 0) continue;

                var strategy = new PosterStrategy(options, videoInfo, i);
                commands.add(strategy.build());

            }

        }

        return commands;

    }

    public @NotNull List<List<String>> buildSingleFileCommands() {

        List<List<String>> commands = new ArrayList<>();

        if (!options.isSingleFile()) {
            return commands;
        }

        if (options.isAllAudioTracks()) {

            if (videoInfo.getAudioStreams() == null
                    || videoInfo.getAudioStreams().isEmpty()) {

                log.warn("All audio tracks mode requested, but no audio streams found.");
                return commands;

            }

            for (AudioStream audio : videoInfo.getAudioStreams()) {

                var strategy = new SingleFileStrategy(options, videoInfo, audio,
                        options.getOutputFilename());

                commands.add(strategy.build());

            }

        } else {

            AudioStream selectedAudio;

            if (options.getAudioStream() != null) {

                selectedAudio = videoInfo.findAudioStream(options.getAudioStream())
                        .orElseThrow(() -> team.cinenetwork.ffmpeg.exceptions.Exception.of(
                                ErrorCode.AUDIO_STREAM_NOT_FOUND,
                                "Selected audio stream not found: " + options.getAudioStream())
                        );

            } else if (videoInfo.getAudioStream() != null) {
                selectedAudio = videoInfo.getAudioStream();
            } else {

                throw team.cinenetwork.ffmpeg.exceptions.Exception.of(ErrorCode.AUDIO_STREAM_NOT_FOUND,
                        "No audio stream available for single file output");

            }

            var strategy = new SingleFileStrategy(options, videoInfo, selectedAudio,
                    options.getOutputFilename());
            commands.add(strategy.build());

        }

        return commands;

    }

    public @NotNull List<String> buildProbeCommand() {
        return List.of(
                "-v", "quiet",
                "-print_format", "json",
                "-show_format",
                "-show_streams",
                options.getInput().toString()
        );
    }
}