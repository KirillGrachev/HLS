package team.cinenetwork.processor.impl.audio;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.cinenetwork.ffmpeg.FFmpegExecutor;
import team.cinenetwork.ffmpeg.type.ProcessingType;
import team.cinenetwork.model.VideoInfo;
import team.cinenetwork.options.AppOptions;
import team.cinenetwork.processor.impl.artifact.ArtifactGenerator;
import team.cinenetwork.processor.impl.rendition.AudioRenditions;

import java.io.IOException;
import java.util.stream.Collectors;

/**
 * Аудио-рендишены: отдельный HLS-поток на каждую дорожку озвучки.
 *
 * <p>Ключевой сценарий онлайн-кинотеатра: зритель переключает «Japanese / Russian /
 * English» прямо во время просмотра, без перезагрузки плеера — дорожки опубликованы
 * в мастер-плейлисте как {@code EXT-X-MEDIA:TYPE=AUDIO}.
 */
@Slf4j
@RequiredArgsConstructor
public class AudioRenditionGenerator implements ArtifactGenerator {

    private final AppOptions options;
    private final VideoInfo videoInfo;
    private final FFmpegExecutor ffmpegExecutor;

    @Override
    public void generate() throws IOException {

        var renditions = AudioRenditions.resolve(options, videoInfo);
        if (renditions.isEmpty()) {
            log.warn("No audio renditions to generate (no matching tracks)");
            return;
        }

        ffmpegExecutor.executeMediaProcessing(ProcessingType.AUDIO);

        log.info("Audio renditions generated: {}", renditions.stream()
                .map(rendition -> rendition.folderName()
                        + " (" + rendition.stream().getDisplayName() + ")")
                .collect(Collectors.joining(", ")));

    }

    @Override
    public boolean isEnabled() {

        return !options.isHlsDisable()
                && !options.isAudioOnly()
                && !options.isNoAudio()
                && !options.isAudioDisable()
                && !videoInfo.getAudioStreams().isEmpty();

    }

    @Override
    public String name() {
        return "Audio renditions (HLS)";
    }
}
