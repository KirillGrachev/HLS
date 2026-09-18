package team.cinenetwork.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Общий контракт всех не-видео дорожек (аудио и субтитры).
 *
 * <p>Появился при рефакторинге: у аудио- и субтитл-дорожек совпадают четыре свойства
 * (индекс, язык, тайтл, кодек), а плейлисты и логика «имя папки на дорожку» работают
 * с ними единообразно. Новый тип дорожки подключается без правки генераторов.
 */
public interface MediaTrack {

    /** Индекс дорожки в исходнике (для {@code ffmpeg -map 0:N}). */
    int getStreamIndex();

    /** Код языка ISO-639 ("jpn", "rus"); {@code "und"}, если неизвестен. */
    @NotNull String getLanguage();

    /** Человекочитаемый тайтл из метаданных ({@code tags.title}); может отсутствовать. */
    @Nullable String getTitle();

    /** Имя кодека FFmpeg: {@code aac}, {@code ass}, {@code subrip}, ... */
    @Nullable String getCodecName();

    /** Имя для плейлистов и логов: тайтл, если есть, иначе код языка. */
    default @NotNull String getDisplayName() {

        String title = getTitle();

        return title != null && !title.isBlank() ? title : getLanguage();

    }
}
