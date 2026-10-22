package team.cinenetwork.processor.impl.playlist;

import org.junit.jupiter.api.Test;
import team.cinenetwork.model.AudioStream;
import team.cinenetwork.model.SubtitleStream;
import team.cinenetwork.model.VideoInfo;
import team.cinenetwork.model.VideoStream;
import team.cinenetwork.options.AppOptions;
import team.cinenetwork.processor.EncodingProfile;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Соответствие мастер-плейлиста спецификации HLS и защита от метаданных.
 *
 * <p>Набор отдельный от {@code MasterPlaylistBuilderTest}, потому что проверяет не
 * «плейлист собрался», а свойства, нарушение которых не видно ни в логах, ни при
 * ручном просмотре, но ломает воспроизведение у части плееров:
 * <ul>
 *   <li>не более одного {@code DEFAULT=YES} в группе (требование RFC 8216);</li>
 *   <li>значения из метаданных не могут добавить в тег собственные атрибуты;</li>
 *   <li>наличие {@code #EXT-X-INDEPENDENT-SEGMENTS}.</li>
 * </ul>
 */
class MasterPlaylistSpecTest {

    @Test
    void declaresIndependentSegments() {

        String master = build(baseOptions(), videoInfoWithAudio("jpn", "rus"));

        // Сегменты режутся по ключевым кадрам, поэтому каждый декодируется
        // независимо. Без тега плеер обязан предполагать зависимости и не может
        // ни стартовать с произвольного сегмента, ни переключать вариант без
        // перебуферизации.
        assertTrue(master.contains("#EXT-X-INDEPENDENT-SEGMENTS"), master);
        assertTrue(master.contains("#EXT-X-VERSION:5"), master);

    }

    @Test
    void exactlyOneDefaultWhenTwoTracksShareRequestedLanguage() {

        AppOptions options = baseOptions();
        options.setAudioDefaultLanguage("rus");

        // Две дорожки одного языка — обычная ситуация для аниме: даб и войсовер.
        String master = build(options, videoInfoWithAudio("rus", "rus"));

        List<String> audioLines = mediaLines(master, "AUDIO");

        assertEquals(2, audioLines.size(), master);
        assertEquals(1, countDefaultYes(audioLines),
                "в группе может быть не более одного DEFAULT=YES: " + audioLines);

    }

    @Test
    void exactlyOneDefaultWhenRequestedLanguageIsUnknown() {

        AppOptions options = baseOptions();
        options.setAudioDefaultLanguage("deu");

        String master = build(options, videoInfoWithAudio("jpn", "rus"));

        List<String> audioLines = mediaLines(master, "AUDIO");

        // Ранее в этом случае DEFAULT=YES не получал никто, и выбор падал на
        // плеер: результат зависел от клиента, а не от настройки.
        assertEquals(1, countDefaultYes(audioLines),
                "группа должна иметь ровно один DEFAULT=YES: " + audioLines);
        assertTrue(audioLines.getFirst().contains(",DEFAULT=YES"),
                "дефолтом должна стать первая дорожка: " + audioLines);

    }

    @Test
    void defaultFollowsRequestedLanguage() {

        AppOptions options = baseOptions();
        options.setAudioDefaultLanguage("rus");

        List<String> audioLines = mediaLines(build(options, videoInfoWithAudio("jpn", "rus")), "AUDIO");

        assertFalse(audioLines.get(0).contains(",DEFAULT=YES"), audioLines.get(0));
        assertTrue(audioLines.get(1).contains(",DEFAULT=YES"), audioLines.get(1));

    }

    @Test
    void defaultIsFirstTrackWhenNoLanguageRequested() {

        List<String> audioLines = mediaLines(build(baseOptions(), videoInfoWithAudio("jpn", "rus")), "AUDIO");

        assertEquals(1, countDefaultYes(audioLines), audioLines.toString());
        assertTrue(audioLines.getFirst().contains(",DEFAULT=YES"), audioLines.getFirst());

    }

    @Test
    void subtitleGroupHasAtMostOneDefault() {

        AppOptions options = baseOptions();
        options.setSubsDefaultLanguage("rus");

        VideoInfo videoInfo = videoInfoWithAudio("jpn");
        videoInfo.getSubtitleStreams().add(subtitle(4, "rus"));
        videoInfo.getSubtitleStreams().add(subtitle(5, "rus"));

        List<String> subtitleLines = mediaLines(build(options, videoInfo), "SUBTITLES");

        assertFalse(subtitleLines.isEmpty(), "ожидалась группа субтитров");
        assertEquals(1, countDefaultYes(subtitleLines),
                "в группе субтитров не более одного DEFAULT=YES: " + subtitleLines);

    }

    @Test
    void languageFromMetadataCannotInjectAttributes() {

        // Язык берётся из контейнера, то есть из данных, которыми библиотека не
        // управляет. Неэкранированная кавычка позволяла метаданным добавить в тег
        // собственные атрибуты и переопределить поведение плеера.
        VideoInfo videoInfo = videoInfoWithAudio("jpn");
        videoInfo.getAudioStreams().add(audio(9, "rus\",DEFAULT=\"YES"));

        String master = build(baseOptions(), videoInfo);

        List<String> audioLines = mediaLines(master, "AUDIO");

        for (String line : audioLines) {
            // Внутри значения LANGUAGE не должно остаться ни одной кавычки:
            // инъекция обезврежена заменой на апостроф.
            String language = extractAttribute(line, "LANGUAGE");

            assertEquals(0, countUnescapedQuotes(language), line);
            assertFalse(language.contains("\""), "кавычка не экранирована: " + line);
            assertFalse(line.contains("DEFAULT=\"YES\""), line);
        }

        // Число DEFAULT=YES в группе не выросло из-за инъекции.
        assertTrue(countDefaultYes(audioLines) <= 1, master);

    }

    @Test
    void displayNameWithQuotesIsEscaped() {

        VideoInfo videoInfo = videoInfoWithAudio("jpn");
        videoInfo.getAudioStreams().add(audioTitled(10, "eng", "Director's \"cut\""));

        String master = build(baseOptions(), videoInfo);

        for (String line : mediaLines(master, "AUDIO")) {
            String name = extractAttribute(line, "NAME");

            assertFalse(name.contains("\""), "кавычка в NAME должна быть заменена: " + line);
            assertEquals(0, countUnescapedQuotes(name), line);

        }

    }

    @Test
    void everyAttributeIsProperlyQuoted() {

        String master = build(baseOptions(), videoInfoWithAudio("jpn", "rus"));

        // Инвариант для всей строки тега: кавычки встречаются парами, поэтому ни
        // одно значение не выходит за пределы своего атрибута.
        for (String line : master.lines().filter(l -> l.startsWith("#EXT-X-MEDIA")).toList()) {
            assertEquals(0, countUnbalancedQuotes(line), "несбалансированные кавычки: " + line);
        }

    }

    private static String build(AppOptions options, VideoInfo videoInfo) {
        return new MasterPlaylistBuilder(options, videoInfo, singleVariantProfile()).build().orElseThrow();
    }

    private static AppOptions baseOptions() {

        AppOptions options = new AppOptions();
        options.setInputOption(Path.of("source.mkv"));
        options.setOutput(Path.of("/tmp/out"));
        options.setRatio("16:9");
        options.setAudioBitrate(128);
        options.setAudioProfile("aac_low");

        return options;

    }

    private static VideoInfo videoInfoWithAudio(String... languages) {

        VideoInfo videoInfo = new VideoInfo();

        VideoStream video = new VideoStream();
        video.populateFromProbeData(Map.of(
                "codec_type", "video",
                "index", 0,
                "width", 1920,
                "height", 1080,
                "r_frame_rate", "24000/1001",
                "bit_rate", "8000000"));
        videoInfo.setVideoStream(video);

        int index = 1;

        for (String language : languages) {
            AudioStream stream = audio(index, language);
            videoInfo.getAudioStreams().add(stream);

            if (index == 1) {
                videoInfo.setAudioStream(stream);
            }

            index++;
        }

        return videoInfo;

    }

    private static AudioStream audio(int index, String language) {
        return audioTitled(index, language, "Track " + index);
    }
    private static AudioStream audioTitled(int index, String language, String title) {

        AudioStream stream = new AudioStream();
        stream.populateFromProbeData(Map.of(
                "codec_type", "audio",
                "index", index,
                "channels", 2,
                "tags", Map.of("language", language, "title", title)));

        return stream;

    }

    private static SubtitleStream subtitle(int index, String language) {

        SubtitleStream stream = new SubtitleStream();
        stream.populateFromProbeData(Map.of(
                "codec_type", "subtitle",
                "index", index,
                "codec_name", "ass",
                "tags", Map.of("language", language, "title", "Subs")));

        return stream;

    }

    private static EncodingProfile singleVariantProfile() {

        return new EncodingProfile(
                List.of(new EncodingProfile.Variant(1920, 8000, "libx264", "high@5.2", "slow", "1080p")),
                1920);
    }
    private static List<String> mediaLines(String master, String type) {

        return master.lines()
                .filter(line -> line.startsWith("#EXT-X-MEDIA:TYPE=" + type))
                .toList();

    }

    private static long countDefaultYes(List<String> lines) {
        return lines.stream().filter(line -> line.contains(",DEFAULT=YES")).count();
    }
    /**
     * Достаёт значение атрибута без окружающих кавычек.
     *
     * @param line строка тега
     * @param name имя атрибута
     * @return значение либо пустая строка, если атрибута нет
     */
    private static String extractAttribute(String line, String name) {

        int start = line.indexOf("," + name + "=\"");

        if (start < 0) {
            return "";
        }

        // +3: запятая, имя атрибута и открывающая последовательность '="'
        int valueStart = start + name.length() + 3;
        int end = line.indexOf("\",", valueStart);

        return end < 0 ? line.substring(valueStart) : line.substring(valueStart, end);

    }

    /** Считает кавычки, не входящие в удвоенную пару. */
    private static int countUnescapedQuotes(String value) {

        int count = 0;

        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) != '"') {
                continue;
            }

            if (i + 1 < value.length() && value.charAt(i + 1) == '"') {
                i++;
                continue;
            }

            count++;
        }

        return count;

    }

    /** Кавычки должны быть сбалансированы: чётное число, без экранирования удвоением. */
    private static int countUnbalancedQuotes(String line) {
        long quotes = line.chars().filter(c -> c == '"').count();
        return (int) (quotes % 2);
    }
}
