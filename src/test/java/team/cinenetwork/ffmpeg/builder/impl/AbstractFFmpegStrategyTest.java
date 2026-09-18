package team.cinenetwork.ffmpeg.builder.impl;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import team.cinenetwork.exception.ErrorCode;
import team.cinenetwork.exception.ProcessingException;
import team.cinenetwork.model.AudioStream;
import team.cinenetwork.model.VideoInfo;
import team.cinenetwork.model.VideoStream;
import team.cinenetwork.options.AppOptions;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Тесты общих «строительных блоков» команд: парсинг ratio/seek, GOP, профили, аудио-аргументы.
 */
class AbstractFFmpegStrategyTest {

    @TempDir
    Path tempDir;

    /** Минимальная конкретная стратегия: открывает protected-блоки для тестов. */
    private static class TestStrategy extends AbstractFFmpegStrategy {

        TestStrategy(AppOptions options, VideoInfo videoInfo) {
            super(options, videoInfo);
        }

        @Override
        public @NotNull List<String> build() {
            return inputArgs();
        }

        double ratio(String ratio) {
            return parseRatio(ratio);
        }

        String seek(String seek, double duration) {
            return parseSeekPosition(seek, duration);
        }

        int gop() {
            return calculateKeyframeInterval();
        }

        String profile(String value, boolean isProfile) {
            return extractProfileLevel(value, isProfile);
        }

        List<String> audio(AudioStream audio) {
            return audioEncodeArgs(audio);
        }

        Path mkdir(Path path) {
            return ensureDirectory(path);
        }

        int[] resolution(int width) {
            return scaledResolution(width);
        }
    }

    private TestStrategy strategy(VideoInfo videoInfo) {

        AppOptions options = new AppOptions();
        options.setInputOption(Path.of("source.mkv"));
        options.setOutput(tempDir);

        return new TestStrategy(options, videoInfo);

    }

    private VideoInfo video(String frameRate) {

        VideoInfo info = new VideoInfo();
        VideoStream video = new VideoStream();
        video.populateFromProbeData(Map.of(
                "codec_type", "video", "index", 0,
                "width", 1920, "height", 1080, "r_frame_rate", frameRate));
        info.setVideoStream(video);
        return info;

    }

    @Test
    void parseRatioValidAndInvalid() {

        TestStrategy s = strategy(video("24/1"));

        assertEquals(16.0 / 9.0, s.ratio("16:9"), 0.0001);
        assertThrows(ProcessingException.class, () -> s.ratio("16"));
        assertThrows(ProcessingException.class, () -> s.ratio("a:b"));
        assertThrows(ProcessingException.class, () -> s.ratio("0:9"));

    }

    @Test
    void parseSeekPositionPercentAndSeconds() {

        TestStrategy s = strategy(video("24/1"));

        assertEquals("50", s.seek("50%", 100.0));
        assertEquals("120", s.seek("120s", 100.0));
        assertThrows(ProcessingException.class, () -> s.seek("half", 100.0));

    }

    @Test
    void gopIntervalUsesFpsAndHlsTime() {

        TestStrategy s = strategy(video("24000/1001"));  // ≈23.976 fps * 6 sec ≈ 144

        assertEquals(144, s.gop());

    }

    @Test
    void gopWithoutFrameRateThrows() {

        TestStrategy s = strategy(video("unknown"));

        ProcessingException e = assertThrows(ProcessingException.class, s::gop);
        assertEquals(ErrorCode.FRAME_RATE_NOT_FOUND, e.getCode());

    }

    @Test
    void extractProfileLevelWithAndWithoutLevel() {

        TestStrategy s = strategy(video("24/1"));

        assertEquals("high", s.profile("high@5.2", true));
        assertEquals("5.2", s.profile("high@5.2", false));
        assertEquals("main", s.profile("high", false));  // нет уровня → main

    }

    @Test
    void audioEncodeArgsSamplingPriority() {

        AppOptions options = new AppOptions();
        options.setInputOption(Path.of("source.mkv"));
        options.setOutput(tempDir);
        options.setAudioSampling(44100);  // явный rate приоритетнее rate дорожки

        AudioStream audio = new AudioStream();
        audio.populateFromProbeData(Map.of(
                "codec_type", "audio", "index", 1, "sample_rate", "48000"));

        List<String> args = new TestStrategy(options, video("24/1")).audio(audio);
        assertTrue(args.containsAll(List.of("-ar", "44100")));

        // без явного rate берём rate дорожки
        AppOptions noRate = new AppOptions();
        noRate.setInputOption(Path.of("source.mkv"));
        noRate.setOutput(tempDir);
        List<String> fromStream = new TestStrategy(noRate, video("24/1")).audio(audio);
        assertTrue(fromStream.containsAll(List.of("-ar", "48000")));

    }

    @Test
    void ensureDirectoryCreatesAndReportsFailures() {

        TestStrategy s = strategy(video("24/1"));

        Path created = s.mkdir(tempDir.resolve("a/b"));
        assertTrue(Files.isDirectory(created));

        // путь под файлом → IOException → наше исключение
        Path file = tempDir.resolve("file.txt");
        assertTrue(file.toFile().getParentFile().isDirectory());
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> Files.writeString(file, "x"));
        assertThrows(ProcessingException.class, () -> s.mkdir(file.resolve("sub")));

    }

    @Test
    void requireVideoStreamThrowsWhenMissing() {

        TestStrategy s = strategy(new VideoInfo());

        ProcessingException e = assertThrows(ProcessingException.class, () -> s.resolution(1280));
        assertEquals(ErrorCode.NO_VIDEO_STREAM, e.getCode());

    }
}
