package team.cinenetwork.processor.impl.hls;

import lombok.RequiredArgsConstructor;
import team.cinenetwork.ffmpeg.FFmpegExecutor;
import team.cinenetwork.ffmpeg.type.ProcessingType;
import team.cinenetwork.model.VideoInfo;
import team.cinenetwork.options.AppOptions;
import team.cinenetwork.processor.impl.artifact.ArtifactGenerator;

import java.io.IOException;

/**
 * HLS-«лестница» видео-вариантов: video/1080p, video/720p, ...
 *
 * <p>В режиме {@code --audio-only} этот же этап (TRANSCODE) несёт одну команду
 * {@code AudioOnlyStrategy} — самостоятельный аудио-плейлист в корне вывода.
 */
@RequiredArgsConstructor
public class HlsGenerator implements ArtifactGenerator {

    private final AppOptions options;
    private final VideoInfo videoInfo;
    private final FFmpegExecutor ffmpegExecutor;

    @Override
    public void generate() throws IOException {
        ffmpegExecutor.executeMediaProcessing(ProcessingType.TRANSCODE);
    }

    @Override
    public boolean isEnabled() {

        // audio-only не отключает этап, а меняет его наполнение: координатор вместо
        // видео-лестницы собирает команду AudioOnlyStrategy. Раньше режим исключался
        // здесь, и --audio-only молча не генерировал ничего (exit 0, пустой вывод).
        return !options.isHlsDisable()
                && (options.isAudioOnly() || videoInfo.getVideoStream() != null);

    }

    @Override
    public String name() {
        return "HLS streams";
    }
}
