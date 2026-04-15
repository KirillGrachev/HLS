package team.cinenetwork.model;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Data
@Slf4j
public class VideoStream {

    private String codecName;

    private int streamIndex;
    private int frameWidth;
    private int frameHeight;

    private String frameRateRaw;

    private double durationSeconds;
    private double pixelAspectRatio = 1.0;

    private Map<String, Object> metadata = new HashMap<>();

    @JsonProperty("r_frame_rate")
    public void parseFrameRate(String rawFrameRate) {
        this.frameRateRaw = String.valueOf(parseFrameRateSafely(rawFrameRate)
                .orElse(null));
    }

    @JsonProperty("sample_aspect_ratio")
    public void parseAspectRatio(String rawAspectRatio) {
        this.pixelAspectRatio = parseAspectRatioSafely(rawAspectRatio)
                .orElse(1.0);
    }

    @JsonAnySetter
    public void addMetadataProperty(String name, Object value) {
        metadata.put(name, value);
    }

    public void populateFromProbeData(@NotNull Map<String, Object> probeData) {

        if (!isVideoStream(probeData)) {
            log.info("Skipping non-video stream data");
            return;
        }

        log.debug("Processing video stream data: {}", probeData);

        probeData.forEach((key, value) -> {
            if (value != null) {
                metadata.put(key, value);
            }
        });

        extractCoreProperties(probeData);
        parseDurationFromProbeData(probeData);
    }

    public Optional<Double> getCalculatedFrameRate() {

        if (this.frameRateRaw == null || this.frameRateRaw.isEmpty()) {
            return Optional.empty();
        }

        try {

            String[] parts = frameRateRaw.split("/");

            double numerator = Double.parseDouble(parts[0]);
            double denominator = parts.length > 1 ? Double.parseDouble(parts[1]) : 1.0;

            return denominator != 0 ? Optional.of(numerator / denominator)
                    : Optional.empty();

        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            return Optional.empty();
        }
    }

    public int getBitrateKbps() {

        if (metadata.containsKey("bit_rate")) {
            Optional<Long> streamBitrate = parseBitrate(metadata.get("bit_rate"));
            if (streamBitrate.isPresent()) {
                return (int) (streamBitrate.get() / 1000);
            }
        }

        Optional<Map<?, ?>> tags = Optional.ofNullable(metadata.get("tags"))
                .filter(Map.class::isInstance)
                .map(t -> (Map<?, ?>) t);

        if (tags.isPresent()) {

            Optional<Long> tagBitrate = tags.get().entrySet().stream()
                    .filter(entry -> {
                        String key = String.valueOf(entry.getKey()).toLowerCase();
                        return key.contains("bps") || key.contains("bitrate");
                    })
                    .findFirst()
                    .flatMap(entry -> parseBitrate(entry.getValue()));

            if (tagBitrate.isPresent()) {
                return (int) (tagBitrate.get() / 1000);
            }
        }

        log.warn("Bitrate not found in metadata. Available keys: {}",
                metadata.keySet());

        return 0;

    }

    private void extractCoreProperties(Map<String, Object> data) {

        this.streamIndex = extractInteger(data, "index");
        this.codecName = extractString(data, "codec_name");
        this.frameWidth = extractInteger(data, "width");
        this.frameHeight = extractInteger(data, "height");

        parseFrameRate(extractString(data, "r_frame_rate"));
        parseAspectRatio(extractString(data, "sample_aspect_ratio"));

    }

    private static String extractString(@NotNull Map<String, Object> data, String key) {
        return Optional.ofNullable(data.get(key))
                .map(Object::toString)
                .orElse(null);
    }

    private static int extractInteger(@NotNull Map<String, Object> data, String key) {
        return Optional.ofNullable(data.get(key))
                .map(value -> {
                    if (value instanceof String) {
                        try {
                            return Integer.parseInt((String) value);
                        } catch (NumberFormatException e) {
                            throw new IllegalArgumentException("Invalid integer: " + value);
                        }
                    } else if (value instanceof Number) {
                        return ((Number) value).intValue();
                    }
                    throw new IllegalArgumentException("Unsupported type for field: " + key);
                })
                .orElseThrow(() -> new IllegalArgumentException("Missing field: " + key));
    }

