package team.cinenetwork.ffmpeg.builder;

import org.junit.jupiter.api.Test;
import team.cinenetwork.model.AudioStream;
import team.cinenetwork.model.VideoInfo;
import team.cinenetwork.model.VideoStream;
import team.cinenetwork.options.AppOptions;
import team.cinenetwork.processor.EncodingProfile;
import team.cinenetwork.processor.impl.rendition.AudioRendition;
import team.cinenetwork.processor.impl.rendition.SubtitleRendition;
import team.cinenetwork.processor.impl.rendition.SubtitleRenditions;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Тесты стратегий сборки команд FFmpeg: проверяем ключевые аргументы каждой команды.
 */
class StrategiesTest {

    private final AppOptions options = options();
    private final VideoInfo videoInfo = videoInfo();
    private final EncodingProfile.Variant variant =
            new EncodingProfile.Variant(1920, 8000, "libx264", "high@5.2", "slow", "1080p");

    @Test
    void hlsVideoStrategyEncodesVideoOnly() {

        List<String> args = new HlsVideoStrategy(options, videoInfo, variant).build();

        assertTrue(args.containsAll(List.of("-y", "-nostdin")));
        assertTrue(args.containsAll(List.of("-c:v", "libx264")));
        assertTrue(args.containsAll(List.of("-b:v", "8000k")));
        assertTrue(args.containsAll(List.of("-profile:v", "high", "-level:v", "5.2")));
        // видео-only: аудио не маппится
        assertTrue(args.stream().noneMatch(a -> a.equals("-c:a")));
        assertTrue(args.contains(String.join("/", "", "tmp", "out", "video", "1080p", "playlist.m3u8")
                .replaceFirst("^/", "/")));

    }

    @Test
    void hlsVideoStrategyOmitsLevelWhenProfileCarriesNone() {

        EncodingProfile.Variant noLevel =
                new EncodingProfile.Variant(1280, 4000, "libx264", "high", "slow", "720p");

        List<String> args = new HlsVideoStrategy(options, videoInfo, noLevel).build();

        assertTrue(args.containsAll(List.of("-profile:v", "high")));
        // без уровня -level:v не передаётся вовсе: раньше сюда попадал "main",
        // а это имя профиля, не уровня — x264 отвергает такую команду
        assertFalse(args.contains("-level:v"));

    }

    @Test
    void audioRenditionStrategyMapsSingleTrack() {

        AudioRendition rendition = new AudioRendition(audio(1, "jpn"), "jpn",
                Path.of("/tmp/out/audio/jpn"));

        List<String> args = new AudioRenditionStrategy(options, videoInfo, rendition).build();

        assertTrue(args.contains("-vn"));
        assertTrue(args.containsAll(List.of("-map", "0:1")));
        assertTrue(args.containsAll(List.of("-c:a", "aac", "-b:a", "128k")));
        assertTrue(args.contains("/tmp/out/audio/jpn/playlist.m3u8"));

    }

    @Test
    void subtitleStrategyConvertsToWebVtt() {

        SubtitleRendition rendition = SubtitleRenditions.resolve(options, videoInfo).getFirst();

        List<String> args = new SubtitleStrategy(options, videoInfo, rendition).build();

        assertTrue(args.containsAll(List.of("-map", "0:3")));
        assertTrue(args.containsAll(List.of("-c:s", "webvtt")));
        assertTrue(args.contains("/tmp/out/subtitles/rus/subtitles.vtt"));

    }

    @Test
    void posterStrategyWritesSinglePreviewFile() {

        List<String> args = new PosterStrategy(options, videoInfo, 1920).build();

        assertTrue(args.containsAll(List.of("-frames:v", "1")));
        assertTrue(args.contains("/tmp/out/preview.jpg"));
        assertTrue(args.contains("-ss"));  // seek из "45%"

    }

    @Test
    void singleFileStrategyRemuxesWithLanguageMetadata() {

        List<String> args = new SingleFileStrategy(options, videoInfo, audio(1, "jpn"), null).build();

        assertTrue(args.containsAll(List.of("-c", "copy")));
        assertTrue(args.containsAll(List.of("-metadata:s:a:0", "language=jpn")));
        assertTrue(args.stream().anyMatch(a -> a.endsWith("_jpn.mp4")));

    }

    @Test
    void audioOnlyStrategyProducesStandalonePlaylist() {

        List<String> args = new AudioOnlyStrategy(options, videoInfo).build();

        assertTrue(args.contains("-vn"));
        assertTrue(args.contains("/tmp/out/playlist.m3u8"));

    }

    @Test
    void singleFileHonorsCustomNameAndDuplicateLanguages() {

        // кастомное базовое имя
        List<String> custom = new SingleFileStrategy(options, videoInfo(), audio(1, "jpn"), "custom").build();
        assertTrue(custom.stream().anyMatch(a -> a.equals("/tmp/out/custom_jpn.mp4")));

        // вторая дорожка того же языка получает суффикс _2
        VideoInfo info = videoInfo();
        info.getAudioStreams().add(audio(2, "jpn"));
        List<String> second = new SingleFileStrategy(options, info,
                info.getAudioStreams().get(1), null).build();
        assertTrue(second.stream().anyMatch(a -> a.endsWith("_jpn_2.mp4")));

    }

