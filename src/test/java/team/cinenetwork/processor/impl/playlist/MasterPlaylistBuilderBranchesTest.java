package team.cinenetwork.processor.impl.playlist;

import org.junit.jupiter.api.Test;
import team.cinenetwork.exception.ErrorCode;
import team.cinenetwork.exception.ProcessingException;
import team.cinenetwork.model.AudioStream;
import team.cinenetwork.model.SubtitleStream;
import team.cinenetwork.model.VideoInfo;
import team.cinenetwork.model.VideoStream;
import team.cinenetwork.options.AppOptions;
import team.cinenetwork.processor.EncodingProfile;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Покрытие веток билдера мастер-плейлиста: отсутствие аудио-группы, языки DEFAULT,
 * FORCED-субтитры, пропуск нулевых вариантов, отсутствие frame rate, экранирование кавычек.
 */
class MasterPlaylistBuilderBranchesTest {

    @Test
    void noAudioModeOmitsAudioGroupAndBandwidthAddon() {

        AppOptions options = options();
        options.setNoAudio(true);

        String master = new MasterPlaylistBuilder(options, info(), profile("1080p")).build();

        assertFalse(master.contains("TYPE=AUDIO"));
        assertFalse(master.contains("AUDIO=\"audio\""));
        assertTrue(master.contains("BANDWIDTH=8000000"));  // только видео, без надбавки аудио

    }

    @Test
    void audioDefaultLanguageMovesDefaultFlag() {

        AppOptions options = options();
        options.setAudioDefaultLanguage("rus");

        String master = new MasterPlaylistBuilder(options, info(), profile("1080p")).build();

        String rusLine = lineContaining(master, "LANGUAGE=\"rus\"");
        String jpnLine = lineContaining(master, "LANGUAGE=\"jpn\"");
        assertTrue(rusLine.contains("DEFAULT=YES"));
        assertTrue(jpnLine.contains("DEFAULT=NO"));

    }

    @Test
    void subsDefaultLanguageAndForcedFlag() {

        AppOptions options = options();
        options.setSubsDefaultLanguage("rus");

        String master = new MasterPlaylistBuilder(options, infoWithForcedSubs(), profile("1080p")).build();

        String rusLine = master.lines()
                .filter(l -> l.contains("TYPE=SUBTITLES") && l.contains("LANGUAGE=\"rus\""))
                .findFirst().orElseThrow();
        assertTrue(rusLine.contains("DEFAULT=YES"));
        assertTrue(rusLine.contains("FORCED=YES"));

    }

    @Test
    void zeroBitrateVariantsAreSkipped() {

        EncodingProfile profile = new EncodingProfile(List.of(
                new EncodingProfile.Variant(1920, 0, "libx264", "high@5.2", "slow", "1080p"),
                new EncodingProfile.Variant(1280, 5000, "libx264", "high@5.1", "slow", "720p")
        ), 1920, 1920, 12000);

        String master = new MasterPlaylistBuilder(options(), info(), profile).build();

        assertFalse(master.contains("video/1080p/playlist.m3u8"));
        assertTrue(master.contains("video/720p/playlist.m3u8"));

    }

    @Test
    void missingFrameRateThrows() {

        VideoInfo info = new VideoInfo();
        VideoStream video = new VideoStream();
        video.populateFromProbeData(Map.of(
                "codec_type", "video", "index", 0, "width", 1920, "height", 1080));
        info.setVideoStream(video);

        ProcessingException e = assertThrows(ProcessingException.class,
                () -> new MasterPlaylistBuilder(options(), info, profile("1080p")).build());
        assertEquals(ErrorCode.FRAME_RATE_NOT_FOUND, e.getCode());

    }

    @Test
    void quotesInVariantNamesAreEscaped() {

        String master = new MasterPlaylistBuilder(options(), info(), profile("1080\"p")).build();

        assertTrue(master.contains("NAME=\"1080'p\""));

    }

    /* ---------- Фикстуры ---------- */

    private AppOptions options() {

        AppOptions options = new AppOptions();
        options.setInputOption(Path.of("source.mkv"));
        options.setOutput(Path.of("/tmp/out"));
        options.setAudioBitrate(128);
        options.setAudioProfile("aac_low");
        return options;

    }

    private EncodingProfile profile(String name) {

        return new EncodingProfile(List.of(
                new EncodingProfile.Variant(1920, 8000, "libx264", "high@5.2", "slow", name)
        ), 1920, 1920, 12000);

    }

    private VideoInfo info() {

        VideoInfo info = new VideoInfo();
        VideoStream video = new VideoStream();
        video.populateFromProbeData(Map.of(
                "codec_type", "video", "index", 0,
                "width", 1920, "height", 1080, "r_frame_rate", "24/1"));
        info.setVideoStream(video);

        for (int i = 1; i <= 2; i++) {
            AudioStream audio = new AudioStream();
            audio.populateFromProbeData(Map.of(
                    "codec_type", "audio", "index", i, "channels", 2,
                    "tags", Map.of("language", i == 1 ? "jpn" : "rus")));
            info.getAudioStreams().add(audio);
        }
        info.setAudioStream(info.getAudioStreams().getFirst());

        SubtitleStream subs = new SubtitleStream();
        subs.populateFromProbeData(Map.of(
                "codec_type", "subtitle", "index", 3, "codec_name", "ass",
                "tags", Map.of("language", "rus")));
        info.getSubtitleStreams().add(subs);
        return info;

    }

    private VideoInfo infoWithForcedSubs() {

        VideoInfo info = info();
        info.getSubtitleStreams().clear();

        SubtitleStream forced = new SubtitleStream();
        forced.populateFromProbeData(Map.of(
                "codec_type", "subtitle", "index", 3, "codec_name", "ass",
                "disposition", Map.of("forced", 1),
                "tags", Map.of("language", "rus")));
        info.getSubtitleStreams().add(forced);
        return info;

    }

    private String lineContaining(String master, String needle) {

        return master.lines().filter(l -> l.contains(needle)).findFirst().orElseThrow();

    }
}
