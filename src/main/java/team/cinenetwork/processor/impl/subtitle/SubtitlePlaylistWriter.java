package team.cinenetwork.processor.impl.subtitle;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import team.cinenetwork.processor.impl.playlist.PlaylistWriter;
import team.cinenetwork.processor.impl.rendition.SubtitleRendition;

import java.io.IOException;
import java.util.Locale;

/**
 * Оборачивает готовый WebVTT-файл в минимальный HLS-плейлист.
 *
 * <p>Ровно та форма, которую требует спецификация HLS для внешних субтитров:
 * медиа-плейлист с одним «сегментом» — всем .vtt файлом. Плеер читает плейлист
 * из мастера ({@code EXT-X-MEDIA:TYPE=SUBTITLES}) и показывает субтитры с таймингом,
 * синхронным видео.
 *
 * <pre>
 * #EXTM3U
 * #EXT-X-TARGETDURATION:1430
 * #EXT-X-VERSION:3
 * #EXT-X-PLAYLIST-TYPE:VOD
 * #EXT-X-MEDIA-SEQUENCE:0
 * #EXTINF:1429.500,
 * subtitles.vtt
 * #EXT-X-ENDLIST
 * </pre>
 */
@Slf4j
@RequiredArgsConstructor
public class SubtitlePlaylistWriter {

    private final PlaylistWriter playlistWriter;

    public void write(@NotNull SubtitleRendition rendition,
                      double durationSeconds)
            throws IOException {

        double safeDuration = Math.max(1.0, durationSeconds);
        int targetDuration = (int) Math.ceil(safeDuration);

        String content = "#EXTM3U\n"
                + "#EXT-X-TARGETDURATION:" + targetDuration + "\n"
                + "#EXT-X-VERSION:3\n"
                + "#EXT-X-PLAYLIST-TYPE:VOD\n"
                + "#EXT-X-MEDIA-SEQUENCE:0\n"
                + String.format(Locale.ROOT, "#EXTINF:%.3f,%n", safeDuration)
                + rendition.vttFile().getFileName() + "\n"
                + "#EXT-X-ENDLIST\n";

        playlistWriter.write(content, rendition.playlistFile());
        log.debug("Subtitle playlist written for track '{}'", rendition.stream().getDisplayName());

    }
}
