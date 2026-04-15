package team.cinenetwork.processor.impl.playlist;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import team.cinenetwork.model.VideoInfo;
import team.cinenetwork.options.AppOptions;
import team.cinenetwork.processor.impl.artifact.ArtifactGenerator;

import java.io.IOException;
import java.nio.file.Path;

@Slf4j
@RequiredArgsConstructor
public class PlaylistGenerator implements ArtifactGenerator {

    private final AppOptions options;

    private final MasterPlaylistBuilder builder;
    private final PlaylistWriter writer;

    public PlaylistGenerator(@NotNull AppOptions options, VideoInfo videoInfo) {
        this(options,
                new MasterPlaylistBuilder(options, videoInfo),
                new PlaylistWriter());
    }

    @Override
    public void generate() throws IOException {

        if (!isEnabled()) {
            log.debug("Playlist generation disabled by options");
            return;
        }

        String content = builder.build();
        if (content.isEmpty()) {
            log.debug("Skipping master playlist - no video variants to include");
            return;
        }

        Path playlistPath = Path.of(options.getOutput().toString(),
                options.getHlsMasterPlaylist());

        writer.write(content, playlistPath);

    }

    @Override
    public boolean isEnabled() {
        return !options.isPlaylistDisable();
    }
}