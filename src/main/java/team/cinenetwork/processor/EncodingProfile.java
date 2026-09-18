package team.cinenetwork.processor;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Неизменяемый план кодирования — результат работы {@code OptionsNormalizer}.
 *
 * <p>Зачем: CLI-опции приходят «параллельными списками» (ширины, битрейты, кодеки...),
 * которые могут быть разной длины и противоречить исходнику. Нормализатор один раз
 * строит неизменяемый профиль, и все потребители (стратегии, плейлист) читают единый
 * согласованный источник правды: junior видит в одном объекте ровно то, что будет закодировано.
 */
public final class EncodingProfile {

    /**
     * Одна ступень видео-лестницы.
     *
     * @param width        целевая ширина, px
     * @param bitrateKbps  итоговый битрейт видео (с учётом источника и quality-фактора)
     * @param codec        кодек видео FFmpeg ({@code libx264}, ...)
     * @param profileLevel профиль и уровень вида {@code high@5.2}
     * @param preset       пресет кодера ({@code slow}, ...)
     * @param name         имя варианта ({@code 1080p}): оно же — папка вывода и id в мастер-плейлисте
     */
    public record Variant(int width,
                          int bitrateKbps,
                          @NotNull String codec,
                          @NotNull String profileLevel,
                          @NotNull String preset,
                          @NotNull String name) {
    }

    /** Ступени лестницы в порядке пользователя (от старшей к младшей). */
    private final List<Variant> variants;

    /** Ширина постера, px. */
    private final int posterWidth;

    /** Ширина MP4 в single-file режиме, px. */
    private final int mp4Width;

    /** Битрейт MP4, kbps. */
    private final int mp4BitrateKbps;

    public EncodingProfile(@NotNull List<Variant> variants,
                           int posterWidth,
                           int mp4Width,
                           int mp4BitrateKbps) {

        this.variants = List.copyOf(variants);
        this.posterWidth = posterWidth;
        this.mp4Width = mp4Width;
        this.mp4BitrateKbps = mp4BitrateKbps;

    }

    /** Ступени лестницы (неизменяемый список). */
    public @NotNull List<Variant> variants() {
        return variants;
    }

    public int posterWidth() {
        return posterWidth;
    }

    public int mp4Width() {
        return mp4Width;
    }

    public int mp4BitrateKbps() {
        return mp4BitrateKbps;
    }

    /** Битрейт ступени заданной ширины (для расчётов MP4 и BANDWIDTH); нет такой — 0. */
    public int bitrateForWidth(int width) {

        return variants.stream()
                .filter(variant -> variant.width() == width)
                .map(Variant::bitrateKbps)
                .findFirst()
                .orElse(0);

    }
}
