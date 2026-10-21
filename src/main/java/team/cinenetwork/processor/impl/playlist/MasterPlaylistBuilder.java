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

        List<AudioRendition> resolvedAudio = audioRenditions;
        List<SubtitleRendition> resolvedSubs = subtitleRenditions;

        // Сегменты режутся по ключевым кадрам (-force_key_frames в команде
        // кодирования), то есть каждый сегмент декодируется независимо. Без этого
        // тега плеер обязан считать, что зависимости есть, и не может ни начать
        // воспроизведение с произвольного сегмента, ни переключать вариант без
        // перебуферизации.
        content.append("#EXT-X-INDEPENDENT-SEGMENTS\n");

        int defaultAudioIndex = resolveDefaultIndex(resolvedAudio,
                options.getAudioDefaultLanguage(),
                rendition -> rendition.stream().getLanguage(),
                "audio");
        int defaultSubtitleIndex = resolveDefaultIndex(resolvedSubs,
                options.getSubsDefaultLanguage(),
                rendition -> rendition.stream().getLanguage(),
                "subtitles");

        appendAudioMediaEntries(content, resolvedAudio, defaultAudioIndex);
        appendSubtitleMediaEntries(content, resolvedSubs, defaultSubtitleIndex);
        appendVariantEntries(content,
                !resolvedAudio.isEmpty(),
                !resolvedSubs.isEmpty());

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
                                         List<AudioRendition> renditions,
                                         int defaultIndex) {

        // Тег кодека зависит только от --audio-profile и одинаков для всех
        // рендишенов группы, поэтому вычисляется один раз, а не на каждой итерации.
        Optional<String> codecTag = CodecTags.audioCodecTag(options.getAudioProfile());

        for (int i = 0; i < renditions.size(); i++) {
            content.append(buildAudioMediaLine(renditions.get(i), i == defaultIndex, codecTag));
        }

    }

    private @NotNull String buildAudioMediaLine(AudioRendition rendition,
                                                boolean isDefault,
                                                Optional<String> codecTag) {

        StringBuilder line = new StringBuilder("#EXT-X-MEDIA:TYPE=AUDIO")
                .append(",GROUP-ID=\"").append(AUDIO_GROUP_ID).append('"')
                .append(",NAME=\"").append(quoted(rendition.stream().getDisplayName())).append('"')
                .append(",LANGUAGE=\"").append(quoted(rendition.stream().getLanguage())).append('"')
                .append(",AUTOSELECT=YES")
                .append(",DEFAULT=").append(isDefault ? "YES" : "NO")
                .append(",CHANNELS=\"").append(rendition.stream().getChannelCount()).append('"');

        codecTag.ifPresent(tag -> line.append(",CODECS=\"").append(quoted(tag)).append('"'));
        line.append(",URI=\"").append(quoted(rendition.playlistUri())).append("\"\n");

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
                                            List<SubtitleRendition> renditions,
                                            int defaultIndex) {

        for (int i = 0; i < renditions.size(); i++) {
            content.append(buildSubtitleMediaLine(renditions.get(i), i == defaultIndex));
        }

    }

    private @NotNull String buildSubtitleMediaLine(SubtitleRendition rendition,
                                                   boolean isDefault) {

        StringBuilder line = new StringBuilder("#EXT-X-MEDIA:TYPE=SUBTITLES")
                .append(",GROUP-ID=\"").append(SUBTITLES_GROUP_ID).append('"')
                .append(",NAME=\"").append(quoted(rendition.stream().getDisplayName())).append('"')
                .append(",LANGUAGE=\"").append(quoted(rendition.stream().getLanguage())).append('"')
                .append(",AUTOSELECT=YES")
                .append(",DEFAULT=").append(isDefault ? "YES" : "NO");

        if (rendition.stream().isForcedTrack()) {
            line.append(",FORCED=YES");
        }

        line.append(",URI=\"").append(quoted(rendition.playlistUri())).append("\"\n");

        return line.toString();

    }

    /* ---------- Видео-варианты ---------- */

    private void appendVariantEntries(StringBuilder content,
                                      boolean hasAudioGroup,
                                      boolean hasSubtitleGroup) {

        for (EncodingProfile.Variant variant : profile.variants()) {

            if (variant.bitrateKbps() <= 0) {
                // WARN, а не DEBUG: пропавшая ступень лестницы видна только в
                // готовом плейлисте, и искать причину в отладочном логе, который
                // в бою обычно выключен, — значит искать вслепую.
                log.warn("Skipping variant '{}' because its bitrate is {} kbps;"
                                + " the master playlist will not offer this resolution",
                        variant.name(), variant.bitrateKbps());
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
                .ifPresent(tag -> line.append(",CODECS=\"").append(quoted(tag)).append('"'));

        if (hasAudioGroup) {
            line.append(",AUDIO=\"").append(AUDIO_GROUP_ID).append('"');
        }

        if (hasSubtitleGroup) {
            line.append(",SUBTITLES=\"").append(SUBTITLES_GROUP_ID).append('"');
        }

        line.append(",NAME=\"").append(quoted(variant.name())).append("\"\n");

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

    /**
     * Определяет единственный рендишен группы, который помечается DEFAULT=YES.
     *
     * <p>Спецификация HLS запрещает более одного DEFAULT=YES в группе, поэтому
     * выбирается индекс, а не флаг на рендишен. Прежнее сравнение языка прямо в
     * построителе строки давало DEFAULT=YES каждой дорожке с запрошенным языком:
     * при двух русских дорожках обе становились дефолтными, и часть плееров
     * выбирала произвольную либо отказывалась воспроизводить группу.
     *
     * <p>Если запрошенный язык не найден ни в одной дорожке, дефолтом назначается
     * первая. Раньше в этом случае DEFAULT=YES не получал никто, и выбор падал на
     * плеер: результат зависел от клиента, а не от настройки, и воспроизводилось
     * «то, что плеер сочтёт нужным» вместо запрошенного пользователем поведения.
     *
     * @param renditions         рендишены группы
     * @param requestedLanguage  язык из опций, может быть null или пустым
     * @param languageOf         способ получить язык рендишена
     * @param groupName          имя группы для сообщения в логе
     * @param <T>                тип рендишена
     * @return индекс дефолтного рендишена либо -1 для пустой группы
     */
    private <T> int resolveDefaultIndex(@NotNull List<T> renditions,
                                        String requestedLanguage,
                                        @NotNull java.util.function.Function<T, String> languageOf,
                                        @NotNull String groupName) {

        if (renditions.isEmpty()) {
            return -1;
        }

        if (requestedLanguage == null || requestedLanguage.isBlank()) {
            return 0;
        }

        for (int i = 0; i < renditions.size(); i++) {
            if (requestedLanguage.equalsIgnoreCase(languageOf.apply(renditions.get(i)))) {
                return i;
            }
        }

        log.warn("Requested default {} language '{}' matches no rendition;"
                        + " falling back to the first one so that the group still has DEFAULT=YES",
                groupName, requestedLanguage);

        return 0;

    }

    /**
     * Экранирует значение, которое подставляется внутрь двойных кавычек атрибута.
     *
     * <p>Двойная кавычка заменяется апострофом. Экранирование обязательно для всех
     * значений из метаданных, а не только для NAME: язык дорожки и отображаемое
     * имя берутся из контейнера, то есть из данных, которыми библиотека не
     * управляет. Неэкранированная кавычка не просто ломает парсинг строки — она
     * позволяет метаданным добавить в тег собственные атрибуты, например
     * {@code rus",DEFAULT="YES}, и переопределить поведение плеера.
     *
     * <p>Прежнее {@code escape} применялось только к NAME, хотя LANGUAGE и URI
     * подставлялись в кавычки точно так же. Частичное экранирование хуже его
     * отсутствия: оно создаёт уверенность, что значения из метаданных безопасны.
     *
     * @param value исходное значение, может быть null
     * @return значение, пригодное для подстановки в кавычки атрибута
     */
    private @NotNull String quoted(String value) {

        return value == null ? "" : value.replace('"', '\'');

    }
}
