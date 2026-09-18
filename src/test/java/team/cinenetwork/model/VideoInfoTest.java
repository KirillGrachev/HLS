package team.cinenetwork.model;

import org.junit.jupiter.api.Test;
import team.cinenetwork.processor.impl.meta.MetadataParser;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Тесты {@link VideoInfo}: раскладка потоков по типам, селекторы аудио, длительность.
 */
class VideoInfoTest {

    @Test
    void registersAllStreamsByType() {

        VideoInfo info = animeInfo();

        assertEquals(2, info.getAudioStreams().size());  // обе озвучки собраны (баг легаси починен)
        assertEquals(2, info.getSubtitleStreams().size());
        assertEquals(0, info.getVideoStream().getStreamIndex());

    }

    @Test
    void firstAudioBecomesDefault() {

        VideoInfo info = animeInfo();

        assertEquals("jpn", info.getAudioStream().getLanguage());

    }

    @Test
    void findAudioStreamUnderstandsSelectors() {

        VideoInfo info = animeInfo();

        assertEquals("rus", info.findAudioStream("index:2").orElseThrow().getLanguage());
        assertEquals("rus", info.findAudioStream("2").orElseThrow().getLanguage());
        assertEquals("jpn", info.findAudioStream(null).orElseThrow().getLanguage());
        assertTrue(info.findAudioStream("index:99").isEmpty());
        assertTrue(info.findAudioStream("abc").isEmpty());

    }

    @Test
    void durationFallsBackToFormat() {

        VideoInfo info = new VideoInfo();
        info.setFormatDetails(Map.of("duration", "1429.5"));

        assertEquals(1429.5, info.getDuration(), 0.001);

    }

    @Test
    void metadataParserReadsProbeJson() throws IOException {

        String json = """
                {
                  "format": {"duration": "100.0"},
                  "streams": [
                    {"index":0,"codec_type":"video","width":1280,"height":720,"r_frame_rate":"24/1"},
                    {"index":1,"codec_type":"audio","channels":2,"tags":{"language":"jpn"}},
                    {"index":2,"codec_type":"subtitle","codec_name":"ass","tags":{"language":"rus"}}
                  ]
                }
                """;

        VideoInfo info = new MetadataParser().parse(json);

        assertEquals(1280, info.getVideoStream().getFrameWidth());
        assertEquals(1, info.getAudioStreams().size());
        assertEquals(1, info.getSubtitleStreams().size());
        assertEquals(100.0, info.getDuration(), 0.001);

    }

    /* ---------- Фикстуры ---------- */

    private VideoInfo animeInfo() {

        VideoInfo info = new VideoInfo();
        info.processMediaStreams(List.of(
                Map.of("codec_type", "video", "index", 0, "width", 1920, "height", 1080),
                Map.of("codec_type", "audio", "index", 1, "channels", 2,
                        "tags", Map.of("language", "jpn")),
                Map.of("codec_type", "audio", "index", 2, "channels", 2,
                        "tags", Map.of("language", "rus")),
                Map.of("codec_type", "subtitle", "index", 3, "codec_name", "ass",
                        "tags", Map.of("language", "rus")),
                Map.of("codec_type", "subtitle", "index", 4, "codec_name", "hdmv_pgs_subtitle",
                        "tags", Map.of("language", "jpn")),
                Map.of("codec_type", "data", "index", 5)  // игнорируется
        ));
        return info;

    }
}
