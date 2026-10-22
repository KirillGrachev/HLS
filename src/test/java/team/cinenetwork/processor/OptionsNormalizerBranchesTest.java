package team.cinenetwork.processor;

import org.junit.jupiter.api.Test;
import team.cinenetwork.exception.ErrorCode;
import team.cinenetwork.exception.ProcessingException;
import team.cinenetwork.model.VideoInfo;
import team.cinenetwork.model.VideoStream;
import team.cinenetwork.options.AppOptions;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Покрытие веток нормализатора: дополнение/обрезка списков, множитель битрейта,
 * источник без битрейта, пустая лестница, ratio "0:9".
 */
class OptionsNormalizerBranchesTest {

    @Test
    void shortListsArePaddedWithLastElement() {

        AppOptions options = base();
        options.setVideoWidths(List.of(1920, 1280));
        options.setVideoBaseBitrates(List.of(8000));        // дополнится до [8000, 8000]
        options.setVideoCodecs(List.of("libx264"));
        options.setVideoProfiles(List.of("high@5.2"));
        options.setVideoPresets(List.of("slow"));
        options.setVideoNames(List.of("1080p", "720p"));
        options.setVideoQualityFactors(List.of(1.0, 0.5));

        EncodingProfile profile = new OptionsNormalizer().normalizeEncodingOptions(options, videoInfo(8000));

        assertEquals(2, profile.variants().size());
        assertEquals(8000, profile.variants().getFirst().bitrateKbps());
        assertEquals(4000, profile.variants().get(1).bitrateKbps());  // 8000 * 0.5

    }

    @Test
    void longListsAreTrimmedToWidthsSize() {

        AppOptions options = base();
        options.setVideoWidths(List.of(1280));
        options.setVideoBaseBitrates(List.of(8000, 5000, 1000));  // обрежется до [8000]
        options.setVideoNames(List.of("720p"));

        EncodingProfile profile = new OptionsNormalizer().normalizeEncodingOptions(options, videoInfo(8000));

        assertEquals(1, profile.variants().size());
        assertEquals(8000, profile.variants().getFirst().bitrateKbps());

    }

    @Test
    void globalBitrateFactorScalesLadder() {

        AppOptions options = base();
        options.setVideoWidths(List.of(1280));
        options.setVideoBaseBitrates(List.of(5000));
        options.setVideoNames(List.of("720p"));
        options.setVideoBitrateFactor(2.0);

        EncodingProfile profile = new OptionsNormalizer().normalizeEncodingOptions(options, videoInfo(0));

        // 5000 * 2.0 = 10000, источник неизвестен (0) → потолок не работает
        assertEquals(10000, profile.variants().getFirst().bitrateKbps());

    }

    @Test
    void sourceWithoutVideoStreamSkipsResolutionFilter() {

        AppOptions options = base();
        options.setVideoWidths(List.of(1920));
        options.setVideoBaseBitrates(List.of(8000));
        options.setVideoNames(List.of("1080p"));

        // видео-потока нет вовсе: фильтр по источнику выходит рано
        EncodingProfile profile = new OptionsNormalizer().normalizeEncodingOptions(options, new VideoInfo());

        assertEquals(1, profile.variants().size());

    }

    @Test
    void sourceWithoutBitrateDoesNotCapLadder() {

        AppOptions options = base();
        options.setVideoWidths(List.of(1280));
        options.setVideoBaseBitrates(List.of(5000));
        options.setVideoNames(List.of("720p"));

        EncodingProfile profile = new OptionsNormalizer().normalizeEncodingOptions(options, videoInfo(0));

        assertEquals(5000, profile.variants().getFirst().bitrateKbps());

    }

    @Test
    void emptyLadderProducesEmptyPlan() {

        AppOptions options = base();
        options.setVideoWidths(List.of());

        EncodingProfile profile = new OptionsNormalizer().normalizeEncodingOptions(options, videoInfo(8000));

        // лестницы нет: вариантов ноль, ширина постера — предел --poster-max-width
        assertTrue(profile.variants().isEmpty());
        assertEquals(options.getPosterMaxWidth(), profile.posterWidth());

    }

