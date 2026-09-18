package team.cinenetwork.ffmpeg;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Тесты координатора: сколько команд даёт каждый режим обработки.
 */
class FFmpegCommandCoordinatorTest {

    @Test
    void defaultModeBuildsVideoAudioAndSubtitleCommands() {

        FFmpegCommandCoordinator coordinator = coordinator(defaultOptions());

        assertEquals(2, coordinator.buildTranscodeCommands().size());   // 2 ступени лестницы
        assertEquals(2, coordinator.buildAudioRenditionCommands().size());  // 2 озвучки
        assertEquals(1, coordinator.buildSubtitleCommands().size());    // 1 текстовая дорожка (PGS отброшен)
        assertEquals(1, coordinator.buildPosterCommands().size());
        assertTrue(coordinator.buildSingleFileCommands().isEmpty());

    }

    @Test
    void audioFlagsDisableAudioRenditions() {

        AppOptions options = defaultOptions();
        options.setNoAudio(true);

        FFmpegCommandCoordinator coordinator = coordinator(options);

        assertTrue(coordinator.buildAudioRenditionCommands().isEmpty());
        assertEquals(2, coordinator.buildTranscodeCommands().size());  // видео остаётся

    }

    @Test
    void subsFlagDisablesSubtitleCommands() {

        AppOptions options = defaultOptions();
        options.setSubsEnabled(false);

        assertTrue(coordinator(options).buildSubtitleCommands().isEmpty());

    }

    @Test
    void singleFileModePerAudioTrack() {

        AppOptions options = defaultOptions();
        options.setSingleFile(true);
        options.setAllAudioTracks(true);

        FFmpegCommandCoordinator coordinator = coordinator(options);

        assertEquals(2, coordinator.buildSingleFileCommands().size());
        assertTrue(coordinator.buildTranscodeCommands().isEmpty()
                || coordinator.buildTranscodeCommands().size() == 2);  // транскод не вызывается в этом режиме

    }

    @Test
    void audioOnlyModeGivesSingleCommand() {

        AppOptions options = defaultOptions();
        options.setAudioOnly(true);

        FFmpegCommandCoordinator coordinator = coordinator(options);

        assertEquals(1, coordinator.buildTranscodeCommands().size());
        assertTrue(coordinator.buildAudioRenditionCommands().isEmpty());
        assertTrue(coordinator.buildPosterCommands().isEmpty());

    }

    /* ---------- Фикстуры ---------- */

    private AppOptions defaultOptions() {

        AppOptions options = new AppOptions();
        options.setInputOption(Path.of("source.mkv"));
        options.setOutput(Path.of("/tmp/out"));
        options.setVideoWidths(List.of(1920, 1280));
        options.setVideoBaseBitrates(List.of(8000, 5000));
        options.setVideoNames(List.of("1080p", "720p"));

        return options;

    }

    private FFmpegCommandCoordinator coordinator(AppOptions options) {
        return new FFmpegCommandCoordinator(options, videoInfo(), profile());
    }

    private EncodingProfile profile() {

        return new EncodingProfile(List.of(
                new EncodingProfile.Variant(1920, 8000, "libx264", "high@5.2", "slow", "1080p"),
                new EncodingProfile.Variant(1280, 5000, "libx264", "high@5.1", "slow", "720p")
        ), 1920, 1920, 12000);

    }

    private VideoInfo videoInfo() {

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

        SubtitleStream pgs = new SubtitleStream();
        pgs.populateFromProbeData(Map.of(
                "codec_type", "subtitle", "index", 4, "codec_name", "hdmv_pgs_subtitle"));
        info.getSubtitleStreams().add(pgs);

        return info;

    }
}
