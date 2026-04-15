package team.cinenetwork.processor.impl.playlist;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

@Slf4j
@RequiredArgsConstructor
public class PlaylistWriter {

    public void write(@NotNull String content,
                      @NotNull Path outputPath)
            throws IOException {

        if (content.isEmpty()) {
            log.debug("Skipping playlist write - empty content");
            return;
        }

        Files.createDirectories(outputPath.getParent());
        Files.writeString(outputPath, content,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);

        log.info("Master playlist written to {}", outputPath);

    }
}