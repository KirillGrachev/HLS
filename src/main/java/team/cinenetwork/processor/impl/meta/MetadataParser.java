package team.cinenetwork.processor.impl.meta;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import team.cinenetwork.model.VideoInfo;

import java.io.IOException;

/**
 * Разбор JSON-вывода ffprobe в {@link VideoInfo}.
 *
 * <p>Неизвестные поля игнорируются (вывод ffprobe отличается по версиям),
 * поэтому парсинг не падает на новых версиях FFmpeg.
 */
public class MetadataParser {

    /** Mapper потокобезопасен и неизменяем — создаётся один раз. */
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /**
     * Разбирает JSON ffprobe в модель.
     *
     * @param json сырой вывод {@code ffprobe -print_format json}
     * @return заполненная модель источника
     * @throws IOException если JSON повреждён
     */
    public VideoInfo parse(String json) throws IOException {
        return MAPPER.readValue(json, VideoInfo.class);
    }
}
