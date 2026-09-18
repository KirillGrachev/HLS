package team.cinenetwork.processor.impl.rendition;

import org.jetbrains.annotations.NotNull;
import team.cinenetwork.model.AudioStream;

import java.nio.file.Path;

/**
 * Аудио-рендишен: дорожка + её выходная папка внутри HLS.
 *
 * @param stream     исходная дорожка (индекс, язык, тайтл)
 * @param folderName уникальное имя папки ({@code jpn}, {@code rus}, {@code rus_2}, ...)
 * @param directory  абсолютный путь {@code <out>/audio/<folderName>}
 */
public record AudioRendition(@NotNull AudioStream stream,
                             @NotNull String folderName,
                             @NotNull Path directory) {

    /** Относительный URI для мастер-плейлиста: {@code audio/jpn/playlist.m3u8}. */
    public @NotNull String playlistUri() {
        return "audio/" + folderName + "/playlist.m3u8";
    }
}
