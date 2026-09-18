package team.cinenetwork.model;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import team.cinenetwork.model.probe.ProbeData;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Видеопоток исходника: геометрия, частота кадров, битрейт, длительность.
 */
@Data
@Slf4j
public class VideoStream {

    /** Имя кодека (h264, hevc, ...). */
    private String codecName;

    /** Индекс потока в файле (для {@code -map 0:N}). */
    private int streamIndex;

    /** Ширина кадра источника, px. */
    private int frameWidth;

    /** Высота кадра источника, px. */
    private int frameHeight;

    /** Сырое значение {@code r_frame_rate}, например "24000/1001". */
    private String frameRateRaw;

    /** Длительность потока, секунды. */
    private double durationSeconds;

    /** Пиксельный аспект (SAR): у анаморфотных источников не 1.0. */
    private double pixelAspectRatio = 1.0;

    /** Всё, что ffprobe сказал о потоке, но что мы не разобрали в поля. */
    private Map<String, Object> metadata = new HashMap<>();

    /* ---------- Jackson ---------- */

    @JsonProperty("r_frame_rate")
    public void parseFrameRate(String rawFrameRate) {
        this.frameRateRaw = rawFrameRate;
    }

    @JsonProperty("sample_aspect_ratio")
    public void parseAspectRatio(String rawAspectRatio) {
        this.pixelAspectRatio = parseFraction(rawAspectRatio).orElse(1.0);
    }

    @JsonAnySetter
    public void addMetadataProperty(String name, Object value) {
        metadata.put(name, value);
    }

    /* ---------- Заполнение из ffprobe ---------- */

    /**
     * Заполняет поток из сырой map ffprobe; не-видео map игнорируется
     * (защита от случайной передачи аудио/субтитров).
     */
    public void populateFromProbeData(@NotNull Map<String, Object> probeData) {

        ProbeData probe = ProbeData.of(probeData);

        if (!probe.isCodecType("video")) {
            log.debug("Skipping non-video stream data: index={}", probe.integer("index").orElse(-1));
            return;
        }

        this.streamIndex = probe.integer("index").orElse(0);
        this.codecName = probe.text("codec_name").orElse(null);
        this.frameWidth = probe.integer("width").orElse(0);
        this.frameHeight = probe.integer("height").orElse(0);
        this.frameRateRaw = probe.text("r_frame_rate").orElse(null);
        this.pixelAspectRatio = probe.text("sample_aspect_ratio")
                .flatMap(VideoStream::parseFraction)
                .orElse(1.0);
        this.durationSeconds = firstPresent(
                probe.decimal("duration"),
                probe.nested("tags").decimal("duration"));

        probe.asMap().forEach((key, value) -> {
            if (value != null) {
                metadata.put(key, value);
            }
        });

    }

    /* ---------- Производные значения ---------- */

    /** Частота кадров: дробь "24000/1001" превращается в 23.976. */
    public Optional<Double> getCalculatedFrameRate() {
        return parseFraction(frameRateRaw);
    }

    /**
     * Битрейт потока в kbps: сначала {@code bit_rate}, затем теги с "bps"/"bitrate".
     * Ничего не найдено — 0 (вызывающий трактует как «битрейт неизвестен»).
     */
    public int getBitrateKbps() {

        OptionalDouble direct = ProbeData.of(metadata).decimal("bit_rate");
        if (direct.isPresent()) {
            return (int) (direct.getAsDouble() / 1000);
        }

        // Фолбэк: ищем битрейт в тегах (ключи, содержащие "bps"/"bitrate")
        ProbeData tags = ProbeData.of(metadata).nested("tags");

        for (Map.Entry<String, Object> entry : tags.asMap().entrySet()) {

            String key = String.valueOf(entry.getKey()).toLowerCase();
            if (!key.contains("bps") && !key.contains("bitrate")) {
                continue;
            }

            OptionalDouble fromTag = ProbeData.of(Map.of("v", entry.getValue())).decimal("v");
            if (fromTag.isPresent()) {
                return (int) (fromTag.getAsDouble() / 1000);
            }

        }

        log.warn("Bitrate not found in video stream metadata. Available keys: {}", metadata.keySet());

        return 0;

    }

    /**
     * Разрешение, масштабированное под целевую ширину с сохранением пропорций источника;
     * обе стороны выравниваются до чётных (требование x264/x265).
     *
     * @param targetWidth ширина ступени лестницы (например 1280)
     * @return пара {ширина, высота}
     */
    public int @NotNull [] scaledResolution(int targetWidth) {

        double ratio = frameHeight == 0 ? 1.0 : frameWidth / (double) frameHeight;
        int width = targetWidth & ~1;
        int height = ((int) (width / ratio)) & ~1;

        return new int[]{width, height};

    }

    /* ---------- Внутренний парсинг ---------- */

    /** Разбирает дробь вида "a/b" или "a:b" ("24000/1001", "16:9" сюда не передаётся). */
    private static Optional<Double> parseFraction(String raw) {

        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }

        String[] parts = raw.split("[/:]");
        if (parts.length == 0 || parts.length > 2) {
            return Optional.empty();
        }

        try {

            double numerator = Double.parseDouble(parts[0].trim());
            double denominator = parts.length == 2 ? Double.parseDouble(parts[1].trim()) : 1.0;

            return denominator != 0 ? Optional.of(numerator / denominator) : Optional.empty();

        } catch (NumberFormatException e) {
            log.debug("Invalid fraction format: {}", raw);
            return Optional.empty();
        }

    }

    /** Первое присутствующее значение из двух кандидатов. */
    private static double firstPresent(OptionalDouble primary, OptionalDouble fallback) {
        return primary.isPresent() ? primary.getAsDouble() : fallback.orElse(0.0);
    }
}
