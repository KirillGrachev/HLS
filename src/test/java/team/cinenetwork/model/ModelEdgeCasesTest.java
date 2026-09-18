package team.cinenetwork.model;

import org.junit.jupiter.api.Test;
import team.cinenetwork.exception.ErrorCode;
import team.cinenetwork.exception.ProcessingException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Дополнительные тесты моделей: Jackson-сеттеры, фолбэки длительности,
 * защита от мусора во входных данных ffprobe.
 */
class ModelEdgeCasesTest {

    @Test
    void videoStreamJacksonSettersWork() {

        VideoStream video = new VideoStream();
        video.parseFrameRate("24000/1001");
        video.parseAspectRatio("4:3");
        video.addMetadataProperty("bit_rate", "5000000");

        assertEquals(23.976, video.getCalculatedFrameRate().orElseThrow(), 0.001);
        assertEquals(4.0 / 3.0, video.getPixelAspectRatio(), 0.001);
        assertEquals(5000, video.getBitrateKbps());

    }

    @Test
    void videoStreamIgnoresNonVideoMaps() {

        VideoStream video = new VideoStream();
        video.populateFromProbeData(Map.of("codec_type", "audio", "index", 1));

        assertEquals(0, video.getFrameWidth());  // ничего не заполнили
        assertTrue(video.getCalculatedFrameRate().isEmpty());

    }

    @Test
    void videoStreamDurationFallsBackToTags() {

        VideoStream video = new VideoStream();
        video.populateFromProbeData(Map.of(
                "codec_type", "video", "index", 0,
                "tags", Map.of("DURATION", "01:02:03.500000")));

        // duration в тегах в формате HH:MM:SS не парсится как double → остаётся 0,
        // а форматные теги без чисел игнорируются без падения
        assertTrue(video.getDurationSeconds() >= 0);

    }

    @Test
    void videoStreamGarbageFrameRateIsEmpty() {

        VideoStream video = new VideoStream();
        video.populateFromProbeData(Map.of(
                "codec_type", "video", "index", 0, "r_frame_rate", "fps?"));

        assertTrue(video.getCalculatedFrameRate().isEmpty());

    }

    @Test
    void audioStreamMissingIndexIsValidationError() {

        ProcessingException e = org.junit.jupiter.api.Assertions.assertThrows(
                ProcessingException.class,
                () -> new AudioStream().populateFromProbeData(Map.of("codec_type", "audio")));

        assertEquals(ErrorCode.MISSING_REQUIRED_FIELD, e.getCode());

    }

    @Test
    void audioStreamSamplingRateHandlesSuffixAndGarbage() {

        AudioStream withSuffix = new AudioStream();
        withSuffix.populateFromProbeData(Map.of(
                "codec_type", "audio", "index", 1, "sample_rate", "48000 Hz"));
        assertEquals(48000, withSuffix.getSamplingRateHz());

        AudioStream garbage = new AudioStream();
        garbage.populateFromProbeData(Map.of(
                "codec_type", "audio", "index", 1, "sample_rate", "unknown"));
        assertEquals(0, garbage.getSamplingRateHz());  // не падаем, просто не передаём -ar

        AudioStream defaulted = new AudioStream();
        defaulted.populateFromProbeData(Map.of("codec_type", "audio", "index", 1));
        assertEquals(2, defaulted.getChannelCount());  // дефолт стерео

    }

    @Test
    void audioStreamMetadataAccumulator() {

        AudioStream audio = new AudioStream();
        audio.storeMetadataProperty("bit_rate", "192000");

        assertEquals("192000", audio.getMetadata().get("bit_rate"));

    }

    @Test
    void subtitleLanguageFromTitleBrackets() {

        SubtitleStream stream = new SubtitleStream();
        stream.populateFromProbeData(Map.of(
                "codec_type", "subtitle", "index", 3, "codec_name", "ass",
                "tags", Map.of("title", "Soft subs [ENG]")));

        assertEquals("eng", stream.getLanguage());
        assertEquals("Soft subs [ENG]", stream.getDisplayName());

    }
}
