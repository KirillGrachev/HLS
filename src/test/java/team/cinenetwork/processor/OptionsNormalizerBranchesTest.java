package team.cinenetwork.processor;

import org.junit.jupiter.api.Test;
import team.cinenetwork.model.VideoInfo;
import team.cinenetwork.model.VideoStream;
import team.cinenetwork.options.AppOptions;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
    void emptyLadderFallsBackToDefaultMp4Bitrate() {

        AppOptions options = base();
        options.setVideoWidths(List.of());

        EncodingProfile profile = new OptionsNormalizer().normalizeEncodingOptions(options, videoInfo(8000));

        assertEquals(3000, profile.mp4BitrateKbps());  // фолбэк при пустой лестнице

    }

    @Test
    void zeroRatioFallsBackTo16x9() {

        AppOptions options = base();
        options.setVideoWidths(List.of(1280));
        options.setVideoNames(List.of());
        options.setRatio("0:9");  // ноль в знаменателе → фолбэк 16:9 → автовариант 720p

        EncodingProfile profile = new OptionsNormalizer().normalizeEncodingOptions(options, videoInfo(8000));

        assertEquals("720p", profile.variants().getFirst().name());

    }

    /* ---------- Фикстуры ---------- */

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
