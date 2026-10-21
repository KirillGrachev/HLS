package team.cinenetwork.processor.impl.playlist;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import team.cinenetwork.model.VideoInfo;
import team.cinenetwork.options.AppOptions;
import team.cinenetwork.processor.EncodingProfile;
import team.cinenetwork.processor.impl.artifact.ArtifactGenerator;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

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

        Optional<String> content = builder.build();

        if (content.isEmpty()) {
            log.warn("Skipping master playlist: builder produced no playable content");
            return;
        }

        Path playlistPath = options.getOutput().resolve(options.getHlsMasterPlaylist());
        writer.write(content.get(), playlistPath);

    }

    /**
     * Мастер-плейлист имеет смысл, только если HLS-лестница вообще генерируется:
     * при {@code --hls-disable} видео-плейлистов на диске не будет, и мастер
     * ссылался бы в пустоту; в audio-only режиме лестницы нет по определению.
     */
    @Override
    public boolean isEnabled() {

        return !options.isPlaylistDisable()
                && !options.isHlsDisable()
                && !options.isAudioOnly();

    }

    @Override
    public String name() {
        return "Master playlist (" + options.getHlsMasterPlaylist() + ")";
    }
}
