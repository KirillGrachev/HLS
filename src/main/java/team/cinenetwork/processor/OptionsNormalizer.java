package team.cinenetwork.processor;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import team.cinenetwork.model.VideoInfo;
import team.cinenetwork.model.VideoStream;
import team.cinenetwork.options.AppOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.stream.Collectors;

/**
 * Нормализация сырых CLI-опций в неизменяемый {@link EncodingProfile}.
 *
 * <p>Шаги нормализации строго упорядочены: убрать ступени с некорректной шириной →
 * выровнять длины параллельных списков → применить глобальный множитель битрейта →
 * убрать ступени выше источника → посчитать итоговый битрейт каждой ступени →
 * подобрать ширины постера/MP4 и битрейт MP4.
 *
 * <p>Важно: класс не мутирует {@link AppOptions}. Опции — «что попросил пользователь»,
 * профиль — «что реально будет закодировано»; все мутации происходят только на
 * локальных копиях списков.
 */
@Slf4j
public class OptionsNormalizer {

    /** Допуск по размерам источника: ступень до 10% выше источника ещё допустима. */
    private static final double SIZE_TOLERANCE = 1.1;

    /** Фолбэк-битрейт, если расчёт невозможен (пустая лестница). */
    private static final int FALLBACK_BITRATE_KBPS = 3000;

    /* Дефолты на случай пустых списков от пользователя: пайплайн обязан остаться рабочим. */
    private static final String DEFAULT_CODEC = "libx264";
    private static final String DEFAULT_PROFILE = "high@4.0";
    private static final String DEFAULT_PRESET = "medium";

    /**
     * Собирает план кодирования из опций и метаданных источника.
     *
     * @param options   сырые опции CLI (не изменяются)
     * @param videoInfo метаданные источника (геометрия, битрейт)
     * @return неизменяемый план кодирования
     */
    public @NotNull EncodingProfile normalizeEncodingOptions(@NotNull AppOptions options,
                                                             @NotNull VideoInfo videoInfo) {

        // Рабочие копии параллельных списков: мутируем только их и только локально.
        List<Integer> widths = options.getVideoWidths();
        List<Integer> bitrates = options.getVideoBaseBitrates();
        List<Double> qualityFactors = options.getVideoQualityFactors();
        List<String> codecs = options.getVideoCodecs();
        List<String> profiles = options.getVideoProfiles();
        List<String> presets = options.getVideoPresets();
        List<String> names = options.getVideoNames();

        dropInvalidWidths(widths, bitrates, codecs, profiles, presets, names, qualityFactors);
        alignListLengths(options, widths, bitrates, qualityFactors, codecs, profiles, presets, names);
        applyBitrateScalingFactor(options, bitrates);
        dropRungsExceedingSource(videoInfo, widths, bitrates, qualityFactors,
                codecs, profiles, presets, names);

        List<EncodingProfile.Variant> variants = buildVariants(videoInfo, widths, bitrates,
                qualityFactors, codecs, profiles, presets, names);

        int posterWidth = valueOrDefault(options.getPosterWidth(),
                () -> findClosestValidWidth(widths, options.getPosterMaxWidth()));
        int mp4Width = valueOrDefault(options.getMp4Width(),
                () -> findClosestValidWidth(widths, options.getMp4MaxWidth()));
        int mp4Bitrate = computeMp4Bitrate(options, variants, mp4Width);

        EncodingProfile profile = new EncodingProfile(variants, posterWidth, mp4Width, mp4Bitrate);
        logProfile(profile);

        return profile;

    }

    /* ---------- Шаг 1: убрать некорректные ступени (width <= 0) ---------- */

    private void dropInvalidWidths(List<Integer> widths,
                                   List<Integer> bitrates,
                                   List<String> codecs,
                                   List<String> profiles,
                                   List<String> presets,
                                   List<String> names,
                                   List<Double> qualityFactors) {

        for (int i = widths.size() - 1; i >= 0; i--) {

            if (widths.get(i) <= 0) {
                log.warn("Dropping video configuration with non-positive width at index {}", i);
                removeAt(i, widths, bitrates, codecs, profiles, presets, names, qualityFactors);
            }

        }

    }

    /* ---------- Шаг 2: выровнять длины списков ---------- */

