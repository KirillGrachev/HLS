package team.cinenetwork.processor.impl.rendition;

import org.jetbrains.annotations.NotNull;
import team.cinenetwork.model.SubtitleStream;

import java.nio.file.Path;

/**
 * Субтитл-рендишен: дорожка + её выходные файлы внутри HLS.
 *
 * @param stream       исходная дорожка (индекс, язык, тайтл, кодек)
 * @param folderName   уникальное имя папки ({@code rus}, {@code eng}, {@code rus_2}, ...)
 * @param directory    абсолютный путь {@code <out>/subtitles/<folderName>}
 * @param vttFile      сконвертированный FFmpeg WebVTT ({@code subtitles.vtt})
 * @param playlistFile HLS-мини-плейлист, оборачивающий vtt ({@code playlist.m3u8})
 */
public record SubtitleRendition(@NotNull SubtitleStream stream,
                                @NotNull String folderName,
                                @NotNull Path directory,
                                @NotNull Path vttFile,
                                @NotNull Path playlistFile) {

    /** Относительный URI для мастер-плейлиста: {@code subtitles/rus/playlist.m3u8}. */
    public @NotNull String playlistUri() {
        return "subtitles/" + folderName + "/playlist.m3u8";
    }
}
