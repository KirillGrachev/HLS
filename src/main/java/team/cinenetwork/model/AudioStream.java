package team.cinenetwork.model;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Data
public class AudioStream {

    private String language;
    private String codecName;

    private int streamIndex;
    private int channelCount;
    private int samplingRateHz;

    private double durationSeconds;

    private Map<String, Object> metadata = new HashMap<>();

    @JsonAnySetter
    public void storeMetadataProperty(String name, Object value) {
        metadata.put(name, value);
    }

    @JsonProperty("sample_rate")
    public void parseSamplingRate(String rawRate) {
        this.samplingRateHz = parseRateSafely(rawRate);
    }

    public void populateFromProbeData(@NotNull Map<String, Object> probeData) {

        this.streamIndex = extractInteger(probeData, "index");
        this.codecName = extractString(probeData, "codec_name");
        this.channelCount = extractInteger(probeData, "channels");
        this.durationSeconds = extractDouble(probeData, "duration");
        this.language = extractLanguage(probeData);

        parseSamplingRate(extractString(probeData, "sample_rate"));

    }

    private Integer parseRateSafely(String rawRate) {
        return Optional.ofNullable(rawRate)
                .map(rate -> rate.replace(" Hz", ""))
                .filter(rate -> !rate.isEmpty())
                .map(rate -> {
                    try {
                        return Integer.parseInt(rate);
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("Invalid sampling rate format: " + rate, e);
                    }
                })
                .orElse(null);
    }

    private @NotNull String parseLanguageFromStreamTitle(String title) {
        Matcher matcher = Pattern.compile(".*\\((\\w{3})\\)").matcher(title);
        return matcher.find() ? matcher.group(1).toLowerCase() : "und";
    }

    private static String extractString(@NotNull Map<String, Object> data, String key) {
        return Optional.ofNullable(data.get(key))
                .map(Object::toString)
                .orElse(null);
    }

    private static int extractInteger(@NotNull Map<String, Object> data, String key) {
        return Optional.ofNullable(data.get(key))
                .map(Number.class::cast)
                .map(Number::intValue)
                .orElseThrow(() -> new IllegalArgumentException("Missing required field: " + key));
    }

    private @NotNull String extractLanguage(@NotNull Map<String, Object> data) {

        String languageTag = Optional.ofNullable(data.get("tags"))
                .filter(Map.class::isInstance)
                .map(tags -> ((Map<?, ?>) tags).get("language"))
                .map(Object::toString)
                .orElse(null);

        if (languageTag != null) return languageTag;

        String streamTitle = Optional.ofNullable(data.get("codec_long_name"))
                .map(Object::toString)
                .orElse("");

        return parseLanguageFromStreamTitle(streamTitle);

    }

    private static double extractDouble(@NotNull Map<String, Object> data, String key) {
        return Optional.ofNullable(data.get(key))
                .map(value -> {
                    if (value instanceof String) {
                        try {
                            return Double.parseDouble((String) value); // Парсим строку
                        } catch (NumberFormatException e) {
                            return 0.0;
                        }
                    } else if (value instanceof Number) {
                        return ((Number) value).doubleValue(); // Берем число
                    }
                    return 0.0; // Неизвестный тип
                })
                .orElse(0.0);
    }
}