package team.cinenetwork.processor.impl.meta;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import team.cinenetwork.model.VideoInfo;
import java.io.IOException;

public class MetadataParser {

    private final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public VideoInfo parse(String json) throws IOException {
        return MAPPER.readValue(json, VideoInfo.class);
    }
}