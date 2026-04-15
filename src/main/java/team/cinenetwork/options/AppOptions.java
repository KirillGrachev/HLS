package team.cinenetwork.options;

import lombok.Getter;
import lombok.Setter;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Command(name = "HLS", mixinStandardHelpOptions = true, version = "1.0",
        description = "Convert video to HLS format with adaptive streaming")
public class AppOptions {

    //Input/Output
    @Getter @Setter
    @Parameters(index = "0", description = "Input video file")
    private Path input;

    @Getter @Setter
    @Option(names = {"-o", "--output"}, description = "Output directory")
    private Path output;

    @Getter @Setter
    @Option(names = "--output-overwrite", description = "Overwrite output directory")
    private boolean outputOverwrite;

    //HLS Options
    @Getter @Setter
    @Option(names = "--hls-disable", negatable = true, defaultValue = "false",
            description = "Disable hls")
    private boolean hlsDisable;

    @Getter @Setter
    @Option(names = "--hls-type", defaultValue = "mpegts",
            description = "HLS segment type: ${COMPLETION-CANDIDATES}")
    private HlsType hlsType;

    @Getter @Setter
    @Option(names = "--hls-time", defaultValue = "6",
            description = "HLS segment duration (seconds)")
    private int hlsTime;

    @Getter @Setter
    @Option(names = "--hls-segments", defaultValue = "{index}",
            description = "HLS segment filename pattern")
    private String hlsSegments;

    @Getter @Setter
    @Option(names = "--hls-segment-prefix", defaultValue = "",
            description = "Segment prefix in playlists")
    private String hlsSegmentPrefix;

    @Option(names = "--hls-playlist-prefix",
            description = "Playlist URI prefixes")
    private List<String> hlsPlaylistPrefix = new ArrayList<>();

    @Getter @Setter
    @Option(names = "--hls-master-playlist", defaultValue = "master-playlist.m3u8",
            description = "Master playlist filename")
    private String hlsMasterPlaylist;

    @Getter @Setter
    @Option(names = "--hls-folder-structure", defaultValue = "true",
            description = "Organize files in folders")
    private boolean hlsFolderStructure;

    //Video Encoding
    @Option(names = "--video-widths", split = ",",
            defaultValue = "1920,1280,854,640,428",
            description = "Video widths (pixels)")
    private List<Integer> videoWidths = new ArrayList<>();

    @Option(names = "--video-bitrates", split = ",",
            defaultValue = "10000,7000,4000,2500,1000",
            description = "Video bitrates (kbps)")
    private List<Integer> videoBaseBitrates = new ArrayList<>();

    @Option(names = "--video-quality-factors", split = ",",
            defaultValue = "1.0,0.7,0.5,0.4,0.2",
            description = "Quality factors for each resolution")
    private List<Double> videoQualityFactors = new ArrayList<>();

    @Option(names = "--video-codecs", split = ",",
            defaultValue = "libx264,libx264,libx264,libx264,libx264",
            description = "Video codecs per variant")
    private List<String> videoCodecs = new ArrayList<>();

    @Option(names = "--video-profiles", split = ",",
            defaultValue = "high@5.2,high@5.2,high@5.1,high@4.2,high@4.0",
            description = "Encoding profiles")
    private List<String> videoProfiles = new ArrayList<>();

    @Option(names = "--video-names", split = ",",
            defaultValue = "1080p,720p,480p,360p,240p",
            description = "Variant names")
    private List<String> videoNames = new ArrayList<>();

    @Getter @Setter
    @Option(names = "--video-bitrate-factor", defaultValue = "1.0",
            description = "Bitrate multiplier")
    private double videoBitrateFactor;

    @Option(names = "--video-presets", split = ",",
            description = "Encoding presets",
            defaultValue = "slow,slow,slow,slow,slow")
    private List<String> videoPresets = new ArrayList<>();

    //Audio
    @Getter @Setter
    @Option(names = "--audio-disable", negatable = true, defaultValue = "false",
            description = "Disable audio track")
    private boolean audioDisable;

    @Getter @Setter
    @Option(names = "--audio-sampling",
            description = "Sampling rate (Hz)")
    private Integer audioSampling;

    @Getter @Setter
    @Option(names = "--audio-bitrate", defaultValue = "128",
            description = "Audio bitrate (kbps)")
    private int audioBitrate;

    @Getter @Setter
    @Option(names = "--audio-codec", defaultValue = "aac",
            description = "Audio codec")
    private String audioCodec;

    @Getter @Setter
    @Option(names = "--audio-profile", defaultValue = "aac_low",
            description = "Audio profile")
    private String audioProfile;

    @Getter @Setter
    @Option(names = "--audio-only",
            description = "Audio-only mode")
    private boolean audioOnly;

    @Getter @Setter
    @Option(names = "--no-audio",
            description = "Disable audio track")
    private boolean noAudio;

    @Getter @Setter
    @Option(names = "--audio-stream",
            description = "Audio stream selector (index:N)")
    private String audioStream;

