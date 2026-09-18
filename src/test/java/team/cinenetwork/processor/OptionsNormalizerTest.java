package team.cinenetwork.processor;

import org.junit.jupiter.api.Test;
import team.cinenetwork.model.VideoInfo;
import team.cinenetwork.model.VideoStream;
import team.cinenetwork.options.AppOptions;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Тесты нормализатора: срезка ступеней выше источника,
 * битрейт = min(источник, лестница) * quality-фактор, неизменяемость опций.
 */
class OptionsNormalizerTest {

    @Test
    void dropsRungsExceedingSourceAndComputesBitrates() {

        AppOptions options = new AppOptions();
        options.setOutput(Path.of("/tmp/out"));
        options.setVideoWidths(List.of(1920, 1280));
        options.setVideoBaseBitrates(List.of(10000, 7000));
        options.setVideoQualityFactors(List.of(1.0, 0.7));
        options.setVideoCodecs(List.of("libx264", "libx264"));
        options.setVideoProfiles(List.of("high@5.2", "high@5.1"));
        options.setVideoPresets(List.of("slow", "slow"));
        options.setVideoNames(List.of("1080p", "720p"));

        // Источник 720p, 5000 kbps: ступень 1920 обязана срезаться,
        // а битрейт ступени 1280 = min(5000, 7000) * 0.7 = 3500
        VideoInfo videoInfo = videoInfo720p();

        EncodingProfile profile = new OptionsNormalizer().normalizeEncodingOptions(options, videoInfo);

        assertEquals(1, profile.variants().size());

        EncodingProfile.Variant variant = profile.variants().getFirst();
        assertEquals(1280, variant.width());
        assertEquals(3500, variant.bitrateKbps());
        assertEquals("720p", variant.name());

    }

    @Test
    void doesNotMutateUserOptions() {

        AppOptions options = new AppOptions();
        options.setOutput(Path.of("/tmp/out"));
        options.setVideoWidths(List.of(1920, 1280));
        options.setVideoBaseBitrates(List.of(10000, 7000));

        new OptionsNormalizer().normalizeEncodingOptions(options, videoInfo720p());

        // Опции остаются «как попросил пользователь»: нормализатор работает на копиях
        assertEquals(List.of(1920, 1280), options.getVideoWidths());
        assertEquals(List.of(10000, 7000), options.getVideoBaseBitrates());

    }

    @Test
    void padsMissingNamesAutomatically() {

        AppOptions options = new AppOptions();
        options.setOutput(Path.of("/tmp/out"));
        options.setVideoWidths(List.of(1280));
        options.setVideoBaseBitrates(List.of(7000));
        options.setVideoNames(List.of());  // имен нет: ожидаем автовариант "720p" (16:9)

        VideoInfo videoInfo = videoInfo1080p();

        EncodingProfile profile = new OptionsNormalizer().normalizeEncodingOptions(options, videoInfo);

        assertEquals(1, profile.variants().size());
        assertEquals("720p", profile.variants().getFirst().name());
        assertTrue(profile.variants().getFirst().bitrateKbps() > 0);

    }

    @Test
    void emptyParallelListsFallBackToDefaults() {

        AppOptions options = new AppOptions();
        options.setOutput(Path.of("/tmp/out"));
        options.setVideoWidths(List.of(1280));
        options.setVideoBaseBitrates(List.of());   // пусто: битрейт ступени станет 0
        options.setVideoCodecs(List.of());         // пусто: libx264
        options.setVideoProfiles(List.of());       // пусто: high@4.0
        options.setVideoPresets(List.of());        // пусто: medium
        options.setVideoNames(List.of());          // пусто: автовариант "720p"

        EncodingProfile profile = new OptionsNormalizer().normalizeEncodingOptions(options, videoInfo1080p());

        assertEquals(1, profile.variants().size());
        EncodingProfile.Variant v = profile.variants().getFirst();
        assertEquals("libx264", v.codec());
        assertEquals("high@4.0", v.profileLevel());
        assertEquals("medium", v.preset());
        assertEquals("720p", v.name());
        assertEquals(0, v.bitrateKbps());

    }

    @Test
    void invalidRatioFallsBackTo16x9() {

        AppOptions options = new AppOptions();
        options.setOutput(Path.of("/tmp/out"));
        options.setVideoWidths(List.of(1280));
        options.setVideoNames(List.of());  // имён нет: автовариант по ratio
        options.setRatio("wide");  // мусор: автовариант всё равно "720p" (фолбэк 16:9)

        EncodingProfile profile = new OptionsNormalizer().normalizeEncodingOptions(options, videoInfo1080p());

        assertEquals("720p", profile.variants().getFirst().name());

    }

    @Test
    void zeroWidthConfigurationsAreDropped() {

        AppOptions options = new AppOptions();
        options.setOutput(Path.of("/tmp/out"));
        options.setVideoWidths(List.of(0, 1280));
        options.setVideoBaseBitrates(List.of(7000, 7000));
        options.setVideoNames(List.of("zero", "720p"));

        EncodingProfile profile = new OptionsNormalizer().normalizeEncodingOptions(options, videoInfo1080p());

        assertEquals(1, profile.variants().size());
        assertEquals("720p", profile.variants().getFirst().name());

    }

    @Test
    void explicitMp4AndPosterValuesAreRespected() {

        AppOptions options = new AppOptions();
        options.setOutput(Path.of("/tmp/out"));
        options.setMp4Width(640);
        options.setPosterWidth(320);
        options.setMp4Bitrate(1234);

        EncodingProfile profile = new OptionsNormalizer().normalizeEncodingOptions(options, videoInfo1080p());

        assertEquals(640, profile.mp4Width());
        assertEquals(320, profile.posterWidth());
        assertEquals(1234, profile.mp4BitrateKbps());  // явный битрейт умножен на factor=1.0

    }

    @Test
    void mp4WidthOutsideLadderFallsBackToMaxRung() {

        AppOptions options = new AppOptions();
        options.setOutput(Path.of("/tmp/out"));
        options.setVideoWidths(List.of(1920, 1280));
        options.setVideoBaseBitrates(List.of(8000, 5000));
        options.setMp4MaxWidth(1000);  // ни одна ступень не подходит -> ширина 1000, битрейт старшей ступени * 1.5

        EncodingProfile profile = new OptionsNormalizer().normalizeEncodingOptions(options, videoInfo1080p());

        assertEquals(1000, profile.mp4Width());
        assertEquals(12000, profile.mp4BitrateKbps());

    }

    /* ---------- Фикстуры ---------- */

    private VideoInfo videoInfo720p() {
        return videoInfo(1280, 720, "5000000");
    }

    private VideoInfo videoInfo1080p() {
        return videoInfo(1920, 1080, "8000000");
    }

    private VideoInfo videoInfo(int width, int height, String bitrate) {

        VideoInfo videoInfo = new VideoInfo();

        VideoStream video = new VideoStream();
        video.populateFromProbeData(Map.of(
                "codec_type", "video",
                "index", 0,
                "width", width,
                "height", height,
                "r_frame_rate", "24000/1001",
                "bit_rate", bitrate));
        videoInfo.setVideoStream(video);

        return videoInfo;

    }
}
