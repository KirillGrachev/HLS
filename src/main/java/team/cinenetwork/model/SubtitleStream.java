package team.cinenetwork.model;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import team.cinenetwork.exception.ErrorCode;
import team.cinenetwork.exception.ProcessingException;
import team.cinenetwork.model.probe.ProbeData;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Дорожка субтитров исходника — новый участник пайплайна.
 *
 * <p>Раньше HLS «вытягивал всё, кроме субтитров»; теперь каждая текстовая дорожка
 * (SRT/ASS/SSA/VTT) конвертируется FFmpeg в WebVTT и попадает в мастер-плейлист
 * как {@code EXT-X-MEDIA:TYPE=SUBTITLES} — зритель выбирает субтитры в плеере.
 *
 * <p>Image-based форматы (PGS, DVD-sub) в WebVTT не конвертируются — такие дорожки
 * вежливо пропускаются с предупреждением ({@link #isTextBased()}).
 */
@Data
@Slf4j
public class SubtitleStream implements MediaTrack {

    /**
     * Кодеки, которые FFmpeg умеет конвертировать в WebVTT.
     * Всё остальное (hdmv_pgs_subtitle, dvd_subtitle, ...) пропускаем.
     */
    private static final Set<String> WEBVTT_CONVERTIBLE_CODECS = Set.of(
            "subrip", "srt", "ass", "ssa", "webvtt", "mov_text", "text");

    /** Код языка в скобках тайтла: "Subs (Ru)", "Soft subs [ENG]". */
    private static final Pattern LANGUAGE_IN_TITLE = Pattern.compile("(?:\\(|\\[)(\\w{2,3})(?:\\)|\\])");

    /** Код языка ISO-639; "und", если определить не удалось. */
    private String language = "und";

    /** Тайтл из {@code tags.title} ("Subs (Ru)"). */
    private String title;

    /** Имя кодека (ass, subrip, hdmv_pgs_subtitle, ...). */
    private String codecName;

    /** Индекс дорожки в файле. */
    private int streamIndex;

    /** Дорожка помечена дефолтной в исходнике ({@code disposition.default == 1}). */
    private boolean defaultTrack;

    /** Форсированная дорожка ({@code disposition.forced == 1}): надписи на экране и т.п. */
    private boolean forcedTrack;

    /** Прочие поля ffprobe по этой дорожке. */
    private Map<String, Object> metadata = new HashMap<>();

    @JsonAnySetter
    public void storeMetadataProperty(String name, Object value) {
        metadata.put(name, value);
    }

    /* ---------- Заполнение из ffprobe ---------- */

    public void populateFromProbeData(@NotNull Map<String, Object> probeData) {

        ProbeData probe = ProbeData.of(probeData);

        this.streamIndex = probe.integer("index").orElseThrow(() ->
                ProcessingException.of(ErrorCode.MISSING_REQUIRED_FIELD,
                        "Subtitle stream has no 'index' field"));
        this.codecName = probe.text("codec_name").orElse(null);
        this.title = probe.titleTag().orElse(null);
        this.language = resolveLanguage(probe);
        this.defaultTrack = probe.nested("disposition").integer("default").orElse(0) == 1;
        this.forcedTrack = probe.nested("disposition").integer("forced").orElse(0) == 1;

        probe.asMap().forEach((key, value) -> {
            if (value != null) {
                metadata.put(key, value);
            }
        });

    }

    /* ---------- Поведение ---------- */

    /** Можно ли конвертировать дорожку в WebVTT (т.е. пустить в HLS). */
    public boolean isTextBased() {
        return codecName != null && WEBVTT_CONVERTIBLE_CODECS.contains(codecName.toLowerCase());
    }

    /* ---------- Определение языка ---------- */

    /** Тот же порядок, что у аудио: {@code tags.language} → код в скобках тайтла → "und". */
    private @NotNull String resolveLanguage(@NotNull ProbeData probe) {

        return probe.languageTag()
                .map(tag -> tag.toLowerCase())
                .or(() -> probe.titleTag().map(this::parseLanguageFromTitle))
                .orElse("und");

    }

    private @NotNull String parseLanguageFromTitle(String title) {

        Matcher matcher = LANGUAGE_IN_TITLE.matcher(title);

        return matcher.find() ? matcher.group(1).toLowerCase() : "und";

    }

    /* ---------- MediaTrack ---------- */

    @Override
    public int getStreamIndex() {
        return streamIndex;
    }

    @Override
    public @NotNull String getLanguage() {
        return language;
    }

    @Override
    public @Nullable String getTitle() {
        return title;
    }

    @Override
    public @Nullable String getCodecName() {
        return codecName;
    }
}