    //MP4
    @Getter @Setter
    @Option(names = "--mp4-width",
            description = "MP4 width (pixels)")
    private Integer mp4Width;

    @Getter @Setter
    @Option(names = "--mp4-max-width", defaultValue = "1920",
            description = "Max MP4 width")
    private int mp4MaxWidth;

    @Getter @Setter
    @Option(names = "--mp4-bitrate-factor", defaultValue = "1.5",
            description = "MP4 bitrate multiplier")
    private double mp4BitrateFactor;

    @Getter @Setter
    @Option(names = "--mp4-bitrate",
            description = "MP4 bitrate (kbps)")
    private Integer mp4Bitrate;

    @Getter @Setter
    @Option(names = "--mp4-codec", defaultValue = "h264",
            description = "MP4 codec")
    private String mp4Codec;

    @Getter @Setter
    @Option(names = "--mp4-profile", defaultValue = "high@5.2",
            description = "MP4 profile")
    private String mp4Profile;

    //Poster
    @Getter @Setter
    @Option(names = "--poster-enabled", negatable = true, defaultValue = "true",
            description = "Generate poster")
    private boolean posterEnabled;

    @Getter @Setter
    @Option(names = "--poster-filename", defaultValue = "preview.jpg",
            description = "Poster filename")
    private String posterFilename;

    @Getter @Setter
    @Option(names = "--poster-seek", defaultValue = "45%",
            description = "Seek position")
    private String posterSeek;

    @Getter @Setter
    @Option(names = "--poster-width",
            description = "Poster width (pixels)")
    private Integer posterWidth;

    @Getter @Setter
    @Option(names = "--poster-max-width", defaultValue = "1920",
            description = "Max poster width")
    private int posterMaxWidth;

    //Playlist
    @Getter @Setter
    @Option(names = "--playlist-disabled", negatable = true, defaultValue = "false",
            description = "Disable creating playlist")
    private boolean playlistDisable;

    //Tools
    @Getter @Setter
    @Option(names = "--ffmpeg", defaultValue = "ffmpeg",
            description = "FFmpeg executable")
    private String ffmpeg;

    @Getter @Setter
    @Option(names = "--ffprobe", defaultValue = "ffprobe",
            description = "FFprobe executable")
    private String ffprobe;

    @Getter @Setter
    @Option(names = "--mp4file", defaultValue = "mp4file",
            description = "MP4file executable")
    private String mp4file;

    @Getter @Setter
    @Option(names = "--ratio", defaultValue = "16:9",
            description = "Aspect ratio")
    private String ratio;

    public enum HlsType {
        mpegts
    }

    //Getters/Setters for Lists
    public List<String> getHlsPlaylistPrefix() {
        return new ArrayList<>(hlsPlaylistPrefix);
    }

    public void setHlsPlaylistPrefix(List<String> hlsPlaylistPrefix) {
        this.hlsPlaylistPrefix = hlsPlaylistPrefix != null
                ? new ArrayList<>(hlsPlaylistPrefix)
                : new ArrayList<>();
    }

    public List<Integer> getVideoWidths() {
        return new ArrayList<>(videoWidths);
    }

    public void setVideoWidths(List<Integer> videoWidths) {
        this.videoWidths = videoWidths != null
                ? new ArrayList<>(videoWidths)
                : new ArrayList<>();
    }

    public List<Integer> getVideoBaseBitrates() {
        return new ArrayList<>(videoBaseBitrates);
    }

    public List<Double> getVideoQualityFactors() {
        return new ArrayList<>(videoQualityFactors);
    }

    public void setVideoBaseBitrates(List<Integer> videoBaseBitrates) {
        this.videoBaseBitrates = videoBaseBitrates != null
                ? new ArrayList<>(videoBaseBitrates)
                : new ArrayList<>();
    }

    public List<String> getVideoCodecs() {
        return new ArrayList<>(videoCodecs);
    }

    public void setVideoCodecs(List<String> videoCodecs) {
        this.videoCodecs = videoCodecs != null
                ? new ArrayList<>(videoCodecs)
                : new ArrayList<>();
    }

    public List<String> getVideoProfiles() {
        return new ArrayList<>(videoProfiles);
    }

    public void setVideoProfiles(List<String> videoProfiles) {
        this.videoProfiles = videoProfiles != null
                ? new ArrayList<>(videoProfiles)
                : new ArrayList<>();
    }

    public List<String> getVideoNames() {
        return new ArrayList<>(videoNames);
    }

    public void setVideoNames(List<String> videoNames) {
        this.videoNames = videoNames != null
                ? new ArrayList<>(videoNames)
                : new ArrayList<>();
    }

    public List<String> getVideoPresets() {
        return new ArrayList<>(videoPresets);
    }

    public void setVideoPresets(List<String> videoPresets) {
        this.videoPresets = videoPresets != null
                ? new ArrayList<>(videoPresets)
                : new ArrayList<>();
    }
}