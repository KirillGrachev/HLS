package team.cinenetwork.processor;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import team.cinenetwork.exception.ErrorCode;
import team.cinenetwork.exception.ProcessingException;
import team.cinenetwork.ffmpeg.FFmpegExecutor;
import team.cinenetwork.ffmpeg.FFprobeExecutor;
import team.cinenetwork.ffmpeg.type.ProcessingType;
import team.cinenetwork.model.VideoInfo;
import team.cinenetwork.options.AppOptions;
import team.cinenetwork.processor.impl.artifact.ArtifactGenerator;
import team.cinenetwork.processor.impl.artifact.ArtifactGenerators;
import team.cinenetwork.processor.impl.meta.MetadataParser;
import team.cinenetwork.utils.FileUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Пайплайн обработки видео — «дирижёр» приложения.
 *
 * <p>Шаги обработки (см. {@link #process()}): подготовка выходной директории →
 * probe источника (ffprobe, один раз на весь пайплайн) → валидация потоков →
 * нормализация опций в {@link EncodingProfile} → запуск FFmpeg (MP4-режим или
 * генерация артефактов) → чистка временных файлов.
 *
 * <p>Сам процессор не умеет ни запускать FFmpeg, ни строить плейлисты: он делегирует
 * исполнителям и генераторам. Его ответственность — порядок шагов и обработка ошибок.
 */
@Slf4j
public class VideoProcessor {

    private final AppOptions options;
    private final MetadataParser metadataParser;
    private final OptionsNormalizer optionsNormalizer;

    public VideoProcessor(@NotNull AppOptions options,
                          @NotNull MetadataParser metadataParser,
                          @NotNull OptionsNormalizer optionsNormalizer) {

        this.options = options;
        this.metadataParser = metadataParser;
        this.optionsNormalizer = optionsNormalizer;

    }

    /** Фабрика с дефолтными зависимостями (используется из {@code Main}). */
    public static @NotNull VideoProcessor create(@NotNull AppOptions options) {
        return new VideoProcessor(options, new MetadataParser(), new OptionsNormalizer());
    }

    /**
     * Запускает весь пайплайн обработки.
     *
     * @throws ProcessingException при любой ошибке обработки (с кодом и контекстом)
     */
    public void process() throws ProcessingException {

        try {

            prepareOutputDirectory();

            VideoInfo videoInfo = probeVideoMetadata();

            validateMediaStreams(videoInfo);
            EncodingProfile profile = normalizeProcessingOptions(videoInfo);

            FFmpegExecutor ffmpegExecutor =
                    new FFmpegExecutor(options, videoInfo, profile);

            if (options.isSingleFile()) {
                ffmpegExecutor.executeMediaProcessing(ProcessingType.SINGLE_FILE);
            } else {
                generateArtifacts(videoInfo, profile, ffmpegExecutor);
            }

            cleanTemporaryFiles();

        } catch (ProcessingException e) {
            throw e;

        } catch (Exception e) {
            throw ProcessingException.of(ErrorCode.VIDEO_PROCESSING_FAILED,
                    "Video processing failed", e);

        }

    }

    /* ---------- Шаги пайплайна ---------- */

    /** Создаёт или проверяет выходную директорию с учётом {@code --output-overwrite}. */
    private void prepareOutputDirectory() throws IOException {

        FileUtil.prepareOutputDirectory(
                options.getOutput(),
                options.isOutputOverwrite()
        );

    }

    /**
     * Зондирует источник ровно один раз: полученный {@link VideoInfo} используют
     * все последующие шаги (раньше ffprobe гоняли дважды).
     */
    private @NotNull VideoInfo probeVideoMetadata() throws IOException {

        String probeJson = new FFprobeExecutor(options).probeMediaInfo();
        VideoInfo videoInfo = metadataParser.parse(probeJson);

        log.info("Source probed: video={}, audio track(s)={}, subtitle track(s)={}",
                videoInfo.getVideoStream() != null ? "yes" : "no",
                videoInfo.getAudioStreams().size(),
                videoInfo.getSubtitleStreams().size());

        return videoInfo;

    }

    /** Проверяет минимум потоков: видео обязательно, для audio-only — ещё и аудио. */
    private void validateMediaStreams(@NotNull VideoInfo videoInfo) {

        if (videoInfo.getVideoStream() == null) {
            throw ProcessingException.of(ErrorCode.NO_VIDEO_STREAM,
                    "Source contains no video stream");
        }

        if (options.isAudioOnly() && videoInfo.getAudioStream() == null) {
            throw ProcessingException.of(ErrorCode.AUDIO_ONLY,
                    "Audio-only mode requires audio stream");
        }

    }

    /** Превращает сырые опции в неизменяемый план кодирования. */
    private @NotNull EncodingProfile normalizeProcessingOptions(@NotNull VideoInfo videoInfo) {
        return optionsNormalizer.normalizeEncodingOptions(options, videoInfo);
    }

    /**
     * Генерирует все включённые артефакты по порядку (см. {@link ArtifactGenerators}):
     * постер → видео → аудио → субтитры → мастер-плейлист.
     */
    private void generateArtifacts(@NotNull VideoInfo videoInfo,
                                   @NotNull EncodingProfile profile,
                                   @NotNull FFmpegExecutor ffmpegExecutor)
            throws IOException {

        List<ArtifactGenerator> generators =
                ArtifactGenerators.createAll(options, videoInfo, profile, ffmpegExecutor);

        List<String> generated = new ArrayList<>();

        for (ArtifactGenerator generator : generators) {

            if (!generator.isEnabled()) {
                log.debug("Artifact disabled: {}", generator.name());
                continue;
            }

            log.info("Generating {}...", generator.name());
            generator.generate();
            generated.add(generator.name());

        }

        log.info("Artifacts generated: {}", String.join(", ", generated));

    }

    /** Удаляет временные файлы FFmpeg (шаблон {@code _*}) из корня вывода. */
    private void cleanTemporaryFiles() throws IOException {

        FileUtil.deleteFilesByPattern(
                options.getOutput(),
                "_*"
        );

    }
}
