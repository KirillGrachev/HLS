package team.cinenetwork;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Успешный путь точки входа: полный пайплайн на фейковых ffprobe/ffmpeg
 * завершается без System.exit и печатает итоговую строку успеха.
 */
class MainSuccessPathTest {

    /** Минимальный JSON источника: видео + одна озвучка + одни сабы. */
    private static final String PROBE_JSON = """
            {
              "format": {"duration": "120.0"},
              "streams": [
                {"index":0,"codec_type":"video","width":1280,"height":720,"r_frame_rate":"24/1","bit_rate":"5000000"},
                {"index":1,"codec_type":"audio","channels":2,"sample_rate":"48000","tags":{"language":"jpn"}},
                {"index":2,"codec_type":"subtitle","codec_name":"ass","tags":{"language":"rus"}}
              ]
            }
            """;

    @TempDir
    Path tempDir;

    @Test
    void successfulRunDoesNotExit() throws IOException {

        Path bin = tempDir.resolve("bin");
        Files.createDirectories(bin);

        Path ffprobe = bin.resolve("ffprobe");
        Files.writeString(ffprobe, "#!/bin/sh\ncat <<'JSON'\n" + PROBE_JSON + "JSON\n");

        Path ffmpeg = bin.resolve("ffmpeg");
        Files.writeString(ffmpeg, """
                #!/bin/sh
                for a in "$@"; do
                  case "$a" in
                    *.vtt) mkdir -p "$(dirname "$a")"; echo WEBVTT > "$a";;
                  esac
                done
                exit 0
                """);

        assertTrue(ffprobe.toFile().setExecutable(true));
        assertTrue(ffmpeg.toFile().setExecutable(true));

        Path out = tempDir.resolve("out");

        // если main попытается вызвать System.exit — тест упадёт сам (нет ловушки):
        // успешный сценарий обязан пройти насквозь без завершения JVM
        Main.main(new String[]{
                "--input", "source.mkv",
                "--output", out.toString(),
                "--ffprobe", ffprobe.toString(),
                "--ffmpeg", ffmpeg.toString()
        });

        assertTrue(Files.exists(out.resolve("master.m3u8")));

    }
}
