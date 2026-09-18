package team.cinenetwork.processor.impl.playlist;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import team.cinenetwork.exception.ErrorCode;
import team.cinenetwork.exception.ProcessingException;
import team.cinenetwork.model.VideoInfo;
import team.cinenetwork.options.AppOptions;
import team.cinenetwork.processor.EncodingProfile;
import team.cinenetwork.processor.impl.rendition.AudioRendition;
import team.cinenetwork.processor.impl.rendition.AudioRenditions;
import team.cinenetwork.processor.impl.rendition.SubtitleRendition;
import team.cinenetwork.processor.impl.rendition.SubtitleRenditions;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Сборка мастер-плейлиста — «оглавления» HLS-потока.
 *
 * <p>Структура результирующего файла:
 * <pre>
 * #EXTM3U
 * #EXT-X-VERSION:5
 * #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="audio",NAME="Japanese",LANGUAGE="jpn",...,URI="audio/jpn/playlist.m3u8"
 * #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="audio",NAME="Russian",LANGUAGE="rus",...,URI="audio/rus/playlist.m3u8"
 * #EXT-X-MEDIA:TYPE=SUBTITLES,GROUP-ID="subtitles",NAME="Russian",LANGUAGE="rus",...,URI="subtitles/rus/playlist.m3u8"
 * #EXT-X-STREAM-INF:BANDWIDTH=...,RESOLUTION=1920x1080,...,AUDIO="audio",SUBTITLES="subtitles"
 * video/1080p/playlist.m3u8
 * ...
 * </pre>
 *
 * <p>Плейлист ссылается ровно на те папки, что создал планировщик рендишенов
 * ({@code AudioRenditions}/{@code SubtitleRenditions}) — URI и содержимое диска
 * гарантированно совпадают.
 */
@Slf4j
@RequiredArgsConstructor
public class MasterPlaylistBuilder {

    private static final String AUDIO_GROUP_ID = "audio";
    private static final String SUBTITLES_GROUP_ID = "subtitles";

    private final AppOptions options;
    private final VideoInfo videoInfo;
    private final EncodingProfile profile;

    public @NotNull String build() {

        if (options.isAudioOnly() || videoInfo.getVideoStream() == null) {
            return "";
        }

        List<AudioRendition> audioRenditions = resolveAudioRenditions();
        List<SubtitleRendition> subtitleRenditions = resolveSubtitleRenditions();

        StringBuilder content = new StringBuilder("#EXTM3U\n#EXT-X-VERSION:5\n");

        appendAudioMediaEntries(content, audioRenditions);
        appendSubtitleMediaEntries(content, subtitleRenditions);
        appendVariantEntries(content,
                !audioRenditions.isEmpty(),
                !subtitleRenditions.isEmpty());

        return content.toString();

    }

    /* ---------- Группа AUDIO ---------- */

    private @NotNull List<AudioRendition> resolveAudioRenditions() {

        if (options.isNoAudio() || options.isAudioDisable()) {
            return List.of();
        }

        return AudioRenditions.resolve(options, videoInfo);

    }

    private void appendAudioMediaEntries(StringBuilder content,
                                         List<AudioRendition> renditions) {

        for (int i = 0; i < renditions.size(); i++) {
            content.append(buildAudioMediaLine(renditions.get(i),
                    isDefaultAudio(renditions.get(i), i)));
        }

    }

    /** DEFAULT: язык из {@code --audio-default-language}, иначе первая дорожка. */
    private boolean isDefaultAudio(AudioRendition rendition, int index) {

        String requested = options.getAudioDefaultLanguage();
        if (requested == null || requested.isBlank()) {
            return index == 0;
        }

        return requested.equalsIgnoreCase(rendition.stream().getLanguage());

    }

    private @NotNull String buildAudioMediaLine(AudioRendition rendition,
                                                boolean isDefault) {

        Optional<String> codecTag = CodecTags.audioCodecTag(options.getAudioProfile());

        StringBuilder line = new StringBuilder("#EXT-X-MEDIA:TYPE=AUDIO")
                .append(",GROUP-ID=\"").append(AUDIO_GROUP_ID).append('"')
                .append(",NAME=\"").append(escape(rendition.stream().getDisplayName())).append('"')
                .append(",LANGUAGE=\"").append(rendition.stream().getLanguage()).append('"')
                .append(",AUTOSELECT=YES")
                .append(",DEFAULT=").append(isDefault ? "YES" : "NO")
                .append(",CHANNELS=\"").append(rendition.stream().getChannelCount()).append('"');

        codecTag.ifPresent(tag -> line.append(",CODECS=\"").append(tag).append('"'));
        line.append(",URI=\"").append(rendition.playlistUri()).append("\"\n");

        return line.toString();

    }

