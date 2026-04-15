package team.cinenetwork.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Data
@Slf4j
@JsonIgnoreProperties(ignoreUnknown = true)
public class VideoInfo {

    private VideoStream videoStream;
    private AudioStream audioStream;

    private List<AudioStream> audioStreams = new ArrayList<>();
    private List<Map<String, Object>> mediaStreams;

    private Map<String, Object> formatDetails;
    private double formatDuration;

    @JsonProperty("format")
    public void setFormatDetails(Map<String, Object> formatDetails) {

        this.formatDetails = formatDetails;

        if (formatDetails != null
                && formatDetails.containsKey("duration")) {

            try {

                this.formatDuration = Double.parseDouble(formatDetails
                        .get("duration").toString());

            } catch (NumberFormatException e) {
                log.warn("Failed to parse format duration", e);
            }
        }
    }

    @JsonProperty("streams")
    public void processMediaStreams(@NotNull List<Map<String, Object>> streams) {
        this.mediaStreams = streams;
        extractPrimaryStreams(streams);
    }

    private void extractPrimaryStreams(@NotNull List<Map<String, Object>> streams) {

        for (Map<String, Object> stream : streams) {

            String streamType = (String) stream.get("codec_type");
            if (streamType == null) continue;

            switch (streamType) {
                case "video" -> initVideoStreamIfMissing(stream);
                case "audio" -> initAudioStreamIfMissing(stream);
            }
        }
    }

    public Optional<AudioStream> findAudioStream(String streamSelector) {

        if (streamSelector == null || streamSelector.isEmpty()) {
            return Optional.ofNullable(audioStream);
        }

        String[] parts = streamSelector.split(":");
        int streamIndex = parts.length > 1 ? Integer.parseInt(parts[1])
                : Integer.parseInt(parts[0]);

        return mediaStreams.stream()
                .filter(s -> "audio".equals(s.get("codec_type")))
                .filter(s -> (int)s.get("index") == streamIndex)
                .findFirst()
                .map(stream -> {
                    AudioStream audio = new AudioStream();
                    audio.populateFromProbeData(stream);
                    return audio;
                });

    }

    private void initVideoStreamIfMissing(Map<String, Object> streamData) {
        if (videoStream == null) {
            videoStream = new VideoStream();
            videoStream.populateFromProbeData(streamData);
        }
    }

    private void initAudioStreamIfMissing(Map<String, Object> streamData) {
        if (audioStream == null) {
            audioStream = new AudioStream();
            audioStream.populateFromProbeData(streamData);
        }
    }

    public double getDuration() {
        if (videoStream != null && videoStream.getDurationSeconds() > 0) {
            return videoStream.getDurationSeconds();
        }
        return formatDuration;
    }
}