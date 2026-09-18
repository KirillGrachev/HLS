package team.cinenetwork.model.probe;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Тесты {@link ProbeData}: единого типизированного доступа к JSON ffprobe.
 */
class ProbeDataTest {

    @Test
    void integerUnderstandsNumbersAndStrings() {

        ProbeData probe = ProbeData.of(Map.of("asNumber", 1920, "asString", "1080", "garbage", "N/A"));

        assertEquals(1920, probe.integer("asNumber").orElse(-1));
        assertEquals(1080, probe.integer("asString").orElse(-1));
        assertTrue(probe.integer("garbage").isEmpty());
        assertTrue(probe.integer("missing").isEmpty());

    }

    @Test
    void decimalNormalizesComma() {

        ProbeData probe = ProbeData.of(Map.of("dot", "23.900000", "comma", "1,5"));

        assertEquals(23.9, probe.decimal("dot").orElse(-1), 0.0001);
        assertEquals(1.5, probe.decimal("comma").orElse(-1), 0.0001);

    }

    @Test
    void nestedMissingSectionIsEmptyWrapperNotNpe() {

        ProbeData probe = ProbeData.of(Map.of("tags", Map.of("language", "jpn")));

        assertEquals("jpn", probe.languageTag().orElse("und"));
        assertTrue(ProbeData.of(Map.of()).nested("tags").text("language").isEmpty());
        assertTrue(ProbeData.of(null).value("any").isEmpty());

    }

    @Test
    void isCodecTypeMatchesCaseInsensitive() {

        ProbeData probe = ProbeData.of(Map.of("codec_type", "Subtitle"));

        assertTrue(probe.isCodecType("subtitle"));
        assertFalse(probe.isCodecType("audio"));

    }

    @Test
    void titleAndLanguageComeFromTags() {

        ProbeData probe = ProbeData.of(Map.of("tags", Map.of("language", "RUS", "title", "Subs (Ru)")));

        assertEquals("RUS", probe.languageTag().orElseThrow());
        assertEquals("Subs (Ru)", probe.titleTag().orElseThrow());

    }
}