    /**
     * Все списки становятся длиной с {@code widths}: недостающие значения заполняются
     * последним элементом (quality-факторы — 1.0, имена — автовидом {@code 720p}),
     * лишние — обрезаются.
     */
    private void alignListLengths(AppOptions options,
                                  List<Integer> widths,
                                  List<Integer> bitrates,
                                  List<Double> qualityFactors,
                                  List<String> codecs,
                                  List<String> profiles,
                                  List<String> presets,
                                  List<String> names) {

        int targetSize = widths.size();

        padOrTrim(bitrates, targetSize, 0, list -> list.getLast());
        padOrTrim(codecs, targetSize, DEFAULT_CODEC, list -> list.getLast());
        padOrTrim(profiles, targetSize, DEFAULT_PROFILE, list -> list.getLast());
        padOrTrim(presets, targetSize, DEFAULT_PRESET, list -> list.getLast());
        padOrTrim(qualityFactors, targetSize, 1.0, list -> list.getLast());

        for (int i = names.size(); i < targetSize; i++) {
            names.add(defaultNameForWidth(widths.get(i), options.getRatio()));
        }

        padOrTrim(names, targetSize, "variant", list -> list.getLast());

    }

    /**
     * Дополняет список до targetSize (значением из {@code filler}, а для пустого —
     * {@code emptyDefault}) либо обрезает до targetSize.
     */
    private <T> void padOrTrim(List<T> list,
                               int targetSize,
                               T emptyDefault,
                               Function<List<T>, T> filler) {

        while (list.size() < targetSize) {
            list.add(list.isEmpty() ? emptyDefault : filler.apply(list));
        }

        if (list.size() > targetSize) {
            list.subList(targetSize, list.size()).clear();
        }

    }

    /** Автоимя ступени, если имён меньше, чем ширин: {@code 720p}. */
    private @NotNull String defaultNameForWidth(int width, String ratio) {
        return Math.round(width / parseRatioSafely(ratio)) + "p";
    }

    /** Безопасный разбор "16:9"; мусор → дефолт 16:9. */
    private double parseRatioSafely(String ratio) {

        try {

            String[] parts = ratio.split(":");
            double value = Double.parseDouble(parts[0]) / Double.parseDouble(parts[1]);

            return value > 0 ? value : 16.0 / 9.0;

        } catch (RuntimeException e) {
            log.warn("Invalid ratio '{}', falling back to 16:9", ratio);
            return 16.0 / 9.0;
        }

    }

    /* ---------- Шаг 3: глобальный множитель битрейта ---------- */

    private void applyBitrateScalingFactor(AppOptions options, List<Integer> bitrates) {

        double factor = options.getVideoBitrateFactor();
        if (factor == 1.0) {
            return;
        }

        for (int i = 0; i < bitrates.size(); i++) {
            bitrates.set(i, (int) (bitrates.get(i) * factor));
        }

    }

    /* ---------- Шаг 4: срезать ступени выше источника ---------- */

    private void dropRungsExceedingSource(VideoInfo videoInfo,
                                          List<Integer> widths,
                                          List<Integer> bitrates,
                                          List<Double> qualityFactors,
                                          List<String> codecs,
                                          List<String> profiles,
                                          List<String> presets,
                                          List<String> names) {

        VideoStream video = videoInfo.getVideoStream();
        if (video == null || video.getFrameWidth() == 0 || video.getFrameHeight() == 0) {
            return;
        }

        for (int i = widths.size() - 1; i >= 0; i--) {

            if (isResolutionExceedingSource(widths.get(i), video)) {
                log.info("Dropping {}p rung: exceeds source resolution {}x{}",
                        widths.get(i), video.getFrameWidth(), video.getFrameHeight());
                removeAt(i, widths, bitrates, codecs, profiles, presets, names, qualityFactors);
            }

        }

    }

    /**
     * Ступень «выше источника», если с учётом пиксельного аспекта (SAR) её эффективная
     * ширина или масштабированная высота превышают источник более чем на {@link #SIZE_TOLERANCE}.
     */
    private boolean isResolutionExceedingSource(int width, @NotNull VideoStream video) {

        double effectiveWidth = width * video.getPixelAspectRatio();
        double scaledHeight = (effectiveWidth / video.getFrameWidth()) * video.getFrameHeight();

        return effectiveWidth > video.getFrameWidth() * SIZE_TOLERANCE
                || scaledHeight > video.getFrameHeight() * SIZE_TOLERANCE;

    }

