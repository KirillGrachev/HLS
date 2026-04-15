package team.cinenetwork.ffmpeg.builder.impl;

import org.jetbrains.annotations.NotNull;
import team.cinenetwork.model.VideoInfo;
import team.cinenetwork.options.AppOptions;

import java.util.List;

public interface FFmpegCommandStrategy {

    @NotNull List<String> build();

    interface Context {
        @NotNull AppOptions options();
        @NotNull VideoInfo videoInfo();
    }

}