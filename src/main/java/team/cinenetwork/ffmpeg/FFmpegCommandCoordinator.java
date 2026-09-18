package team.cinenetwork.ffmpeg;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import team.cinenetwork.exception.ErrorCode;
import team.cinenetwork.exception.ProcessingException;
import team.cinenetwork.ffmpeg.builder.AudioOnlyStrategy;
import team.cinenetwork.ffmpeg.builder.AudioRenditionStrategy;
import team.cinenetwork.ffmpeg.builder.HlsVideoStrategy;
import team.cinenetwork.ffmpeg.builder.PosterStrategy;
import team.cinenetwork.ffmpeg.builder.SingleFileStrategy;
import team.cinenetwork.ffmpeg.builder.SubtitleStrategy;
import team.cinenetwork.model.AudioStream;
import team.cinenetwork.model.VideoInfo;
import team.cinenetwork.options.AppOptions;
import team.cinenetwork.processor.EncodingProfile;
import team.cinenetwork.processor.impl.rendition.AudioRenditions;
import team.cinenetwork.processor.impl.rendition.SubtitleRenditions;

import java.util.ArrayList;
import java.util.List;

/**
 * Сборка наборов команд FFmpeg для каждого этапа обработки.
 *
 * <p>Это «диспетчерская»: здесь сосредоточены условия («когда нужны субтитры?
 * когда аудио?»), но не устройство аргументов конкретной команды (это дело стратегий).
 * Благодаря этому условия не размазаны по генераторам.
 */
@Slf4j
@RequiredArgsConstructor
public class FFmpegCommandCoordinator {

    private final AppOptions options;
    private final VideoInfo videoInfo;
    private final EncodingProfile profile;

    /* ---------- Видео: лестница вариантов ---------- */

    /** Команды видео-лестницы; в audio-only режиме — одна команда аудио-плейлиста. */
    public @NotNull List<List<String>> buildTranscodeCommands() {

        List<List<String>> commands = new ArrayList<>();

        if (options.isAudioOnly()) {
            commands.add(new AudioOnlyStrategy(options, videoInfo).build());
            return commands;
        }

        for (EncodingProfile.Variant variant : profile.variants()) {
            commands.add(new HlsVideoStrategy(options, videoInfo, variant).build());
        }

        return commands;

    }

    /* ---------- Аудио: рендишены по дорожкам ---------- */

    /** По команде на каждую озвучку; пусто, если аудио отключено флагами. */
    public @NotNull List<List<String>> buildAudioRenditionCommands() {

        if (options.isAudioOnly() || options.isNoAudio() || options.isAudioDisable()) {
            log.debug("Audio renditions disabled by options");
            return List.of();
        }

        return AudioRenditions.resolve(options, videoInfo).stream()
                .map(rendition -> new AudioRenditionStrategy(options, videoInfo, rendition).build())
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);

    }

    /* ---------- Субтитры: WebVTT по дорожкам ---------- */

    /** По команде конвертации на каждую текстовую дорожку субтитров. */
    public @NotNull List<List<String>> buildSubtitleCommands() {

        if (!options.isSubsEnabled()) {
            log.debug("Subtitle extraction disabled by options");
            return List.of();
        }

        return SubtitleRenditions.resolve(options, videoInfo).stream()
                .map(rendition -> new SubtitleStrategy(options, videoInfo, rendition).build())
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);

    }

    /* ---------- Постер ---------- */

    /** Одна команда постера; пусто в audio-only режиме или без видеопотока. */
    public @NotNull List<List<String>> buildPosterCommands() {

        if (options.isAudioOnly() || videoInfo.getVideoStream() == null) {
            return List.of();
        }

        return List.of(new PosterStrategy(options, videoInfo, profile.posterWidth()).build());

    }

    /* ---------- Single-file (MP4 remux) ---------- */

    /** Команды MP4-remux: одна на дорожку в режиме {@code --all-audio-tracks}, иначе одна на выбранный трек. */
    public @NotNull List<List<String>> buildSingleFileCommands() {

        List<List<String>> commands = new ArrayList<>();

        if (!options.isSingleFile()) {
            return commands;
        }

        if (options.isAllAudioTracks()) {
            return buildSingleFileCommandPerAudioTrack();
        }

        return List.of(new SingleFileStrategy(options, videoInfo,
                resolveSingleAudioTrack(),
                options.getOutputFilename()).build());

    }

    private @NotNull List<List<String>> buildSingleFileCommandPerAudioTrack() {

        List<AudioStream> tracks = videoInfo.getAudioStreams();
        if (tracks.isEmpty()) {
            log.warn("All audio tracks mode requested, but no audio streams found.");
            return List.of();
        }

        List<List<String>> commands = new ArrayList<>();
        for (AudioStream track : tracks) {
            commands.add(new SingleFileStrategy(options, videoInfo, track,
                    options.getOutputFilename()).build());
        }

        return commands;

    }

    /** Дорожка по селектору {@code --audio-stream}; если не задан — первая. */
    private @NotNull AudioStream resolveSingleAudioTrack() {

        if (options.getAudioStream() != null) {
            return videoInfo.findAudioStream(options.getAudioStream())
                    .orElseThrow(() -> ProcessingException.of(ErrorCode.AUDIO_STREAM_NOT_FOUND,
                            "Selected audio stream not found: " + options.getAudioStream()));
        }

        AudioStream first = videoInfo.getAudioStream();
        if (first == null) {
            throw ProcessingException.of(ErrorCode.AUDIO_STREAM_NOT_FOUND,
                    "No audio stream available for single file output");
        }

        return first;

    }
}