    @Test
    void zeroRatioFallsBackTo16x9() {

        AppOptions options = base();
        options.setVideoWidths(List.of(1280));
        options.setVideoBaseBitrates(List.of(5000));
        options.setVideoNames(List.of());
        options.setRatio("0:9");  // ноль в числителе → фолбэк 16:9 → автовариант 720p

        EncodingProfile profile = new OptionsNormalizer().normalizeEncodingOptions(options, videoInfo(8000));

        assertEquals("720p", profile.variants().getFirst().name());

    }

    @Test
    void zeroSourceDimensionsSkipResolutionFilter() {

        AppOptions options = base();
        options.setVideoWidths(List.of(3840, 1920));
        options.setVideoBaseBitrates(List.of(8000));
        options.setVideoNames(List.of("2160p", "1080p"));

        // ffprobe не отдал ширину: сравнивать ступени не с чем, фильтр выходит рано
        assertEquals(2, new OptionsNormalizer()
                .normalizeEncodingOptions(options, probeWithout("width")).variants().size());

        // ширина есть, высоты нет — тот же ранний выход
        assertEquals(2, new OptionsNormalizer()
                .normalizeEncodingOptions(options, probeWithout("height")).variants().size());

    }

    /** Источник 1920x1080, из probe-данных исключён один ключ геометрии. */
    private VideoInfo probeWithout(String key) {

        java.util.HashMap<String, Object> probe = new java.util.HashMap<>(Map.of(
                "codec_type", "video", "index", 0,
                "width", 1920, "height", 1080, "r_frame_rate", "24/1",
                "bit_rate", "8000000"));
        probe.remove(key);

        VideoInfo info = new VideoInfo();
        VideoStream video = new VideoStream();
        video.populateFromProbeData(probe);
        info.setVideoStream(video);
        return info;

    }

    @Test
    void nonPositiveWidthRungIsDroppedFromAllParallelLists() {

        AppOptions options = base();
        options.setVideoWidths(List.of(1920, 0));        // нулевая ступень бессмысленна
        options.setVideoBaseBitrates(List.of(8000, 5000));
        options.setVideoNames(List.of("1080p", "0p"));

        EncodingProfile profile = new OptionsNormalizer().normalizeEncodingOptions(options, videoInfo(8000));

        // удалена из всех параллельных списков синхронно: осталась одна ступень 1080p
        assertEquals(1, profile.variants().size());
        assertEquals("1080p", profile.variants().getFirst().name());
        assertEquals(8000, profile.variants().getFirst().bitrateKbps());

    }

    @Test
    void dotVariantNamesAreRejected() {

        AppOptions options = base();
        options.setVideoWidths(List.of(1280));
        options.setVideoBaseBitrates(List.of(5000));

        // "." и ".." формально состоят из разрешённых символов, но как имя папки
        // выводят запись за пределы video/ — отклоняются отдельно от паттерна
        options.setVideoNames(List.of("."));
        assertThrows(ProcessingException.class,
                () -> new OptionsNormalizer().normalizeEncodingOptions(options, videoInfo(8000)));

        options.setVideoNames(List.of(".."));
        ProcessingException e = assertThrows(ProcessingException.class,
                () -> new OptionsNormalizer().normalizeEncodingOptions(options, videoInfo(8000)));
        assertEquals(ErrorCode.INVALID_COMMAND_ARGUMENTS, e.getCode());

    }

    /**
     * Фикстуры.
     */

    private AppOptions base() {

        AppOptions options = new AppOptions();
        options.setOutput(Path.of("/tmp/out"));
        options.setVideoCodecs(List.of());
        options.setVideoProfiles(List.of());
        options.setVideoPresets(List.of());
        options.setVideoQualityFactors(List.of());
        options.setVideoNames(List.of());
        options.setVideoBaseBitrates(List.of());
        options.setVideoWidths(List.of());
        return options;

    }

    private VideoInfo videoInfo(int sourceBitrateKbps) {

        VideoInfo info = new VideoInfo();
        VideoStream video = new VideoStream();
        Map<String, Object> probe = new java.util.HashMap<>(Map.of(
                "codec_type", "video", "index", 0,
                "width", 1920, "height", 1080, "r_frame_rate", "24/1"));
        if (sourceBitrateKbps > 0) {
            probe.put("bit_rate", String.valueOf(sourceBitrateKbps * 1000));
        }
        video.populateFromProbeData(probe);
        info.setVideoStream(video);
        return info;

    }
}
