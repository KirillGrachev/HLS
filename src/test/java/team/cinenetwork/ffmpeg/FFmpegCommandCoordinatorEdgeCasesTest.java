package team.cinenetwork.ffmpeg;

import org.junit.jupiter.api.Test;
import team.cinenetwork.exception.ErrorCode;
import team.cinenetwork.exception.ProcessingException;
import team.cinenetwork.model.AudioStream;
import team.cinenetwork.model.VideoInfo;
import team.cinenetwork.model.VideoStream;
import team.cinenetwork.options.AppOptions;
import team.cinenetwork.processor.EncodingProfile;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Крайние случаи координатора: несуществующие дорожки, пустой исходник, отключённое аудио.
 */
class FFmpegCommandCoordinatorEdgeCasesTest {

    @Test
    void singleFileWithMissingSelectedTrackThrows() {

        AppOptions options = options();
        options.setSingleFile(true);
        options.setAudioStream("index:99");

        ProcessingException e = assertThrows(ProcessingException.class,
                () -> coordinator(options, fullInfo()).buildSingleFileCommands());
        assertEquals(ErrorCode.AUDIO_STREAM_NOT_FOUND, e.getCode());

    }

    @Test
    void singleFileWithoutAnyAudioThrows() {

        AppOptions options = options();
        options.setSingleFile(true);

        ProcessingException e = assertThrows(ProcessingException.class,
                () -> coordinator(options, videoOnlyInfo()).buildSingleFileCommands());
        assertEquals(ErrorCode.AUDIO_STREAM_NOT_FOUND, e.getCode());

    }

    @Test
    void allAudioTracksModeWithoutTracksIsEmpty() {

        AppOptions options = options();
        options.setSingleFile(true);
        options.setAllAudioTracks(true);

        assertTrue(coordinator(options, videoOnlyInfo()).buildSingleFileCommands().isEmpty());

    }

    @Test
    void singleFileUsesSelectedTrack() {

        AppOptions options = options();
        options.setSingleFile(true);
        options.setAudioStream("index:2");

        List<List<String>> commands = coordinator(options, fullInfo()).buildSingleFileCommands();

        assertEquals(1, commands.size());
        assertTrue(commands.getFirst().contains("0:2"));  // замаппили именно вторую дорожку

    }

    @Test
    void posterWithoutVideoStreamIsEmpty() {

        assertTrue(coordinator(options(), videoOnlyInfoWithoutVideo()).buildPosterCommands().isEmpty());

    }

    @Test
    void audioDisableFlagEmptiesAudioRenditions() {

        AppOptions options = options();
        options.setAudioDisable(true);

        assertTrue(coordinator(options, fullInfo()).buildAudioRenditionCommands().isEmpty());

    }

    /* ---------- Фикстуры ---------- */

    private AppOptions options() {

        AppOptions options = new AppOptions();
        options.setInputOption(Path.of("source.mkv"));
        options.setOutput(Path.of("/tmp/out"));

        return options;

    }

    private FFmpegCommandCoordinator coordinator(AppOptions options, VideoInfo info) {
        return new FFmpegCommandCoordinator(options, info, profile());
    }

    private EncodingProfile profile() {

        return new EncodingProfile(List.of(
                new EncodingProfile.Variant(1920, 8000, "libx264", "high@5.2", "slow", "1080p")
        ), 1920, 1920, 12000);

    }

    private VideoInfo fullInfo() {

        VideoInfo info = videoOnlyInfo();
        for (int i = 1; i <= 2; i++) {
            AudioStream audio = new AudioStream();
            audio.populateFromProbeData(Map.of(
                    "codec_type", "audio", "index", i, "channels", 2,
                    "tags", Map.of("language", i == 1 ? "jpn" : "rus")));
            info.getAudioStreams().add(audio);
        }
        info.setAudioStream(info.getAudioStreams().getFirst());
        return info;

    }

    private VideoInfo videoOnlyInfo() {

        VideoInfo info = new VideoInfo();
        VideoStream video = new VideoStream();
        video.populateFromProbeData(Map.of(
                "codec_type", "video", "index", 0, "width", 1920, "height", 1080,
                "r_frame_rate", "24/1"));
        info.setVideoStream(video);
        return info;

    }

    private VideoInfo videoOnlyInfoWithoutVideo() {
        return new VideoInfo();
    }
}
