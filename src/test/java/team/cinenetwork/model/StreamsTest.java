package team.cinenetwork.model;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Тесты моделей дорожек: геометрия/битрейт видео, язык аудио, конвертируемость субтитров.
 */
class StreamsTest {

    @Test
    void videoStreamParsesGeometryAndRate() {

        VideoStream video = video(1920, 1080, "24000/1001", "8000000");

        assertEquals(1920, video.getFrameWidth());
        assertEquals(23.976, video.getCalculatedFrameRate().orElseThrow(), 0.001);
        assertEquals(8000, video.getBitrateKbps());

    }

    @Test
    void scaledResolutionKeepsProportionsAndEvenSides() {

        VideoStream video = video(1920, 1080, "24/1", "1");

        int[] scaled = video.scaledResolution(1280);

        assertEquals(1280, scaled[0]);
        assertEquals(720, scaled[1]);
        assertEquals(0, scaled[0] % 2);
        assertEquals(0, scaled[1] % 2);

    }

    @Test
    void bitrateFallsBackToTags() {

        VideoStream video = new VideoStream();
        video.populateFromProbeData(Map.of(
                "codec_type", "video", "index", 0,
                "tags", Map.of("BPS", "6000000")));

        assertEquals(6000, video.getBitrateKbps());

    }

    @Test
    void audioLanguageResolutionOrder() {

        // 1) tags.language
        AudioStream fromTag = audio(1, Map.of("language", "JPN", "title", "Japanese"));
        assertEquals("jpn", fromTag.getLanguage());
        assertEquals("Japanese", fromTag.getDisplayName());

        // 2) код в скобках тайтла
        AudioStream fromTitle = audio(2, Map.of("title", "Dub (Ru)"));
        assertEquals("ru", fromTitle.getLanguage());

        // 3) фолбэк und; displayName тогда — код языка
        AudioStream und = audio(3, Map.of());
        assertEquals("und", und.getLanguage());
        assertEquals("und", und.getDisplayName());

    }

    @Test
    void subtitleTextBasedCheck() {

        SubtitleStream ass = subtitle(3, "ass");
        SubtitleStream pgs = subtitle(4, "hdmv_pgs_subtitle");

        assertTrue(ass.isTextBased());
        assertFalse(pgs.isTextBased());

    }

    @Test
    void subtitleDispositionFlags() {

        SubtitleStream stream = new SubtitleStream();
        stream.populateFromProbeData(Map.of(
                "codec_type", "subtitle", "index", 5, "codec_name", "subrip",
                "disposition", Map.of("default", 1, "forced", 0),
                "tags", Map.of("language", "eng")));

        assertTrue(stream.isDefaultTrack());
        assertFalse(stream.isForcedTrack());

    }

    /* ---------- Фикстуры ---------- */

    private VideoStream video(int width, int height, String rate, String bitrate) {

        VideoStream video = new VideoStream();
        video.populateFromProbeData(Map.of(
                "codec_type", "video", "index", 0,
                "width", width, "height", height,
                "r_frame_rate", rate, "bit_rate", bitrate));
        return video;

    }

    private AudioStream audio(int index, Map<String, Object> tags) {

        AudioStream audio = new AudioStream();
        audio.populateFromProbeData(Map.of(
                "codec_type", "audio", "index", index, "channels", 2, "tags", tags));
        return audio;

    }

    private SubtitleStream subtitle(int index, String codec) {

        SubtitleStream stream = new SubtitleStream();
        stream.populateFromProbeData(Map.of(
                "codec_type", "subtitle", "index", index, "codec_name", codec));
        return stream;

    }
}
