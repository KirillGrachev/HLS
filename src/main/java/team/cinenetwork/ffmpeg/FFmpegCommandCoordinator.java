package team.cinenetwork.ffmpeg;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import team.cinenetwork.ffmpeg.builder.*;
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