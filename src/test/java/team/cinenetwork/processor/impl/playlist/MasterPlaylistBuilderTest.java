package team.cinenetwork.processor.impl.playlist;

import org.junit.jupiter.api.Test;
import team.cinenetwork.model.AudioStream;
import team.cinenetwork.model.SubtitleStream;
import team.cinenetwork.model.VideoInfo;
import team.cinenetwork.model.VideoStream;
import team.cinenetwork.options.AppOptions;
import team.cinenetwork.processor.EncodingProfile;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Тесты мастер-плейлиста: аудио- и субтитл-группы, BANDWIDTH в битах/сек,
 * CODECS-теги, корректные URI.
 */
class MasterPlaylistBuilderTest {

    @Test
    void buildsMasterWithAudioAndSubtitleGroups() {

        AppOptions options = baseOptions();
        VideoInfo videoInfo = animeVideoInfo();
        EncodingProfile profile = singleVariantProfile();

        String master = new MasterPlaylistBuilder(options, videoInfo, profile).build();

        // Аудио-рендишены: обе озвучки, первая — DEFAULT
        assertTrue(master.contains("#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID=\"audio\""));
        assertTrue(master.contains("LANGUAGE=\"jpn\""));
        assertTrue(master.contains("LANGUAGE=\"rus\""));
        assertTrue(master.contains("URI=\"audio/jpn/playlist.m3u8\""));
        assertTrue(master.contains("URI=\"audio/rus/playlist.m3u8\""));

        // Субтитры: только текстовые дорожки (PGS отфильтрован раньше, на планировании)
        assertTrue(master.contains("#EXT-X-MEDIA:TYPE=SUBTITLES,GROUP-ID=\"subtitles\""));
        assertTrue(master.contains("URI=\"subtitles/rus/playlist.m3u8\""));

        // Варианты ссылаются на группы
        assertTrue(master.contains("AUDIO=\"audio\""));
        assertTrue(master.contains("SUBTITLES=\"subtitles\""));

        // BANDWIDTH в битах/сек: (8000 kbps видео + 128 kbps аудио) * 1000
        assertTrue(master.contains("BANDWIDTH=8128000"), master);

        // Кодековый тег H.264 high@5.2
        assertTrue(master.contains("CODECS=\"avc1.640034\""), master);

        // URI варианта совпадает с папкой, куда пишет HlsVideoStrategy
        assertTrue(master.contains("video/1080p/playlist.m3u8"));

    }

    @Test
    void returnsEmptyForAudioOnlyMode() {

        AppOptions options = baseOptions();
        options.setAudioOnly(true);

        String master = new MasterPlaylistBuilder(options, animeVideoInfo(),
                singleVariantProfile()).build();

        assertTrue(master.isEmpty());

    }

    @Test
    void omitsSubtitleGroupWhenDisabled() {

        AppOptions options = baseOptions();
        options.setSubsEnabled(false);

        String master = new MasterPlaylistBuilder(options, animeVideoInfo(),
                singleVariantProfile()).build();

        assertFalse(master.contains("TYPE=SUBTITLES"));
        assertFalse(master.contains("SUBTITLES=\"subtitles\""));

    }

    /* ---------- Фикстуры ---------- */

    private AppOptions baseOptions() {

        AppOptions options = new AppOptions();
        options.setInputOption(Path.of("source.mkv"));
        options.setOutput(Path.of("/tmp/out"));
        options.setRatio("16:9");
        options.setAudioBitrate(128);
        options.setAudioProfile("aac_low");

        return options;

    }

    private VideoInfo animeVideoInfo() {

        VideoInfo videoInfo = new VideoInfo();

        VideoStream video = new VideoStream();
        video.populateFromProbeData(Map.of(
                "codec_type", "video",
                "index", 0,
                "width", 1920,
                "height", 1080,
                "r_frame_rate", "24000/1001",
                "bit_rate", "8000000"));
        videoInfo.setVideoStream(video);

        AudioStream jpn = new AudioStream();
        jpn.populateFromProbeData(Map.of(
                "codec_type", "audio", "index", 1, "channels", 2,
                "tags", Map.of("language", "jpn", "title", "Japanese")));

        AudioStream rus = new AudioStream();
        rus.populateFromProbeData(Map.of(
                "codec_type", "audio", "index", 2, "channels", 2,
                "tags", Map.of("language", "rus", "title", "Russian dub")));

        videoInfo.getAudioStreams().addAll(List.of(jpn, rus));
        videoInfo.setAudioStream(jpn);

        SubtitleStream rusSubs = new SubtitleStream();
        rusSubs.populateFromProbeData(Map.of(
                "codec_type", "subtitle", "index", 3, "codec_name", "ass",
                "tags", Map.of("language", "rus", "title", "Subs (Ru)")));
        videoInfo.getSubtitleStreams().add(rusSubs);

        return videoInfo;

    }

    private EncodingProfile singleVariantProfile() {

        return new EncodingProfile(
                List.of(new EncodingProfile.Variant(1920, 8000, "libx264", "high@5.2", "slow", "1080p")),
                1920, 1920, 12000);

    }
}
