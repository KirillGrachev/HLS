package team.cinenetwork.ffmpeg;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import team.cinenetwork.ffmpeg.type.ProcessingType;
import team.cinenetwork.model.AudioStream;
import team.cinenetwork.model.VideoInfo;
import team.cinenetwork.model.VideoStream;
import team.cinenetwork.options.AppOptions;
import team.cinenetwork.processor.EncodingProfile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Тест исполнителя FFmpeg: пустой набор команд этапа — это warn, а не падение.
 */
class FFmpegExecutorTest {

    @TempDir
    Path tempDir;

    @Test
    void emptyCommandSetIsWarnNotFailure() {

        AppOptions options = new AppOptions();
        options.setInputOption(Path.of("source.mkv"));
        options.setOutput(tempDir);
        options.setAudioOnly(true);   // в audio-only постер не строится
        options.setFfmpeg("/bin/true");

        FFmpegExecutor executor = new FFmpegExecutor(options, info(), profile());

        // команд для POSTER нет: исполнитель обязан просто залогировать warn
        assertDoesNotThrow(() -> executor.executeMediaProcessing(ProcessingType.POSTER));

    }

    /* ---------- Фикстуры ---------- */

    private VideoInfo info() {

        VideoInfo info = new VideoInfo();
        VideoStream video = new VideoStream();
        video.populateFromProbeData(Map.of(
                "codec_type", "video", "index", 0,
                "width", 1920, "height", 1080, "r_frame_rate", "24/1"));
        info.setVideoStream(video);

        AudioStream audio = new AudioStream();
        audio.populateFromProbeData(Map.of(
                "codec_type", "audio", "index", 1, "channels", 2,
                "tags", Map.of("language", "jpn")));
        info.getAudioStreams().add(audio);
        info.setAudioStream(audio);

        return info;

    }

    private EncodingProfile profile() {

        return new EncodingProfile(List.of(
                new EncodingProfile.Variant(1920, 8000, "libx264", "high@5.2", "slow", "1080p")
        ), 1920, 1920, 12000);

    }
}
