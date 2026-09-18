package team.cinenetwork.ffmpeg.builder;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import team.cinenetwork.ffmpeg.builder.impl.AbstractFFmpegStrategy;
import team.cinenetwork.model.AudioStream;
import team.cinenetwork.model.VideoInfo;
import team.cinenetwork.options.AppOptions;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Команда MP4-remux (режим {@code --single-file}): видео + одна аудиодорожка без
 * перекодирования ({@code -c copy}).
 *
 * <p>Нужна онлайн-кинотеатру для быстрой раздачи «скачиваемых» копий под каждую озвучку:
 * с флагом {@code --all-audio-tracks} координатор собирает по команде на дорожку, а файлы
 * именуются по языку: {@code title_jpn.mp4}, {@code title_rus.mp4}, для повторов языка —
 * {@code title_rus_2.mp4}.
 */
@Slf4j
public class SingleFileStrategy extends AbstractFFmpegStrategy {

    private final AudioStream targetAudioStream;
    private final String customOutputName;

    public SingleFileStrategy(AppOptions options,
                              VideoInfo videoInfo,
                              AudioStream targetAudioStream,
                              String customOutputName) {
        super(options, videoInfo);
        this.targetAudioStream = targetAudioStream;
        this.customOutputName = customOutputName;
    }

    @Override
    public @NotNull List<String> build() {

        ensureDirectory(options.getOutput());

        String language = targetAudioStream.getLanguage();
        log.info("Building single file command for audio language: {}", language);

        List<String> args = new ArrayList<>(inputArgs());
        args.addAll(List.of(
                "-map", "0:" + requireVideoStream().getStreamIndex(),
                "-map", "0:" + targetAudioStream.getStreamIndex(),
                "-c", "copy",
                // Пишем язык в метаданные дорожки, чтобы плееры показывали его в списке
                "-metadata:s:a:0", "language=" + language,
                "-f", "mp4",
                "-movflags", "+faststart",
                determineOutputPath().toString()
        ));

        return args;

    }

    /* ---------- Именование выходного файла ---------- */

    /**
     * {@code <out>/<база>_<язык>[_N].mp4}, где N — порядковый номер дорожки внутри одного
     * языка (для случаев «вторая русская озвучка»).
     */
    private @NotNull Path determineOutputPath() {

        String baseName = resolveBaseName();
        String suffix = resolveLanguageSuffix();

        return options.getOutput().resolve(baseName + "_" + suffix + ".mp4");

    }

    private @NotNull String resolveBaseName() {

        if (customOutputName != null && !customOutputName.isBlank()) {
            return customOutputName;
        }

        String fileName = options.getInput().getFileName().toString();
        int dotIndex = fileName.lastIndexOf('.');

        return dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;

    }

    private @NotNull String resolveLanguageSuffix() {

        String language = sanitize(targetAudioStream.getLanguage());
        int trackOrdinal = countPrecedingTracksWithSameLanguage();

        return trackOrdinal == 0 ? language : language + "_" + (trackOrdinal + 1);

    }

    /** Сколько дорожек того же языка стоит перед текущей в исходнике. */
    private int countPrecedingTracksWithSameLanguage() {

        int count = 0;

        for (AudioStream stream : videoInfo.getAudioStreams()) {

            if (stream.getStreamIndex() == targetAudioStream.getStreamIndex()) {
                break;
            }

            if (sanitize(stream.getLanguage()).equals(sanitize(targetAudioStream.getLanguage()))) {
                count++;
            }

        }

        return count;

    }

    private @NotNull String sanitize(String language) {

        if (language == null || language.isBlank()) {
            return "und";
        }

        return language.replaceAll("[^a-zA-Z0-9_-]", "_");

    }
}
