package team.cinenetwork.processor.impl.rendition;

import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * Выдаёт каждой дорожке уникальное имя папки по её языку.
 *
 * <p>У аниме нередко две дорожки одного языка (две русских озвучки): неймер гарантирует
 * уникальные папки {@code rus}, {@code rus_2}, {@code jpn}, ... Те же имена используются
 * в URI мастер-плейлиста, поэтому класс — единственный источник правды «дорожка → папка».
 */
public final class TrackFolderNamer {

    private final Map<String, Integer> usedCounts = new HashMap<>();

    /**
     * Следующее свободное имя папки для языка; вызов с состоянием: каждый новый вызов
     * с тем же языком даёт новый суффикс.
     */
    public @NotNull String nextFolderName(String language) {

        String base = sanitize(language);
        int occurrence = usedCounts.merge(base, 1, Integer::sum);

        return occurrence == 1 ? base : base + "_" + occurrence;

    }

    /** Нижний регистр, только «безопасные» для путей и URI символы. */
    private @NotNull String sanitize(String language) {

        if (language == null || language.isBlank()) {
            return "und";
        }

        String safe = language.toLowerCase().replaceAll("[^a-z0-9_-]", "_");

        return safe.isEmpty() ? "und" : safe;

    }
}
