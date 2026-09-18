package team.cinenetwork.model.probe;

import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Тонкая типизированная обёртка над сырой map из ffprobe (JSON, разобранный Jackson).
 *
 * <p>Зачем: поля ffprobe приходят «кто во что горазд» — число может прийти и
 * {@code Integer}, и {@code Long}, и строкой ({@code "duration": "23.900000"}).
 * Раньше у каждой модели была своя копия extractString/extractInteger с немного
 * разным поведением; теперь чтение probe-данных живёт в одном месте.
 *
 * <p>Все методы «безопасные»: отсутствующий ключ или мусор вроде {@code "N/A"}
 * дают пустой Optional вместо исключения.
 */
public final class ProbeData {

    /** Пустая обёртка-заглушка: для отсутствующих секций (tags, disposition). */
    private static final ProbeData EMPTY = new ProbeData(Map.of());

    /** Сырая map из ffprobe. */
    private final Map<String, Object> raw;

    private ProbeData(Map<String, Object> raw) {
        this.raw = raw;
    }

    /** Оборачивает map; {@code null} превращается в пустую (безопасную) обёртку. */
    public static @NotNull ProbeData of(Map<String, Object> raw) {
        return raw == null ? EMPTY : new ProbeData(raw);
    }

    /** Пустая обёртка — для отсутствующих секций. */
    public static @NotNull ProbeData empty() {
        return EMPTY;
    }

    /** Сырое значение по ключу. */
    public Optional<Object> value(@NotNull String key) {
        return Optional.ofNullable(raw.get(key));
    }

    /** Любое значение строкой (числа, дроби вида "24000/1001"); пустые строки отфильтрованы. */
    public Optional<String> text(@NotNull String key) {

        return value(key)
                .map(Object::toString)
                .filter(s -> !s.isBlank());

    }

    /**
     * Значение как int: понимает и JSON-числа, и строки ("2", "1920").
     * Мусор вроде "N/A" даёт пустой Optional вместо исключения.
     */
    public OptionalInt integer(@NotNull String key) {

        Optional<Object> value = value(key);
        if (value.isEmpty()) {
            return OptionalInt.empty();
        }

        Object rawValue = value.get();
        if (rawValue instanceof Number number) {
            return OptionalInt.of(number.intValue());
        }

        try {

            return OptionalInt.of(Integer.parseInt(rawValue.toString().trim()));

        } catch (NumberFormatException e) {
            return OptionalInt.empty();
        }

    }

    /** Значение как double: понимает числа и строки ("23.900000", запятая нормализуется в точку). */
    public OptionalDouble decimal(@NotNull String key) {

        Optional<Object> value = value(key);
        if (value.isEmpty()) {
            return OptionalDouble.empty();
        }

        Object rawValue = value.get();
        if (rawValue instanceof Number number) {
            return OptionalDouble.of(number.doubleValue());
        }

        try {

            String normalized = rawValue.toString().replace(',', '.').trim();
            return OptionalDouble.of(Double.parseDouble(normalized));

        } catch (NumberFormatException e) {
            return OptionalDouble.empty();
        }

    }

    /**
     * Вложенный объект ({@code tags}, {@code disposition}): если ключа нет или это
     * не map — вернётся пустая обёртка, цепочка вызовов никогда не бросает NPE.
     */
    @SuppressWarnings("unchecked")
    public @NotNull ProbeData nested(@NotNull String key) {

        return value(key)
                .filter(Map.class::isInstance)
                .map(map -> ProbeData.of((Map<String, Object>) map))
                .orElse(EMPTY);

    }

    /** Является ли поток указанным типом ({@code codec_type == "video"/"audio"/"subtitle"}). */
    public boolean isCodecType(@NotNull String type) {

        return text("codec_type")
                .map(String::toLowerCase)
                .map(type::equals)
                .orElse(false);

    }

    /** Язык из {@code tags.language}, например "jpn", "rus". */
    public Optional<String> languageTag() {
        return nested("tags").text("language");
    }

    /** Тайтл из {@code tags.title} — у аниме это часто "Russian dub" / "Japanese". */
    public Optional<String> titleTag() {
        return nested("tags").text("title");
    }

    /** Исходная map (по соглашению — только для чтения). */
    public Map<String, Object> asMap() {
        return raw;
    }
}