    /* ---------- Группа SUBTITLES ---------- */

    private @NotNull List<SubtitleRendition> resolveSubtitleRenditions() {

        if (!options.isSubsEnabled()) {
            return List.of();
        }

        return SubtitleRenditions.resolve(options, videoInfo);

    }

    private void appendSubtitleMediaEntries(StringBuilder content,
                                            List<SubtitleRendition> renditions) {

        for (SubtitleRendition rendition : renditions) {
            content.append(buildSubtitleMediaLine(rendition));
        }

    }

    private @NotNull String buildSubtitleMediaLine(SubtitleRendition rendition) {

        boolean isDefault = options.getSubsDefaultLanguage() != null
                && options.getSubsDefaultLanguage()
                        .equalsIgnoreCase(rendition.stream().getLanguage());

        StringBuilder line = new StringBuilder("#EXT-X-MEDIA:TYPE=SUBTITLES")
                .append(",GROUP-ID=\"").append(SUBTITLES_GROUP_ID).append('"')
                .append(",NAME=\"").append(escape(rendition.stream().getDisplayName())).append('"')
                .append(",LANGUAGE=\"").append(rendition.stream().getLanguage()).append('"')
                .append(",AUTOSELECT=YES")
                .append(",DEFAULT=").append(isDefault ? "YES" : "NO");

        if (rendition.stream().isForcedTrack()) {
            line.append(",FORCED=YES");
        }

        line.append(",URI=\"").append(rendition.playlistUri()).append("\"\n");

        return line.toString();

    }

    /* ---------- Видео-варианты ---------- */

    private void appendVariantEntries(StringBuilder content,
                                      boolean hasAudioGroup,
                                      boolean hasSubtitleGroup) {

        for (EncodingProfile.Variant variant : profile.variants()) {

            if (variant.bitrateKbps() <= 0) {
                log.debug("Skipping variant '{}' with zero bitrate", variant.name());
                continue;
            }

            content.append(buildVariantLine(variant, hasAudioGroup, hasSubtitleGroup));
            content.append("video/").append(variant.name()).append("/playlist.m3u8\n");

        }

    }

    private @NotNull String buildVariantLine(EncodingProfile.Variant variant,
                                             boolean hasAudioGroup,
                                             boolean hasSubtitleGroup) {

        int[] resolution = videoInfo.getVideoStream().scaledResolution(variant.width());
        double frameRate = videoInfo.getVideoStream().getCalculatedFrameRate()
                .orElseThrow(() -> ProcessingException.of(ErrorCode.FRAME_RATE_NOT_FOUND,
                        "Frame rate not found"));

        StringBuilder line = new StringBuilder("#EXT-X-STREAM-INF:")
                .append("BANDWIDTH=").append(calculateBandwidthBits(variant, hasAudioGroup))
                .append(",RESOLUTION=").append(resolution[0]).append('x').append(resolution[1])
                .append(String.format(Locale.ROOT, ",FRAME-RATE=%.3f", frameRate));

        CodecTags.videoCodecTag(variant.codec(), variant.profileLevel())
                .ifPresent(tag -> line.append(",CODECS=\"").append(tag).append('"'));

        if (hasAudioGroup) {
            line.append(",AUDIO=\"").append(AUDIO_GROUP_ID).append('"');
        }

        if (hasSubtitleGroup) {
            line.append(",SUBTITLES=\"").append(SUBTITLES_GROUP_ID).append('"');
        }

        line.append(",NAME=\"").append(escape(variant.name())).append("\"\n");

        return line.toString();

    }

    /**
     * BANDWIDTH в битах в секунду (требование спеки HLS): битрейт видео плюс
     * аудио-рендишен, который играет вместе с ним.
     */
    private long calculateBandwidthBits(EncodingProfile.Variant variant,
                                        boolean hasAudioGroup) {

        long totalKbps = variant.bitrateKbps() + (hasAudioGroup ? options.getAudioBitrate() : 0);

        return totalKbps * 1000L;

    }

    /* ---------- Вспомогательные ---------- */

    /** Кавычки внутри NAME/URI ломают парсинг плейлиста — меняем их на апострофы. */
    private @NotNull String escape(String value) {
        return value == null ? "" : value.replace('"', '\'');
    }
}
