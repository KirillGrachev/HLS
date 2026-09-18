package team.cinenetwork.model;

import org.junit.jupiter.api.Test;
import team.cinenetwork.exception.ErrorCode;
import team.cinenetwork.exception.ProcessingException;
import team.cinenetwork.model.probe.ProbeData;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Покрытие краевых веток моделей и {@link ProbeData}: пустые/пустые-строки значения,
 * мусор в дробях, отсутствующие битрейты,Jackson-сеттеры, невалидный format.duration.
 */
class ModelBranchesTest {

    /* ---------- ProbeData ---------- */

    @Test
    void probeDataEmptyWrapperAndBlankText() {

        assertTrue(ProbeData.empty().value("any").isEmpty());
        assertTrue(ProbeData.of(Map.of("blank", "   ")).text("blank").isEmpty());

    }

    @Test
    void probeDataDecimalFromNumberAndGarbage() {

        assertEquals(23.5, ProbeData.of(Map.of("num", 23.5)).decimal("num").orElseThrow(), 0.001);
        assertTrue(ProbeData.of(Map.of("bad", "abc")).decimal("bad").isEmpty());

    }

    /* ---------- VideoStream ---------- */

    @Test
    void bitrateTagsScanSkipsUnrelatedKeysAndWarnsWhenNothing() {

        // теги без bps/bitrate пропускаются, битрейта нет вовсе → 0 и warn
        VideoStream video = new VideoStream();
        video.populateFromProbeData(Map.of(
                "codec_type", "video", "index", 0,
                "tags", Map.of("title", "something")));

        assertEquals(0, video.getBitrateKbps());

    }

    @Test
    void scaledResolutionSurvivesZeroHeight() {

        VideoStream video = new VideoStream();
        video.populateFromProbeData(Map.of(
                "codec_type", "video", "index", 0, "width", 1920, "height", 0));

        int[] res = video.scaledResolution(1280);
        assertEquals(1280, res[0]);
        assertEquals(1280, res[1]);  // ratio-фолбэк 1.0

    }

    @Test
    void frameRateFractionEdgeForms() {

        VideoStream video = new VideoStream();

        video.parseFrameRate("24");          // без знаменателя → 24.0
        assertEquals(24.0, video.getCalculatedFrameRate().orElseThrow(), 0.001);

        video.parseFrameRate("24/0");        // ноль в знаменателе → empty
        assertTrue(video.getCalculatedFrameRate().isEmpty());

        video.parseFrameRate("1/2/3");       // слишком частей → empty
        assertTrue(video.getCalculatedFrameRate().isEmpty());

    }

    /* ---------- AudioStream ---------- */

    @Test
    void audioJacksonSetterAndBlankRateAndCodecGetter() {

        AudioStream audio = new AudioStream();
        audio.parseSamplingRate("44100 Hz");
        assertEquals(44100, audio.getSamplingRateHz());

        audio.parseSamplingRate("Hz");       // после очистки пусто → 0 без warn-падения
        assertEquals(0, audio.getSamplingRateHz());

        audio.populateFromProbeData(Map.of(
                "codec_type", "audio", "index", 1, "codec_name", "flac",
                "tags", Map.of("title", "Original")));  // скобок нет → язык und
        assertEquals("und", audio.getLanguage());
        assertEquals("flac", audio.getCodecName());

    }

    /* ---------- SubtitleStream ---------- */

    @Test
    void subtitleMissingIndexAndForcedFlagAndMetadataSetter() {

        ProcessingException e = assertThrows(ProcessingException.class,
                () -> new SubtitleStream().populateFromProbeData(Map.of("codec_type", "subtitle")));
        assertEquals(ErrorCode.MISSING_REQUIRED_FIELD, e.getCode());

        SubtitleStream forced = new SubtitleStream();
        forced.populateFromProbeData(Map.of(
                "codec_type", "subtitle", "index", 7, "codec_name", "subrip",
                "disposition", Map.of("forced", 1),
                "tags", Map.of("title", "Signs")));   // без скобок → und
        assertTrue(forced.isForcedTrack());
        assertEquals("und", forced.getLanguage());

        forced.storeMetadataProperty("k", "v");
        assertEquals("v", forced.getMetadata().get("k"));

    }

    /* ---------- VideoInfo ---------- */

    @Test
    void formatDurationGarbageAndStreamWithoutType() {

        VideoInfo info = new VideoInfo();
        info.setFormatDetails(Map.of("duration", "abc"));  // warn, без падения
        assertEquals(0.0, info.getFormatDuration(), 0.001);

        info.processMediaStreams(List.of(Map.of("index", 9)));  // нет codec_type → игнор
        assertTrue(info.getAudioStreams().isEmpty());
        assertTrue(info.getSubtitleStreams().isEmpty());

    }
}
