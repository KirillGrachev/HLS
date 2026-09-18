package team.cinenetwork.processor.impl.playlist;

import java.util.Optional;

/**
 * Кодековые теги RFC 6381 для мастер-плейлиста ({@code CODECS="..."}).
 *
 * <p>Зачем: без CODECS плеер (особенно Safari и TV-приложения) вынужден «пробовать»
 * каждый вариант по сети перед переключением качества; с явными тегами переключение
 * происходит мгновенно.
 *
 * <p>Если кодек неизвестен (например libx265 с нестандартным профилем), тег просто
 * не пишется — это лучше, чем писать неверный.
 */
public final class CodecTags {

    private CodecTags() {
    }

    /**
     * Видео-тег H.264: {@code avc1.PPCCLL}, где PP — байт профиля, CC — байт ограничений,
     * LL — байт уровня. Пример: {@code high@5.2 -> avc1.640034}.
     *
     * @param ffmpegCodec  имя кодека FFmpeg ({@code libx264}/{@code h264})
     * @param profileLevel строка профиля вида {@code high@5.2}
     * @return тег, если кодек и профиль распознаны
     */
    public static Optional<String> videoCodecTag(String ffmpegCodec, String profileLevel) {

        if (ffmpegCodec == null || (!ffmpegCodec.equals("libx264")
                && !ffmpegCodec.equals("h264"))) {
            return Optional.empty();
        }

        String[] parts = profileLevel.split("@");

        int profileByte = switch (parts[0]) {
            case "baseline" -> 0x42;
            case "main" -> 0x4D;
            case "high" -> 0x64;

            default -> -1;
        };

        if (profileByte < 0 || parts.length < 2) {
            return Optional.empty();
        }

        try {

            int levelByte = (int) Math.round(Double.parseDouble(parts[1]) * 10);

            return Optional.of(String.format("avc1.%02x00%02x", profileByte, levelByte));

        } catch (NumberFormatException e) {
            return Optional.empty();
        }

    }

    /**
     * Аудио-тег профилей семейства AAC:
     * {@code aac_low -> mp4a.40.2}, {@code aac_he -> mp4a.40.5}, {@code aac_he_v2 -> mp4a.40.29}.
     *
     * @param aacProfile имя профиля из {@code --audio-profile}
     * @return тег, если профиль распознан
     */
    public static Optional<String> audioCodecTag(String aacProfile) {

        if (aacProfile == null) {
            return Optional.empty();
        }

        return switch (aacProfile) {
            case "aac_low" -> Optional.of("mp4a.40.2");
            case "aac_he" -> Optional.of("mp4a.40.5");
            case "aac_he_v2" -> Optional.of("mp4a.40.29");

            default -> Optional.empty();
        };

    }
}