    @Test
    void singleFileInputWithoutExtensionKeepsWholeName() {

        AppOptions options = options();
        options.setInputOption(Path.of("source_no_ext"));

        List<String> args = new SingleFileStrategy(options, videoInfo(), audio(1, "jpn"), null).build();

        assertTrue(args.stream().anyMatch(a -> a.equals("/tmp/out/source_no_ext_jpn.mp4")));

    }

    @Test
    void audioOnlyWithoutAudioThrows() {

        VideoInfo info = new VideoInfo();
        VideoStream video = new VideoStream();
        video.populateFromProbeData(Map.of(
                "codec_type", "video", "index", 0, "width", 1920, "height", 1080, "r_frame_rate", "24/1"));
        info.setVideoStream(video);   // аудио нет вовсе

        org.junit.jupiter.api.Assertions.assertThrows(
                team.cinenetwork.exception.ProcessingException.class,
                () -> new AudioOnlyStrategy(options, info).build());

    }

    @Test
    void posterWithZeroWidthThrows() {

        org.junit.jupiter.api.Assertions.assertThrows(
                team.cinenetwork.exception.ProcessingException.class,
                () -> new PosterStrategy(options, videoInfo(), 0).build());

    }

    @Test
    void singleFileBlankCustomNameFallsBackToInputName() {

        // пустое кастомное имя — как если бы его не передали: база из имени исходника
        List<String> args = new SingleFileStrategy(options, videoInfo(), audio(1, "jpn"), "   ").build();

        assertTrue(args.stream().anyMatch(a -> a.equals("/tmp/out/source_jpn.mp4")));

    }

    @Test
    void singleFileMissingLanguagesBecomeUndAndDeduplicate() {

        VideoInfo info = videoInfo();
        info.getAudioStreams().clear();

        AudioStream noTag = new AudioStream();
        noTag.populateFromProbeData(Map.of(
                "codec_type", "audio", "index", 1, "channels", 2, "sample_rate", "48000"));

        AudioStream blankTag = new AudioStream();
        blankTag.populateFromProbeData(Map.of(
                "codec_type", "audio", "index", 2, "channels", 2, "sample_rate", "48000",
                "tags", Map.of("language", "  ")));

        info.getAudioStreams().add(noTag);
        info.getAudioStreams().add(blankTag);

        // нет тега языка и пустой тег приводятся к "und", вторая дорожка получает _2
        List<String> first = new SingleFileStrategy(options, info, noTag, null).build();
        assertTrue(first.stream().anyMatch(a -> a.endsWith("_und.mp4")));

        List<String> second = new SingleFileStrategy(options, info, blankTag, null).build();
        assertTrue(second.stream().anyMatch(a -> a.endsWith("_und_2.mp4")));

    }

    @Test
    void singleFileWithTrackOutsideSourceListGetsPlainName() {

        VideoInfo info = videoInfo();
        info.getAudioStreams().clear();  // целевая дорожка в списке источника отсутствует

        List<String> args = new SingleFileStrategy(options, info, audio(1, "jpn"), null).build();

        // предшествующих одноязычных дорожек нет — суффикс _2 не появляется
        assertTrue(args.stream().anyMatch(a -> a.endsWith("_jpn.mp4")));

    }

    /* ---------- Фикстуры ---------- */

    private AppOptions options() {

        AppOptions options = new AppOptions();
        options.setInputOption(Path.of("source.mkv"));
        options.setOutput(Path.of("/tmp/out"));

        return options;

    }

    private VideoInfo videoInfo() {

        VideoInfo info = new VideoInfo();

        VideoStream video = new VideoStream();
        video.populateFromProbeData(Map.of(
                "codec_type", "video", "index", 0,
                "width", 1920, "height", 1080,
                "r_frame_rate", "24000/1001", "bit_rate", "8000000",
                "duration", "1429.5"));
        info.setVideoStream(video);
        info.setFormatDetails(Map.of("duration", "1429.5"));

        info.getAudioStreams().add(audio(1, "jpn"));
        info.setAudioStream(info.getAudioStreams().getFirst());

        team.cinenetwork.model.SubtitleStream subs = new team.cinenetwork.model.SubtitleStream();
        subs.populateFromProbeData(Map.of(
                "codec_type", "subtitle", "index", 3, "codec_name", "ass",
                "tags", Map.of("language", "rus")));
        info.getSubtitleStreams().add(subs);

        return info;

    }

    private AudioStream audio(int index, String language) {

        AudioStream audio = new AudioStream();
        audio.populateFromProbeData(Map.of(
                "codec_type", "audio", "index", index, "channels", 2, "sample_rate", "48000",
                "tags", Map.of("language", language)));
        return audio;

    }
}
