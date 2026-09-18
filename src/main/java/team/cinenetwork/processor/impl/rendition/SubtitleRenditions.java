package team.cinenetwork.processor.impl.rendition;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import team.cinenetwork.model.SubtitleStream;
import team.cinenetwork.model.VideoInfo;
import team.cinenetwork.options.AppOptions;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Планирование субтитл-рендишенов: какие дорожки конвертируем в WebVTT
 * и какие файлы создаём под каждую.
 *
 * <p>Правила фильтрации: image-based форматы (PGS, DVD-sub) пропускаем с предупреждением —
 * FFmpeg не конвертирует их в WebVTT; задан флаг {@code --subs-languages} — оставляем только
 * перечисленные языки; иначе — все текстовые дорожки.
 *
 * <p>Раскладка вывода на дорожку:
 * <pre>
 * &lt;out&gt;/subtitles/rus/subtitles.vtt      — WebVTT, сконвертированный FFmpeg
 * &lt;out&gt;/subtitles/rus/playlist.m3u8      — HLS-плейлист, оборачивающий vtt
 * </pre>
 */
@Slf4j
public final class SubtitleRenditions {

    private SubtitleRenditions() {
    }

    public static @NotNull List<SubtitleRendition> resolve(@NotNull AppOptions options,
                                                           @NotNull VideoInfo videoInfo) {

        Set<String> languageFilter = normalizeFilter(options.getSubsLanguages());
        TrackFolderNamer namer = new TrackFolderNamer();
        List<SubtitleRendition> renditions = new ArrayList<>();

        for (SubtitleStream stream : videoInfo.getSubtitleStreams()) {

            if (!stream.isTextBased()) {
                log.warn("Skipping subtitle track {} (codec '{}'): image-based formats "
                                + "cannot be converted to WebVTT",
                        stream.getStreamIndex(), stream.getCodecName());
                continue;
            }

            if (!languageFilter.isEmpty() && !languageFilter.contains(stream.getLanguage())) {
                log.debug("Skipping subtitle track {} (language '{}' not in --subs-languages)",
                        stream.getStreamIndex(), stream.getLanguage());
                continue;
            }

            String folderName = namer.nextFolderName(stream.getLanguage());
            Path directory = options.getOutput().resolve("subtitles").resolve(folderName);

            renditions.add(new SubtitleRendition(
                    stream,
                    folderName,
                    directory,
                    directory.resolve("subtitles.vtt"),
                    directory.resolve("playlist.m3u8")));

        }

        log.debug("Planned subtitle renditions: {}", renditions.stream()
                .map(SubtitleRendition::folderName)
                .collect(Collectors.joining(", ")));

        return renditions;

    }

    private static @NotNull Set<String> normalizeFilter(List<String> languages) {

        return languages == null ? Set.of() : languages.stream()
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

    }
}
