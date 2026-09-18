package team.cinenetwork.processor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import team.cinenetwork.options.AppOptions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end тест пайплайна на фейковых ffprobe/ffmpeg: от CLI-опций до артефактов
 * на диске (мастер-плейлист с аудио- и субтитл-группами, WebVTT-рендишены, remux).
 */
class VideoProcessorE2ETest {

    /** JSON «аниме-источника»: видео, 2 озвучки, ASS-сабы + PGS (должен быть пропущен). */
    private static final String PROBE_JSON = """
            {
              "format": {"duration": "1429.5"},
              "streams": [
                {"index":0,"codec_type":"video","codec_name":"hevc","width":1920,"height":1080,
                 "r_frame_rate":"24000/1001","bit_rate":"8000000","duration":"1429.5"},
                {"index":1,"codec_type":"audio","codec_name":"aac","channels":2,"sample_rate":"48000",
                 "tags":{"language":"jpn","title":"Japanese"}},
                {"index":2,"codec_type":"audio","codec_name":"aac","channels":2,"sample_rate":"48000",
                 "tags":{"language":"rus","title":"Russian dub"}},
                {"index":3,"codec_type":"subtitle","codec_name":"ass","tags":{"language":"rus","title":"Subs (Ru)"}},
                {"index":4,"codec_type":"subtitle","codec_name":"hdmv_pgs_subtitle","tags":{"language":"jpn"}}
              ]
            }
            """;

    @TempDir
    Path tempDir;

    @Test
    void fullHlsPipelineProducesAllArtifacts() throws IOException {

        Path bin = writeFakeBinaries();
        Path out = tempDir.resolve("out");

        VideoProcessor.create(hlsOptions(bin, out)).process();

        // мастер-плейлист: аудио-группа на 2 озвучки + субтитл-группа (PGS пропущен)
        String master = Files.readString(out.resolve("master.m3u8"));
        assertEquals(2, countOccurrences(master, "TYPE=AUDIO"));
        assertEquals(1, countOccurrences(master, "TYPE=SUBTITLES"));
        assertTrue(master.contains("AUDIO=\"audio\""));
        assertTrue(master.contains("SUBTITLES=\"subtitles\""));

        // субтитл-рендишен: vtt от «ffmpeg» + оборачивающий плейлист
        assertTrue(Files.exists(out.resolve("subtitles/rus/subtitles.vtt")));
        assertTrue(Files.readString(out.resolve("subtitles/rus/playlist.m3u8"))
                .contains("subtitles.vtt"));

        // в командах «ffmpeg» реально была конвертация субтитров
        String ffmpegLog = Files.readString(tempDir.resolve("ffmpeg.log"));
        assertTrue(ffmpegLog.contains("-c:s webvtt"));
        assertTrue(ffmpegLog.contains("audio/jpn"));  // аудио-рендишены тоже собраны

    }

    @Test
    void singleFileModeRemuxesEveryAudioTrack() throws IOException {

        Path bin = writeFakeBinaries();
        Path out = tempDir.resolve("out-mp4");

        AppOptions options = hlsOptions(bin, out);
        options.setSingleFile(true);
        options.setAllAudioTracks(true);

        VideoProcessor.create(options).process();

        String ffmpegLog = Files.readString(tempDir.resolve("ffmpeg.log"));
        assertEquals(2, countOccurrences(ffmpegLog, "-metadata:s:a:0"));  // по команде на озвучку

    }

    /* ---------- Фикстуры ---------- */

    private AppOptions hlsOptions(Path bin, Path out) {

        AppOptions options = new AppOptions();
        options.setInputOption(Path.of("source.mkv"));
        options.setOutput(out);
        options.setFfprobe(bin.resolve("ffprobe").toString());
        options.setFfmpeg(bin.resolve("ffmpeg").toString());
        options.setVideoWidths(List.of(1920, 1280));
        options.setVideoBaseBitrates(List.of(8000, 5000));
        options.setVideoNames(List.of("1080p", "720p"));

        return options;

    }

    /** Пишет исполняемые заглушки ffprobe/ffmpeg; ffmpeg логирует аргументы и создаёт .vtt. */
    private Path writeFakeBinaries() throws IOException {

        Path bin = tempDir.resolve("bin");
        Files.createDirectories(bin);
        Path log = tempDir.resolve("ffmpeg.log");

        Path ffprobe = bin.resolve("ffprobe");
        Files.writeString(ffprobe, "#!/bin/sh\ncat <<'JSON'\n" + PROBE_JSON + "JSON\n");

        Path ffmpeg = bin.resolve("ffmpeg");
        Files.writeString(ffmpeg, """
                #!/bin/sh
                echo "$@" >> %s
                for a in "$@"; do
                  case "$a" in
                    *.vtt) mkdir -p "$(dirname "$a")"; echo WEBVTT > "$a";;
                  esac
                done
                exit 0
                """.formatted(log));

        assertTrue(ffprobe.toFile().setExecutable(true));
        assertTrue(ffmpeg.toFile().setExecutable(true));

        return bin;

    }

    private int countOccurrences(String haystack, String needle) {

        int count = 0;
        int index = haystack.indexOf(needle);
        while (index >= 0) {
            count++;
            index = haystack.indexOf(needle, index + needle.length());
        }

        return count;

    }
}
