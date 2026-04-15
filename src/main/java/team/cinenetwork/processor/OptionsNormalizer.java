package team.cinenetwork.processor;

import lombok.extern.slf4j.Slf4j;

import org.jetbrains.annotations.NotNull;
import team.cinenetwork.ffmpeg.exceptions.ErrorCode;
import team.cinenetwork.model.VideoInfo;
import team.cinenetwork.model.VideoStream;
import team.cinenetwork.options.AppOptions;

import java.util.List;
import java.util.ListIterator;
import java.util.Optional;

@Slf4j
public class OptionsNormalizer {

    private static final double SIZE_TOLERANCE = 1.1;
    private static final int FALLBACK_BITRATE = 3000;

    public void normalizeEncodingOptions(AppOptions options,
                                         VideoInfo videoInfo) {

        adjustAudioRelatedOptions(options);
        alignVideoOptionListsLengths(options);
        applyBitrateScalingFactors(options);

        filterSupportedResolutions(options, videoInfo);

        setDefaultAssetDimensions(options);
        computeMp4BitrateIfMissing(options);

    }

    private void computeMp4BitrateIfMissing(@NotNull AppOptions options) {

        if (options.getMp4Bitrate() != null) return;

        try {

            int targetWidth = determineTargetWidth(options);
            int computedBitrate = calculateMp4Bitrate(options, targetWidth);

            options.setMp4Bitrate(computedBitrate);
            log.info("Computed MP4 bitrate: {} kbps (based on {}p variant)", computedBitrate, targetWidth);

        } catch (Exception e) {

            log.warn("MP4 bitrate calculation failed, using fallback. Reason: {}", e.getMessage());
            options.setMp4Bitrate(FALLBACK_BITRATE);

        }
    }

    private int determineTargetWidth(@NotNull AppOptions options) {
        return Optional.ofNullable(options.getMp4Width())
                .orElseGet(() -> findClosestValidWidth(
                        options.getVideoWidths(),
                        options.getMp4MaxWidth()
                ));
    }

    private int calculateMp4Bitrate(@NotNull AppOptions options, int targetWidth) {

        int baseIndex = options.getVideoWidths().indexOf(targetWidth);
        if (baseIndex == -1 || baseIndex >= options.getVideoBaseBitrates().size()) {
            throw team.cinenetwork.ffmpeg.exceptions.Exception.of(ErrorCode.NO_MATCHING_BITRATE,
                    "No matching bitrate for width " + targetWidth);
        }

        return (int) (options.getVideoBaseBitrates().get(baseIndex)
                * options.getMp4BitrateFactor());

    }

    private void adjustAudioRelatedOptions(@NotNull AppOptions options) {
        if (options.isAudioOnly() || options.isNoAudio()) {
            options.getVideoWidths().add(0);
        }
    }

    private void alignVideoOptionListsLengths(@NotNull AppOptions options) {

        int targetSize = options.getVideoWidths().size();

        normalizeListLength(options.getVideoBaseBitrates(), targetSize);
        normalizeListLength(options.getVideoCodecs(), targetSize);
        normalizeListLength(options.getVideoProfiles(), targetSize);
        normalizeListLength(options.getVideoPresets(), targetSize);

    }

    private <T> void normalizeListLength(@NotNull List<T> list,
                                         int targetLength) {

        if (list.isEmpty()) return;

        T lastElement = list.getLast();
        while (list.size() < targetLength) {
            list.add(lastElement);
        }

        if (list.size() > targetLength) {
            list.subList(targetLength, list.size()).clear();
        }

    }

    private void applyBitrateScalingFactors(@NotNull AppOptions options) {

        scaleBitrateList(options.getVideoBaseBitrates(), options.getVideoBitrateFactor());
        Optional.ofNullable(options.getMp4Bitrate())
                .ifPresent(bitrate -> options.setMp4Bitrate((int)(bitrate
                        * options.getVideoBitrateFactor())));

    }

    private void scaleBitrateList(@NotNull List<Integer> bitrates,
                                  double factor) {

        ListIterator<Integer> iterator = bitrates.listIterator();

        while (iterator.hasNext()) {

            int original = iterator.next();
            iterator.set((int) (original * factor));

        }

    }

    private void filterSupportedResolutions(@NotNull AppOptions options,
                                            @NotNull VideoInfo videoInfo) {

        VideoStream video = videoInfo.getVideoStream();
        double aspectRatio = video.getPixelAspectRatio();

        for (int i = options.getVideoWidths().size() - 1; i >= 0; i--) {

            int targetWidth = options.getVideoWidths().get(i);
            if (targetWidth == 0) continue;

            if (isResolutionExceedingSource(targetWidth,
                    aspectRatio, video))
                removeVideoConfiguration(options, i);

        }

    }

    private boolean isResolutionExceedingSource(int width,
                                                double aspectRatio,
                                                @NotNull VideoStream video) {

        double effectiveWidth = width * aspectRatio;
        double scaledHeight = (effectiveWidth / video.getFrameWidth())
                * video.getFrameHeight();

        return effectiveWidth > video.getFrameWidth() * SIZE_TOLERANCE
                || scaledHeight > video.getFrameHeight() * SIZE_TOLERANCE;

    }

    private void removeVideoConfiguration(@NotNull AppOptions options,
                                          int index) {

        List<List<?>> configLists = List.of(
                options.getVideoWidths(),
                options.getVideoBaseBitrates(),
                options.getVideoCodecs(),
                options.getVideoProfiles(),
                options.getVideoPresets()
        );

        configLists.forEach(list -> {
            if (list.size() > index) list.remove(index);
        });

    }

    private void setDefaultAssetDimensions(@NotNull AppOptions options) {

        options.setPosterWidth(Optional.ofNullable(options.getPosterWidth())
                .orElseGet(() -> findClosestValidWidth(
                        options.getVideoWidths(),
                        options.getPosterMaxWidth()
                )));

        options.setMp4Width(Optional.ofNullable(options.getMp4Width())
                .orElseGet(() -> findClosestValidWidth(
                        options.getVideoWidths(),
                        options.getMp4MaxWidth()
                )));

    }

    private int findClosestValidWidth(@NotNull List<Integer> widths, int maxWidth) {
        return widths.stream()
                .filter(width -> width > 0 && width <= maxWidth)
                .max(Integer::compare)
                .orElse(maxWidth);
    }
}