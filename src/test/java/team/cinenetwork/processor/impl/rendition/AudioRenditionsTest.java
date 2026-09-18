package team.cinenetwork.processor.impl.rendition;

import org.junit.jupiter.api.Test;
import team.cinenetwork.model.AudioStream;
import team.cinenetwork.model.VideoInfo;
import team.cinenetwork.options.AppOptions;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Тесты планировщика аудио-рендишенов и неймера папок.
 */
class AudioRenditionsTest {

    @Test
    void uniqueFoldersForDuplicateLanguages() {

        VideoInfo info = infoWithAudio("rus", "rus", "jpn");

        List<AudioRendition> renditions = AudioRenditions.resolve(options(), info);

        assertEquals(List.of("rus", "rus_2", "jpn"),
                renditions.stream().map(AudioRendition::folderName).toList());
        assertEquals("audio/jpn/playlist.m3u8", renditions.get(2).playlistUri());

    }

    @Test
    void languageFilterIsCaseInsensitive() {

        AppOptions options = options();
        options.setAudioLanguages(List.of("JPN"));

        List<AudioRendition> renditions = AudioRenditions.resolve(options, infoWithAudio("rus", "jpn"));

        assertEquals(1, renditions.size());
        assertEquals("jpn", renditions.getFirst().folderName());

    }

    @Test
    void namerSanitizesUnsafeLanguageCodes() {

        // каждый кейс — на свежем неймере: повторы базы дают суффикс _2, _3, ...
        assertEquals("und", new TrackFolderNamer().nextFolderName(null));
        assertEquals("und", new TrackFolderNamer().nextFolderName("  "));
        assertEquals("ru_x", new TrackFolderNamer().nextFolderName("RU X"));

        // повтор базы на одном неймере → уникальный суффикс
        TrackFolderNamer namer = new TrackFolderNamer();
        assertEquals("und", namer.nextFolderName(null));
        assertEquals("und_2", namer.nextFolderName("  "));

    }

    /* ---------- Фикстуры ---------- */

    private AppOptions options() {

        AppOptions options = new AppOptions();
        options.setOutput(Path.of("/tmp/out"));

        return options;

    }

    private VideoInfo infoWithAudio(String... languages) {

        VideoInfo info = new VideoInfo();
        int index = 1;

        for (String language : languages) {
            AudioStream audio = new AudioStream();
            audio.populateFromProbeData(Map.of(
                    "codec_type", "audio", "index", index++, "channels", 2,
                    "tags", Map.of("language", language)));
            info.getAudioStreams().add(audio);
        }

        return info;

    }
}
