package team.cinenetwork.processor.impl.artifact;

import java.io.IOException;

public interface ArtifactGenerator {

    void generate() throws IOException;
    boolean isEnabled();

}