    private Optional<Long> parseBitrate(Object rawBitrate) {

        if (rawBitrate == null)
            return Optional.empty();

        try {

            long bitrate = rawBitrate instanceof Number
                    ? ((Number) rawBitrate).longValue()
                    : Long.parseLong(rawBitrate.toString().trim());

            return Optional.of(bitrate);

        } catch (NumberFormatException e) {
            log.warn("Invalid bitrate value: {}", rawBitrate);
            return Optional.empty();
        }
    }

    private void parseDurationFromProbeData(@NotNull Map<String, Object> data) {
        Optional.ofNullable(data.get("duration"))
                .ifPresentOrElse(
                        this::processDuration,
                        () -> parseDurationFromTags(data)
                );
    }

    private void parseDurationFromTags(@NotNull Map<String, Object> data) {
        Optional.ofNullable(data.get("tags"))
                .filter(Map.class::isInstance)
                .map(tags -> (Map<?, ?>) tags)
                .ifPresent(tags -> {
                    tags.entrySet().stream()
                            .filter(entry -> {
                                Object key = entry.getKey();
                                return key != null &&
                                        key.toString().toLowerCase().contains("duration");
                            })
                            .forEach(entry ->
                                    log.debug("Found duration tag: {} = {}", entry.getKey(), entry.getValue()));

                    tags.entrySet().stream()
                            .filter(entry -> {
                                Object key = entry.getKey();
                                Object value = entry.getValue();
                                return key != null &&
                                        key.toString().toLowerCase().contains("duration") &&
                                        value != null &&
                                        !value.toString().isEmpty();
                            })
                            .findFirst()
                            .ifPresentOrElse(
                                    entry -> processDuration(entry.getValue()),
                                    () -> log.warn("No valid duration tags found in: {}", tags.keySet())
                            );
                });
    }

    private void processDuration(Object rawDuration) {
        try {
            this.durationSeconds = parseDuration(rawDuration);
        } catch (IllegalArgumentException e) {
            log.warn("Failed to parse duration: {}", e.getMessage());
        }
    }

    private Optional<Double> parseFrameRateSafely(String rawRate) {
        return Optional.ofNullable(rawRate)
                .map(rate -> rate.split("/"))
                .filter(parts -> parts.length == 2)
                .flatMap(parts -> parseFraction(parts[0], parts[1]));
    }

    private Optional<Double> parseAspectRatioSafely(String rawRatio) {
        return Optional.ofNullable(rawRatio)
                .map(ratio -> ratio.split(":"))
                .filter(parts -> parts.length == 2)
                .flatMap(parts -> parseFraction(parts[0], parts[1]));
    }

    private Optional<Double> parseFraction(String numerator, String denominator) {

        try {

            double num = Double.parseDouble(numerator);
            double den = Double.parseDouble(denominator);

            return den != 0 ? Optional.of(num / den) : Optional.empty();

        } catch (NumberFormatException e) {

            log.debug("Invalid fraction format: {}/{}", numerator, denominator);
            return Optional.empty();

        }
    }

    private double parseDuration(Object rawDuration) {

        if (rawDuration instanceof Number) {
            return ((Number) rawDuration).doubleValue();
        }

        return Optional.ofNullable(rawDuration)
                .map(Object::toString)
                .map(this::normalizeDurationString)
                .flatMap(this::parseDurationString)
                .orElse(0.0);

    }

    private Optional<Double> parseDurationString(String duration) {

        try {
            return Optional.of(parseComplexDuration(duration));
        } catch (NumberFormatException e) {

            log.warn("Invalid duration format: {}", duration);
            return Optional.empty();

        }

    }

    private double parseComplexDuration(@NotNull String duration) {

        String[] parts = duration.split("[:.]");
        if (parts.length < 3) {
            return Double.parseDouble(duration);
        }

        double hours = Double.parseDouble(parts[0]);
        double minutes = Double.parseDouble(parts[1]);
        double seconds = Double.parseDouble(parts[2]);

        return hours * 3600 + minutes * 60 + seconds;

    }

    private static boolean isVideoStream(@NotNull Map<String, Object> data) {
        return Optional.ofNullable(data.get("codec_type"))
                .or(() -> Optional.ofNullable(data.get("codecType")))
                .map(Object::toString)
                .map(String::toLowerCase)
                .filter(type -> type.contains("video"))
                .isPresent();
    }

    private @NotNull String normalizeDurationString(@NotNull String duration) {
        return duration.replace(',', '.').replace("N/A",
                "").trim();
    }
}