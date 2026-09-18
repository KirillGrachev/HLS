package team.cinenetwork.processor.impl.playlist;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import team.cinenetwork.model.VideoInfo;
import team.cinenetwork.options.AppOptions;
import team.cinenetwork.processor.EncodingProfile;
import team.cinenetwork.processor.impl.artifact.ArtifactGenerator;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Мастер-плейлист: итоговый артефакт, связывающий все рендишены
 * (видео-лестницу + аудио + субтитры) в единый HLS-поток.
 */
@Slf4j
public class PlaylistGenerator implements ArtifactGenerator {

    private final AppOptions options;
    private final MasterPlaylistBuilder builder;
    private final PlaylistWriter writer;

    public PlaylistGenerator(@NotNull AppOptions options,
                             @NotNull VideoInfo videoInfo,
                             @NotNull EncodingProfile profile) {
        this(options,
                new MasterPlaylistBuilder(options, videoInfo, profile),
                new PlaylistWriter());
    }

    private PlaylistGenerator(AppOptions options,
                              MasterPlaylistBuilder builder,
                              PlaylistWriter writer) {

        this.options = options;
        this.builder = builder;
        this.writer = writer;

    }

    @Override
    public void generate() throws IOException {

        String content = builder.build();
        if (content.isEmpty()) {
            log.debug("Skipping master playlist - no video variants to include");
            return;
        }

        Path playlistPath = options.getOutput().resolve(options.getHlsMasterPlaylist());
        writer.write(content, playlistPath);

    }

    @Override
    public boolean isEnabled() {
        return !options.isPlaylistDisable();
    }

    @Override
    public String name() {
        return "Master playlist (" + options.getHlsMasterPlaylist() + ")";
    }
}
