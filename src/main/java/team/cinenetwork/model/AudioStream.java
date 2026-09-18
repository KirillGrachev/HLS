package team.cinenetwork.model;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import team.cinenetwork.exception.ErrorCode;
import team.cinenetwork.exception.ProcessingException;
import team.cinenetwork.model.probe.ProbeData;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Аудиодорожка исходника: у аниме их обычно несколько (jpn/rus/eng),
 * поэтому дорожка хранит и код языка, и тайтл — тайтл попадает в мастер-плейлист
 * как человекочитаемое имя аудио-рендишена.
 */
@Data
@Slf4j
public class AudioStream implements MediaTrack {

    /** Код языка в скобках тайтла: "... (Ru)", "... [JPN]". */
    private static final Pattern LANGUAGE_IN_TITLE = Pattern.compile("(?:\\(|\\[)(\\w{2,3})(?:\\)|\\])");

    /** Код языка ISO-639; "und", если определить не удалось. */
    private String language = "und";

    /** Тайтл из {@code tags.title} ("Russian dub"). */
    private String title;

    /** Имя кодека (aac, flac, ...). */
    private String codecName;

    /** Индекс дорожки в файле. */
    private int streamIndex;

    /** Число каналов (2 = стерео). */
    private int channelCount;

    /** Частота дискретизации, Гц; 0 — если не распознали. */
    private int samplingRateHz;

    /** Длительность дорожки, секунды. */
    private double durationSeconds;

    /** Прочие поля ffprobe по этой дорожке. */
    private Map<String, Object> metadata = new HashMap<>();

    /* ---------- Jackson ---------- */

    @JsonAnySetter
    public void storeMetadataProperty(String name, Object value) {
        metadata.put(name, value);
    }

    @JsonProperty("sample_rate")
    public void parseSamplingRate(String rawRate) {
        this.samplingRateHz = parseRateSafely(rawRate);
    }

    /* ---------- Заполнение из ffprobe ---------- */

    public void populateFromProbeData(@NotNull Map<String, Object> probeData) {

        ProbeData probe = ProbeData.of(probeData);

        this.streamIndex = probe.integer("index").orElseThrow(() ->
                ProcessingException.of(ErrorCode.MISSING_REQUIRED_FIELD,
                        "Audio stream has no 'index' field"));
        this.codecName = probe.text("codec_name").orElse(null);
        this.channelCount = probe.integer("channels").orElse(2);
        this.durationSeconds = probe.decimal("duration").orElse(0.0);
        this.title = probe.titleTag().orElse(null);
        this.language = resolveLanguage(probe);
        this.samplingRateHz = probe.text("sample_rate")
                .map(this::parseRateSafely)
                .orElse(0);

    }

    /* ---------- Определение языка ---------- */

    /**
     * Порядок определения языка (от надёжного к менее надёжному):
     * {@code tags.language} → код в скобках тайтла "Dub (Ru)" → {@code "und"}.
     */
    private @NotNull String resolveLanguage(@NotNull ProbeData probe) {

        Optional<String> fromTag = probe.languageTag();
        if (fromTag.isPresent()) {
            return fromTag.get().toLowerCase();
        }

        return probe.titleTag()
                .map(this::parseLanguageFromTitle)
                .orElse("und");

    }

    private @NotNull String parseLanguageFromTitle(String title) {

        Matcher matcher = LANGUAGE_IN_TITLE.matcher(title);

        return matcher.find() ? matcher.group(1).toLowerCase() : "und";

    }

    /** Парсит "48000"/"48000 Hz"; мусор не фатален: без rates просто не передадим -ar. */
    private int parseRateSafely(String rawRate) {

        String cleaned = rawRate.replace(" Hz", "").trim();
        if (cleaned.isEmpty()) {
            return 0;
        }

        try {

            return Integer.parseInt(cleaned);

        } catch (NumberFormatException e) {
            log.warn("Invalid sampling rate format: '{}' (raw: '{}')", cleaned, rawRate);
            return 0;
        }

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
