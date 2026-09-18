package team.cinenetwork.processor.impl.rendition;

import org.junit.jupiter.api.Test;
import team.cinenetwork.model.SubtitleStream;
import team.cinenetwork.model.VideoInfo;
import team.cinenetwork.options.AppOptions;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Тесты планировщика субтитл-рендишенов: уникальные папки для одинаковых языков,
 * пропуск image-based форматов, фильтрация через --subs-languages.
 */
class SubtitleRenditionsTest {

    @Test
    void assignsUniqueFoldersAndSkipsImageBasedCodecs() {

        AppOptions options = options();
        VideoInfo videoInfo = new VideoInfo();
        videoInfo.getSubtitleStreams().addAll(List.of(
                subtitle(3, "ass", "rus"),
                subtitle(4, "subrip", "rus"),   // вторая русская дорожка -> rus_2
                subtitle(5, "subrip", "eng"),
                subtitle(6, "hdmv_pgs_subtitle", "jpn")  // PGS: в WebVTT не конвертируется
        ));

        List<SubtitleRendition> renditions = SubtitleRenditions.resolve(options, videoInfo);

        assertEquals(List.of("rus", "rus_2", "eng"),
                renditions.stream().map(SubtitleRendition::folderName).toList());
        assertEquals("subtitles/rus/playlist.m3u8", renditions.getFirst().playlistUri());
        assertEquals("subtitles.vtt", renditions.getFirst().vttFile().getFileName().toString());

    }

    @Test
    void filtersByLanguage() {

        AppOptions options = options();
        options.setSubsLanguages(List.of("ENG"));  // регистр не должен влиять

        VideoInfo videoInfo = new VideoInfo();
        videoInfo.getSubtitleStreams().addAll(List.of(
                subtitle(3, "ass", "rus"),
                subtitle(5, "subrip", "eng")));

        List<SubtitleRendition> renditions = SubtitleRenditions.resolve(options, videoInfo);

        assertEquals(1, renditions.size());
        assertEquals("eng", renditions.getFirst().folderName());

    }

    /* ---------- Фикстуры ---------- */

    private AppOptions options() {

        AppOptions options = new AppOptions();
        options.setOutput(Path.of("/tmp/out"));

        return options;

    }

    private SubtitleStream subtitle(int index, String codec, String language) {

        SubtitleStream stream = new SubtitleStream();
        stream.populateFromProbeData(Map.of(
                "codec_type", "subtitle",
                "index", index,
                "codec_name", codec,
                "tags", Map.of("language", language)));

        return stream;

    }
}
