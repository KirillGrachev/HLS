package team.cinenetwork.processor.impl.rendition;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import team.cinenetwork.model.AudioStream;
import team.cinenetwork.model.VideoInfo;
import team.cinenetwork.options.AppOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Планирование аудио-рендишенов: какие дорожки исходника станут HLS-потоками
 * и где лежат их папки.
 *
 * <p>Правила фильтрации: задан флаг {@code --audio-languages} — оставляем только
 * перечисленные языки; иначе — все дорожки, найденные в исходнике.
 *
 * <p>Класс используется в трёх местах (координатор команд, генератор, билдер
 * мастер-плейлиста), поэтому состав рендишенов вычисляется одинаково везде —
 * URI в плейлисте всегда совпадают с папками на диске.
 */
@Slf4j
public final class AudioRenditions {

    private AudioRenditions() {
    }

    public static @NotNull List<AudioRendition> resolve(@NotNull AppOptions options,
                                                        @NotNull VideoInfo videoInfo) {

        Set<String> languageFilter = normalizeFilter(options.getAudioLanguages());
        TrackFolderNamer namer = new TrackFolderNamer();
        List<AudioRendition> renditions = new ArrayList<>();

        for (AudioStream stream : videoInfo.getAudioStreams()) {

            if (!languageFilter.isEmpty() && !languageFilter.contains(stream.getLanguage())) {
                log.debug("Skipping audio track {} (language '{}' not in --audio-languages)",
                        stream.getStreamIndex(), stream.getLanguage());
                continue;
            }

            String folderName = namer.nextFolderName(stream.getLanguage());
            renditions.add(new AudioRendition(
                    stream,
                    folderName,
                    options.getOutput().resolve("audio").resolve(folderName)));

        }

        log.debug("Planned audio renditions: {}", renditions.stream()
                .map(AudioRendition::folderName)
                .collect(Collectors.joining(", ")));

        return renditions;

    }

    /** Приводит фильтр к нижнему регистру, чтобы работал и {@code --audio-languages RUS}. */
    private static @NotNull Set<String> normalizeFilter(List<String> languages) {

        return languages == null ? Set.of() : languages.stream()
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

    }
}
