package team.cinenetwork.ffmpeg.builder.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import team.cinenetwork.exception.ErrorCode;
import team.cinenetwork.exception.ProcessingException;
import team.cinenetwork.model.AudioStream;
import team.cinenetwork.model.VideoInfo;
import team.cinenetwork.model.VideoStream;
import team.cinenetwork.options.AppOptions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Общая база всех стратегий: хранит опции и метаданные, даёт переиспользуемые
 * «строительные блоки» команд.
 *
 * <p>Почему {@code -y} и {@code -nostdin} присутствуют всегда:
 * <ul>
 *   <li>{@code -y} — выходная директория уже проверена/подготовлена на старте пайплайна
 *       ({@code FileUtil.prepareOutputDirectory}), поэтому промпты FFmpeg о перезаписи
 *       не нужны и вредны (процесс зависает);</li>
 *   <li>{@code -nostdin} — FFmpeg не должен читать stdin: в сервисе без консоли любой
 *       промпт означает вечное ожидание.</li>
 * </ul>
 */
@Slf4j
@RequiredArgsConstructor
public abstract class AbstractFFmpegStrategy implements FFmpegCommandStrategy {

    /** Форматы seek-позиции: проценты ("45%") или секунды ("120s"). */
    private static final Pattern SEEK_PATTERN = Pattern.compile("(\\d+)%|(\\d+)s");

    protected final AppOptions options;
    protected final VideoInfo videoInfo;

    @Override
    public abstract @NotNull List<String> build();

    /* ---------- Вход ---------- */

    /** Единая преамбула: перезаписывать без вопросов, не читать stdin, входной файл. */
    protected @NotNull List<String> inputArgs() {
        return List.of("-y", "-nostdin", "-i", options.getInput().toString());
    }

    /** Преамбула с быстрым seek до входа (для постера). */
    protected @NotNull List<String> inputArgsWithSeek(String seekPosition) {
        return List.of("-y", "-nostdin", "-ss", seekPosition, "-i", options.getInput().toString());
    }

    /** Видеопоток источника; бросает NO_VIDEO_STREAM, если его нет. */
    protected @NotNull VideoStream requireVideoStream() {

        VideoStream video = videoInfo.getVideoStream();
        if (video == null) {
            throw ProcessingException.of(ErrorCode.NO_VIDEO_STREAM,
                    "Strategy requires a video stream, but source has none");
        }

        return video;

    }

    /* ---------- Геометрия и тайминги ---------- */

    /** Разрешение ступени лестницы: пропорции источника, чётные стороны. */
    protected int @NotNull [] scaledResolution(int targetWidth) {
        return requireVideoStream().scaledResolution(targetWidth);
    }

    /** Разбирает "16:9" в 1.777...; мусор → INVALID_ASPECT_RATIO. */
    protected double parseRatio(@NotNull String ratio) {

        String[] parts = ratio.split(":");
        if (parts.length != 2) {
            throw ProcessingException.of(ErrorCode.INVALID_ASPECT_RATIO,
                    "Invalid aspect ratio: " + ratio);
        }

        try {

            double value = Double.parseDouble(parts[0]) / Double.parseDouble(parts[1]);
            if (value <= 0) {
                throw ProcessingException.of(ErrorCode.INVALID_ASPECT_RATIO,
                        "Aspect ratio must be positive: " + ratio);
            }

            return value;

        } catch (NumberFormatException e) {
            throw ProcessingException.of(ErrorCode.INVALID_ASPECT_RATIO,
                    "Invalid aspect ratio: " + ratio, e);
        }

    }

    /** Разбирает seek "45%" / "120s" в секунды (для {@code -ss}). */
    protected @NotNull String parseSeekPosition(String seek, double durationSeconds) {

        Matcher matcher = SEEK_PATTERN.matcher(seek);
        if (!matcher.matches()) {
            throw ProcessingException.of(ErrorCode.INVALID_COMMAND_ARGUMENTS,
                    "Invalid seek position format: " + seek);
        }

        if (matcher.group(1) != null) {
            double percentage = Double.parseDouble(matcher.group(1)) / 100.0;
            return String.valueOf(Math.round(durationSeconds * percentage));
        }

        return matcher.group(2);

    }

    /** GOP-интервал: ключевые кадры ровно по границам сегментов — иначе плеер переключает качество с задержкой. */
    protected int calculateKeyframeInterval() {

        double fps = requireVideoStream().getCalculatedFrameRate()
                .orElseThrow(() -> ProcessingException.of(ErrorCode.FRAME_RATE_NOT_FOUND,
                        "Frame rate is required to calculate keyframe interval"));

        return (int) Math.ceil(fps * options.getHlsTime());

    }

    /* ---------- Кодеки ---------- */

    /**
     * Достаёт профиль ("high") или уровень ("5.2") из строки вида "high@5.2".
     *
     * @param profileLevel строка профиля вида {@code profile@level}
     * @param isProfile    true — вернуть профиль, false — уровень
     */
    protected @NotNull String extractProfileLevel(@NotNull String profileLevel,
                                                  boolean isProfile) {

        String[] parts = profileLevel.split("@");

        return isProfile ? parts[0] : parts.length > 1 ? parts[1] : "main";

    }

    /**
     * Маппит и кодирует аудиодорожку: кодек, профиль, битрейт, sampling rate.
     * Маппинг — по индексу дорожки ({@code -map 0:N}): это надёжнее символьных
     * селекторов, которые раньше могли попасть в команду литералом "null".
     */
    protected @NotNull List<String> audioEncodeArgs(@NotNull AudioStream audio) {

        List<String> args = new ArrayList<>(List.of(
                "-map", "0:" + audio.getStreamIndex(),
                "-c:a", options.getAudioCodec(),
                "-profile:a", options.getAudioProfile(),
                "-b:a", options.getAudioBitrate() + "k"
        ));

        Integer requestedSampling = options.getAudioSampling();
        if (requestedSampling != null) {
            args.addAll(List.of("-ar", requestedSampling.toString()));
        } else if (audio.getSamplingRateHz() > 0) {
            args.addAll(List.of("-ar", String.valueOf(audio.getSamplingRateHz())));
        }

        return args;

    }

    /* ---------- HLS-мультиплексор ---------- */

    /**
     * Единый HLS-выход: VOD-плейлист фиксированной длины, сегменты в указанную папку.
     * {@code independent_segments} позволяет плееру переключать качество без ресинхрона.
     *
     * @param segmentPattern шаблон имён сегментов ({@code .../segment_%03d.ts} или {%03d.ts})
     * @param playlistPath   путь медиа-плейлиста
     */
    protected @NotNull List<String> hlsOutputArgs(@NotNull Path segmentPattern,
                                                  @NotNull Path playlistPath) {

        return List.of(
                "-f", "hls",
                "-hls_time", String.valueOf(options.getHlsTime()),
                "-hls_playlist_type", "vod",
                "-hls_list_size", "0",
                "-hls_segment_type", options.getHlsType().name(),
                "-hls_flags", "independent_segments",
                "-hls_base_url", options.getHlsSegmentPrefix(),
                "-hls_segment_filename", segmentPattern.toString(),
                playlistPath.toString()
        );

    }

    /* ---------- Файловая система ---------- */

    /** Создаёт директорию (с родительскими); ошибку IO заворачивает в наше исключение. */
    protected @NotNull Path ensureDirectory(Path path) {

        try {

            Files.createDirectories(path);
            return path;

        } catch (IOException e) {
            throw ProcessingException.of(ErrorCode.VIDEO_PROCESSING_FAILED,
                    "Cannot create directory: " + path, e);
        }

    }
}
