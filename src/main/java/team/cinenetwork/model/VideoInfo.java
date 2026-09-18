package team.cinenetwork.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Агрегированный результат ffprobe: всё, что пайплайн знает об исходнике.
 *
 * <p>Ключевая починка рефакторинга: раньше {@link #audioStreams} всегда был пуст
 * (в {@link #audioStream} падала только первая дорожка), из-за чего режим
 * {@code --all-audio-tracks} никогда не работал. Теперь собираются все дорожки:
 * видео — первый видеопоток, аудио — все дорожки, субтитры — все дорожки.
 */
@Data
@Slf4j
@JsonIgnoreProperties(ignoreUnknown = true)
public class VideoInfo {

    /** Первый видеопоток. */
    private VideoStream videoStream;

    /** Первая аудиодорожка — «дефолт» для легаси-кода и single-audio сценариев. */
    private AudioStream audioStream;

    /** Все аудиодорожки исходника (озвучки). */
    private List<AudioStream> audioStreams = new ArrayList<>();

    /** Все дорожки субтитров исходника. */
    private List<SubtitleStream> subtitleStreams = new ArrayList<>();

    /** Сырые map всех потоков — для диагностики и тонких селекторов. */
    private List<Map<String, Object>> mediaStreams;

    /** Сырая секция {@code format} из ffprobe. */
    private Map<String, Object> formatDetails;

    /** Длительность контейнера, секунды. */
    private double formatDuration;

    /* ---------- Jackson ---------- */

    @JsonProperty("format")
    public void setFormatDetails(Map<String, Object> formatDetails) {

        this.formatDetails = formatDetails;

        if (formatDetails != null && formatDetails.containsKey("duration")) {

            try {

                this.formatDuration = Double.parseDouble(formatDetails
                        .get("duration").toString());

            } catch (NumberFormatException e) {
                log.warn("Failed to parse format duration: {}", formatDetails.get("duration"));
            }

        }

    }

    @JsonProperty("streams")
    public void processMediaStreams(@NotNull List<Map<String, Object>> streams) {
        this.mediaStreams = streams;
        streams.forEach(this::registerStream);
    }

    /* ---------- Распределение потоков по типам ---------- */

    /** Раскладывает поток в нужную коллекцию; неизвестные типы (data, attachment) игнорирует. */
    private void registerStream(@NotNull Map<String, Object> streamData) {

        Object rawType = streamData.get("codec_type");
        if (rawType == null) {
            return;
        }

        switch (rawType.toString().toLowerCase()) {
            case "video" -> registerVideoStream(streamData);
            case "audio" -> registerAudioStream(streamData);
            case "subtitle" -> registerSubtitleStream(streamData);

            default -> log.debug("Ignoring stream of type '{}'", rawType);
        }

    }

    private void registerVideoStream(Map<String, Object> streamData) {

        if (videoStream == null) {
            videoStream = new VideoStream();
            videoStream.populateFromProbeData(streamData);
        }

    }

    private void registerAudioStream(Map<String, Object> streamData) {

        AudioStream audio = new AudioStream();
        audio.populateFromProbeData(streamData);
        audioStreams.add(audio);

        if (audioStream == null) {
            audioStream = audio;
        }

    }

    private void registerSubtitleStream(Map<String, Object> streamData) {

        SubtitleStream subtitle = new SubtitleStream();
        subtitle.populateFromProbeData(streamData);
        subtitleStreams.add(subtitle);

    }

    /* ---------- Селекторы ---------- */

    /**
     * Ищет аудиодорожку по селектору: {@code "index:1"}, {@code "1"} или {@code null}
     * (null означает «дефолтная», т.е. первая).
     *
     * @param streamSelector селектор из CLI ({@code --audio-stream}) или null
     * @return найденная дорожка, если она есть
     */
    public Optional<AudioStream> findAudioStream(@Nullable String streamSelector) {

        if (streamSelector == null || streamSelector.isBlank()) {
            return Optional.ofNullable(audioStream);
        }

        String[] parts = streamSelector.split(":");
        String rawIndex = parts[parts.length - 1].trim();

        try {

            int index = Integer.parseInt(rawIndex);

            return audioStreams.stream()
                    .filter(stream -> stream.getStreamIndex() == index)
                    .findFirst();

        } catch (NumberFormatException e) {
            log.warn("Invalid audio stream selector: '{}'", streamSelector);
            return Optional.empty();
        }

    }

    /** Длительность воспроизведения: из видеопотока, если известна, иначе из контейнера. */
    public double getDuration() {

        if (videoStream != null && videoStream.getDurationSeconds() > 0) {
            return videoStream.getDurationSeconds();
        }

        return formatDuration;

    }
}