    /** Синхронно удаляет элемент индекса из всех параллельных списков. */
    private void removeAt(int index,
                          List<Integer> widths,
                          List<Integer> bitrates,
                          List<String> codecs,
                          List<String> profiles,
                          List<String> presets,
                          List<String> names,
                          List<Double> qualityFactors) {

        widths.remove(index);
        removeIfPresent(bitrates, index);
        removeIfPresent(codecs, index);
        removeIfPresent(profiles, index);
        removeIfPresent(presets, index);
        removeIfPresent(names, index);
        removeIfPresent(qualityFactors, index);

    }

    private static <T> void removeIfPresent(List<T> list, int index) {
        if (list.size() > index) {
            list.remove(index);
        }
    }

    /* ---------- Шаг 5: собрать варианты ---------- */

    /**
     * Итоговый битрейт ступени: {@code min(битрейт источника, битрейт лестницы) * quality-фактор}.
     * Битрейт источника ограничивает лестницу сверху: незачем гнать 10000 kbps,
     * если сам источник — 6000 kbps.
     */
    private @NotNull List<EncodingProfile.Variant> buildVariants(VideoInfo videoInfo,
                                                                 List<Integer> widths,
                                                                 List<Integer> bitrates,
                                                                 List<Double> qualityFactors,
                                                                 List<String> codecs,
                                                                 List<String> profiles,
                                                                 List<String> presets,
                                                                 List<String> names) {

        int sourceBitrate = videoInfo.getVideoStream() != null
                ? videoInfo.getVideoStream().getBitrateKbps()
                : 0;

        List<EncodingProfile.Variant> variants = new ArrayList<>();

        for (int i = 0; i < widths.size(); i++) {

            int ladderBitrate = bitrates.get(i);
            int cappedBitrate = sourceBitrate > 0
                    ? Math.min(sourceBitrate, ladderBitrate)
                    : ladderBitrate;
            int finalBitrate = (int) Math.round(cappedBitrate * qualityFactors.get(i));

            variants.add(new EncodingProfile.Variant(
                    widths.get(i),
                    finalBitrate,
                    codecs.get(i),
                    profiles.get(i),
                    presets.get(i),
                    names.get(i)));

        }

        return variants;

    }

    /* ---------- Шаг 6: битрейт MP4 ---------- */

    /** Явный {@code --mp4-bitrate} приоритетен; иначе берём ступень mp4Width с множителем. */
    private int computeMp4Bitrate(AppOptions options,
                                  List<EncodingProfile.Variant> variants,
                                  int mp4Width) {

        if (options.getMp4Bitrate() != null) {
            return (int) (options.getMp4Bitrate() * options.getVideoBitrateFactor());
        }

        if (variants.isEmpty()) {
            log.warn("MP4 bitrate calculation failed (empty ladder), using fallback {} kbps",
                    FALLBACK_BITRATE_KBPS);
            return FALLBACK_BITRATE_KBPS;
        }

        int baseBitrate = variants.stream()
                .filter(variant -> variant.width() == mp4Width)
                .map(EncodingProfile.Variant::bitrateKbps)
                .findFirst()
                .orElseGet(() -> variants.stream()
                        .map(EncodingProfile.Variant::bitrateKbps)
                        .max(Integer::compare)
                        .orElse(FALLBACK_BITRATE_KBPS));

        int computed = (int) (baseBitrate * options.getMp4BitrateFactor());
        log.info("Computed MP4 bitrate: {} kbps (based on {}p variant)", computed, mp4Width);

        return computed;

    }

    /* ---------- Вспомогательные ---------- */

    private int valueOrDefault(Integer explicitValue, IntSupplier defaultValue) {
        return explicitValue != null ? explicitValue : defaultValue.getAsInt();
    }

    /** Максимальная ширина из лестницы, не превышающая maxWidth. */
    private int findClosestValidWidth(List<Integer> widths, int maxWidth) {

        return widths.stream()
                .filter(width -> width > 0 && width <= maxWidth)
                .max(Integer::compare)
                .orElse(maxWidth);

    }

    /** Человекочитаемая сводка плана в лог. */
    private void logProfile(EncodingProfile profile) {

        log.info("Encoding profile ready: {} variant(s) [{}], poster {}p, mp4 {}p@{}k",
                profile.variants().size(),
                profile.variants().stream()
                        .map(v -> v.name() + "@" + v.bitrateKbps() + "k")
                        .collect(Collectors.joining(", ")),
                profile.posterWidth(),
                profile.mp4Width(),
                profile.mp4BitrateKbps());

    }
}
