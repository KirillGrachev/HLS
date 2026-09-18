package team.cinenetwork.processor.impl.artifact;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import team.cinenetwork.ffmpeg.FFmpegExecutor;
import team.cinenetwork.model.AudioStream;
import team.cinenetwork.model.SubtitleStream;
import team.cinenetwork.model.VideoInfo;
import team.cinenetwork.model.VideoStream;
import team.cinenetwork.options.AppOptions;
import team.cinenetwork.processor.EncodingProfile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Тесты генераторов артефактов: матрица условий isEnabled() и реальная генерация
 * (в роли ffmpeg выступает /bin/true — команды «выполняются» успешно).
 */
class ArtifactGeneratorsTest {

    @TempDir
    Path tempDir;

    @Test
    void isEnabledMatrixDependsOnOptionsAndSource() {

        // полный исходник + дефолтные опции: включено всё
        var all = generators(options(), fullInfo());
        assertTrue(all.getFirst().isEnabled());  // постер
        assertTrue(all.get(1).isEnabled());  // видео
        assertTrue(all.get(2).isEnabled());  // аудио
        assertTrue(all.get(3).isEnabled());  // субтитры
        assertTrue(all.get(4).isEnabled());  // мастер-плейлист

        // субтитры выключены флажком
        AppOptions noSubs = options();
        noSubs.setSubsEnabled(false);
        assertFalse(generators(noSubs, fullInfo()).get(3).isEnabled());

        // аудио выключено флажком
        AppOptions noAudio = options();
        noAudio.setNoAudio(true);
        assertFalse(generators(noAudio, fullInfo()).get(2).isEnabled());

        // audio-only: постер/видео/аудио-рендишены/субтитлы не нужны
        AppOptions audioOnly = options();
        audioOnly.setAudioOnly(true);
        var only = generators(audioOnly, fullInfo());
        assertFalse(only.getFirst().isEnabled());
        assertFalse(only.get(1).isEnabled());
        assertFalse(only.get(2).isEnabled());
        assertFalse(only.get(3).isEnabled());

        // исходник без субтитров: генератор субтитров молчит
        assertFalse(generators(options(), noSubsInfo()).get(3).isEnabled());

    }

    @Test
    void namesAreHumanReadable() {

        var all = generators(options(), fullInfo());

        assertTrue(all.getFirst().name().contains("preview.jpg"));
        assertTrue(all.get(3).name().contains("WebVTT"));
        assertTrue(all.get(4).name().contains("master.m3u8"));

    }

    @Test
    void generateProducesPlaylistArtifacts() throws IOException {

        AppOptions options = options();
        options.setFfmpeg("/bin/true");  // «ffmpeg» мгновенно успешен

        var all = generators(options, fullInfo());
        for (ArtifactGenerator generator : all) {
            if (generator.isEnabled()) {
                generator.generate();
            }
        }

        // мастер-плейлист и субтитл-плейлисты реально на диске
        assertTrue(Files.exists(tempDir.resolve("master.m3u8")));
        assertTrue(Files.exists(tempDir.resolve("subtitles/rus/playlist.m3u8")));
        assertTrue(Files.readString(tempDir.resolve("master.m3u8")).contains("TYPE=SUBTITLES"));

    }

    @Test
    void subtitleGeneratorWarnsWhenOnlyImageBasedTracks() throws IOException {

        AppOptions options = options();
        options.setFfmpeg("/bin/true");

        VideoInfo info = fullInfo();
        info.getSubtitleStreams().clear();
        SubtitleStream pgs = new SubtitleStream();
        pgs.populateFromProbeData(Map.of(
                "codec_type", "subtitle", "index", 5, "codec_name", "hdmv_pgs_subtitle"));
        info.getSubtitleStreams().add(pgs);

        var gens = generators(options, info);
        assertTrue(gens.get(3).isEnabled());   // дорожка есть...

        gens.get(3).generate();                // ...но конвертируемых нет: warn и выход

        assertFalse(Files.exists(tempDir.resolve("subtitles")));

    }

    @Test
    void playlistGeneratorSkipsEmptyContentInAudioOnly() throws IOException {

        AppOptions options = options();
        options.setAudioOnly(true);
        options.setFfmpeg("/bin/true");

        generators(options, fullInfo()).get(4).generate();

        // в audio-only мастер не строится: контента нет, писатель пропускает пустоту
        assertFalse(Files.exists(tempDir.resolve("master.m3u8")));

    }

    @Test
    void audioGeneratorSurvivesWhenLanguageFilterExcludesAll() throws IOException {

        AppOptions options = options();
        options.setAudioLanguages(List.of("xxx"));  // ни одна дорожка не подходит
        options.setFfmpeg("/bin/true");

        var gens = generators(options, fullInfo());
        assertTrue(gens.get(2).isEnabled());  // дорожки в исходнике есть...

        gens.get(2).generate();               // ...но рендишенов 0: warn и выход без падения

    }

    /* ---------- Фикстуры ---------- */

    private AppOptions options() {

        AppOptions options = new AppOptions();
        options.setInputOption(Path.of("source.mkv"));
        options.setOutput(tempDir);

        return options;

    }

    private List<ArtifactGenerator> generators(AppOptions options, VideoInfo info) {

        FFmpegExecutor executor = new FFmpegExecutor(options, info, profile());

        return ArtifactGenerators.createAll(options, info, profile(), executor);

    }

    private EncodingProfile profile() {

        return new EncodingProfile(List.of(
                new EncodingProfile.Variant(1920, 8000, "libx264", "high@5.2", "slow", "1080p")
        ), 1920, 1920, 12000);

    }

    private VideoInfo fullInfo() {

        VideoInfo info = noSubsInfo();

        AudioStream audio = new AudioStream();
        audio.populateFromProbeData(Map.of(
                "codec_type", "audio", "index", 1, "channels", 2,
                "tags", Map.of("language", "jpn")));
        info.getAudioStreams().add(audio);
        info.setAudioStream(audio);

        SubtitleStream subs = new SubtitleStream();
        subs.populateFromProbeData(Map.of(
                "codec_type", "subtitle", "index", 2, "codec_name", "ass",
                "tags", Map.of("language", "rus")));
        info.getSubtitleStreams().add(subs);

        return info;

    }

    private VideoInfo noSubsInfo() {

        VideoInfo info = new VideoInfo();
        VideoStream video = new VideoStream();
        video.populateFromProbeData(Map.of(
                "codec_type", "video", "index", 0,
                "width", 1920, "height", 1080, "r_frame_rate", "24/1"));
        info.setVideoStream(video);
        return info;

    }
}
