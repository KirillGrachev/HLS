package team.cinenetwork.processor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import team.cinenetwork.exception.ErrorCode;
import team.cinenetwork.exception.ProcessingException;
import team.cinenetwork.options.AppOptions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Покрытие веток процессора: оборачивание неожиданных IO-ошибок, источник без видео,
 * audio-only без аудио и без видео, пропуск выключенных генераторов.
 */
class VideoProcessorBranchesTest {

    /** JSON источника без видеопотока: только аудио. */
    private static final String AUDIO_ONLY_PROBE = """
            {
              "format": {"duration": "120.0"},
              "streams": [
                {"index":0,"codec_type":"audio","channels":2,"sample_rate":"48000","tags":{"language":"jpn"}}
              ]
            }
            """;

    /** JSON источника без аудиодорожек: только видео. */
    private static final String VIDEO_ONLY_PROBE = """
            {
              "format": {"duration": "120.0"},
              "streams": [
                {"index":0,"codec_type":"video","width":1280,"height":720,"r_frame_rate":"24/1"}
              ]
            }
            """;

    /** JSON полноценного источника: видео + аудио. */
    private static final String FULL_PROBE = """
            {
              "format": {"duration": "120.0"},
              "streams": [
                {"index":0,"codec_type":"video","width":1280,"height":720,"r_frame_rate":"24/1"},
                {"index":1,"codec_type":"audio","channels":2,"sample_rate":"48000","tags":{"language":"jpn"}}
              ]
            }
            """;

    @TempDir
    Path tempDir;

    @Test
    void unexpectedIoErrorIsWrappedIntoProcessingException() throws IOException {

        // выходная директория «внутри» файла: Files.createDirectories бросит IOException
        Path file = tempDir.resolve("blocker.txt");
        Files.writeString(file, "x");

        AppOptions options = options(AUDIO_ONLY_PROBE);
        options.setOutput(file.resolve("sub"));

        ProcessingException e = assertThrows(ProcessingException.class,
                () -> VideoProcessor.create(options).process());
        assertEquals(ErrorCode.VIDEO_PROCESSING_FAILED, e.getCode());

    }

    @Test
    void sourceWithoutVideoFailsValidation() throws IOException {

        AppOptions options = options(AUDIO_ONLY_PROBE);

        ProcessingException e = assertThrows(ProcessingException.class,
                () -> VideoProcessor.create(options).process());
        assertEquals(ErrorCode.NO_VIDEO_STREAM, e.getCode());

    }

    @Test
    void audioOnlyWithoutAudioFailsValidation() throws IOException {

        AppOptions options = options(VIDEO_ONLY_PROBE);
        options.setAudioOnly(true);

        ProcessingException e = assertThrows(ProcessingException.class,
                () -> VideoProcessor.create(options).process());
        assertEquals(ErrorCode.AUDIO_ONLY, e.getCode());

    }

    @Test
    void disabledGeneratorsAreSkippedSilently() throws IOException {

        AppOptions options = options(VIDEO_ONLY_PROBE);
        options.setSubsEnabled(false);      // генератор субтитров выключен
        options.setPosterEnabled(false);    // ...и постера: обе ветки isEnabled=false
        options.setFfmpeg("/bin/true");

        VideoProcessor.create(options).process();

        // видеочасть и мастер собрались, постера и субтитров нет
        assertTrue(Files.exists(options.getOutput().resolve("master.m3u8")));
        assertTrue(!Files.exists(options.getOutput().resolve("preview.jpg")));

    }

    @Test
    void audioOnlyWithAudioPassesValidation() throws IOException {

        AppOptions options = options(FULL_PROBE);
        options.setAudioOnly(true);

        // аудио в источнике есть — валидация пропускает запуск; мастер-плейлист
        // в audio-only режиме не строится, а самостоятельный аудио-плейлист пишет
        // FFmpeg — его наличие доказывает VideoProcessorE2ETest
        VideoProcessor.create(options).process();

        assertFalse(Files.exists(options.getOutput().resolve("master.m3u8")));

    }

    @Test
    void audioOnlyWithoutVideoPassesValidation() throws IOException {

        // подкаст/OST: источнику без видеопотока audio-only режим не отказывает —
        // видео для аудио-плейлиста не нужно (регрессия NO_VIDEO_STREAM)
        AppOptions options = options(AUDIO_ONLY_PROBE);
        options.setAudioOnly(true);

        VideoProcessor.create(options).process();

    }

    /**
     * Фикстуры.
     */

    private AppOptions options(String probeJson) throws IOException {

        Path bin = tempDir.resolve("bin-" + Math.abs(probeJson.hashCode()));
        Files.createDirectories(bin);

        Path ffprobe = bin.resolve("ffprobe");
        Files.writeString(ffprobe, "#!/bin/sh\ncat <<'JSON'\n" + probeJson + "JSON\n");
        assertTrue(ffprobe.toFile().setExecutable(true));

        AppOptions options = new AppOptions();
        options.setInputOption(Path.of("source.mkv"));
        options.setOutput(tempDir.resolve("out-" + Math.abs(probeJson.hashCode())));
        options.setFfprobe(ffprobe.toString());
        options.setFfmpeg("/bin/true");
        options.setVideoWidths(java.util.List.of(1280));
        options.setVideoBaseBitrates(java.util.List.of(5000));
        options.setVideoNames(java.util.List.of("720p"));
        return options;

    }
}
