package team.cinenetwork.processor.impl.playlist;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Тесты кодековых тегов RFC 6381 для мастер-плейлиста.
 */
class CodecTagsTest {

    @Test
    void h264ProfileLevelToAvcTag() {

        assertEquals("avc1.640034", CodecTags.videoCodecTag("libx264", "high@5.2").orElseThrow());
        assertEquals("avc1.640034", CodecTags.videoCodecTag("h264", "high@5.2").orElseThrow());
        assertEquals("avc1.4d0028", CodecTags.videoCodecTag("libx264", "main@4.0").orElseThrow());
        assertEquals("avc1.42001f", CodecTags.videoCodecTag("libx264", "baseline@3.1").orElseThrow());

    }

    @Test
    void unknownCodecsProduceNoTag() {

        assertTrue(CodecTags.videoCodecTag("libx265", "high@5.2").isEmpty());
        assertTrue(CodecTags.videoCodecTag("libx264", "magic@5.2").isEmpty());
        assertTrue(CodecTags.videoCodecTag("libx264", "high").isEmpty());
        assertTrue(CodecTags.videoCodecTag(null, "high@5.2").isEmpty());

    }

    @Test
    void malformedLevelYieldsNoTag() {

        assertTrue(CodecTags.videoCodecTag("libx264", "high@x").isEmpty());

    }

    @Test
    void aacProfilesToMp4aTags() {

        assertEquals("mp4a.40.2", CodecTags.audioCodecTag("aac_low").orElseThrow());
        assertEquals("mp4a.40.5", CodecTags.audioCodecTag("aac_he").orElseThrow());
        assertEquals("mp4a.40.29", CodecTags.audioCodecTag("aac_he_v2").orElseThrow());
        assertTrue(CodecTags.audioCodecTag("opus").isEmpty());

    }
}
