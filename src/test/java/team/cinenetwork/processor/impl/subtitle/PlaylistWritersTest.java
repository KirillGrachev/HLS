package team.cinenetwork.processor.impl.subtitle;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import team.cinenetwork.model.SubtitleStream;
import team.cinenetwork.processor.impl.playlist.PlaylistWriter;
import team.cinenetwork.processor.impl.rendition.SubtitleRendition;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Тесты писателей плейлистов: мастер/субтитльный оборачиватель.
 */
class PlaylistWritersTest {

    @TempDir
    Path tempDir;

    @Test
    void playlistWriterCreatesFoldersAndSkipsEmpty() throws IOException {

        PlaylistWriter writer = new PlaylistWriter();
        Path target = tempDir.resolve("a/b/playlist.m3u8");

        writer.write("", target);
        assertFalse(Files.exists(target));  // пустое содержимое не пишем

        writer.write("#EXTM3U\n", target);
        assertTrue(Files.readString(target).startsWith("#EXTM3U"));

    }

    @Test
    void subtitlePlaylistWrapsVttFile() throws IOException {

        SubtitleStream stream = new SubtitleStream();
        stream.populateFromProbeData(Map.of(
                "codec_type", "subtitle", "index", 3, "codec_name", "ass",
                "tags", Map.of("language", "rus")));

        Path dir = tempDir.resolve("subtitles/rus");
        SubtitleRendition rendition = new SubtitleRendition(stream, "rus", dir,
                dir.resolve("subtitles.vtt"), dir.resolve("playlist.m3u8"));

        new SubtitlePlaylistWriter(new PlaylistWriter()).write(rendition, 1429.5);

        String content = Files.readString(rendition.playlistFile());
        assertTrue(content.contains("#EXT-X-TARGETDURATION:1430"));
        assertTrue(content.contains("#EXT-X-PLAYLIST-TYPE:VOD"));
        assertTrue(content.contains("#EXTINF:1429.500,"));
        assertTrue(content.contains("subtitles.vtt"));
        assertTrue(content.contains("#EXT-X-ENDLIST"));

    }

    @Test
    void zeroDurationDoesNotBreakPlaylist() throws IOException {

        SubtitleStream stream = new SubtitleStream();
        stream.populateFromProbeData(Map.of(
                "codec_type", "subtitle", "index", 4, "codec_name", "subrip"));

        Path dir = tempDir.resolve("subtitles/und");
        SubtitleRendition rendition = new SubtitleRendition(stream, "und", dir,
                dir.resolve("subtitles.vtt"), dir.resolve("playlist.m3u8"));

        new SubtitlePlaylistWriter(new PlaylistWriter()).write(rendition, 0.0);

        // длительность защищена снизу единицей
        assertTrue(Files.readString(rendition.playlistFile()).contains("#EXT-X-TARGETDURATION:1"));

    }
}
