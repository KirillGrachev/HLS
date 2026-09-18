package team.cinenetwork.options;

import lombok.Getter;
import lombok.Setter;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Все CLI-флаги приложения, сгруппированные по смыслу.
 *
 * <p>Соглашения, принятые при рефакторинге (единый и предсказуемый CLI):
 * <ul>
 *   <li>входной файл принимает две формы: {@code --input file.mkv} (как на продуктовой
 *       инфографике) и легаси-позиционную {@code HLS file.mkv};</li>
 *   <li>«булевы» флаги принимают явное значение: работают и {@code --poster-enabled},
 *       и {@code --poster-enabled true} (форма с баннера), и отрицание
 *       {@code --no-poster-enabled};</li>
 *   <li>списки перечисляются через запятую: {@code --video-widths 1920,1280,854};</li>
 *   <li>новые пользовательские флаги субтитров и мульти-аудио:
 *       {@code --subs-enabled}, {@code --subs-languages}, {@code --audio-languages}, ...</li>
 * </ul>
 *
 * <p>Все дефолты живут в инициализаторах полей: объект полностью валиден и после
 * парсинга picocli, и при программном создании (в тестах).
 *
 * <p>Важно: класс только <b>хранит</b> сырые значения командной строки. Нормализацией
 * (выравнивание списков, срезка ступеней лестницы, расчёт битрейтов) занимается
 * {@code OptionsNormalizer}, строящий неизменяемый {@code EncodingProfile}: опции
 * остаются «тем, что попросил пользователь», профиль — «тем, что реально пойдёт в кодер».
 */
@Getter
@Setter
@Command(name = "HLS", mixinStandardHelpOptions = true, version = "1.0.0",
        description = "Convert video to HLS format with adaptive streaming: "
                + "video ladder, multiple audio tracks and WebVTT subtitles in one master playlist.")
public class AppOptions {

    /* ==================== Ввод / вывод ==================== */

    /** Позиционная форма входного файла: {@code HLS source.mp4}. */
    @Parameters(index = "0", arity = "0..1",
            description = "Input video file (positional form; alternatively use --input)")
    private Path inputPositional;

    /** Флаг-форма входного файла: {@code HLS --input source.mp4}. */
    @Option(names = {"-i", "--input"}, description = "Input video file")
    private Path inputOption;

    @Option(names = {"-o", "--output"}, description = "Output directory")
    private Path output;

    @Option(names = "--output-overwrite",
            description = "Allow writing into an existing output directory")
    private boolean outputOverwrite;

    @Option(names = "--single-file",
            description = "Generate MP4 file(s) instead of HLS (remux, no re-encode)")
    private boolean singleFile;

    @Option(names = "--output-filename",
            description = "Base name for output file(s) without extension")
    private String outputFilename;

    /* ==================== HLS ==================== */

    @Option(names = "--hls-disable", negatable = true,
            description = "Disable HLS segments generation")
    private boolean hlsDisable;

    @Option(names = "--hls-type", description = "HLS segment type: ${COMPLETION-CANDIDATES}")
    private HlsType hlsType = HlsType.mpegts;

    @Option(names = "--hls-time", description = "HLS segment duration (seconds)")
    private int hlsTime = 6;

    @Option(names = "--hls-segments", description = "HLS segment filename pattern")
    private String hlsSegments = "{index}";

    @Option(names = "--hls-segment-prefix", description = "Segment prefix in playlists")
    private String hlsSegmentPrefix = "";

    @Option(names = "--hls-playlist-prefix", description = "Playlist URI prefixes")
    private List<String> hlsPlaylistPrefix = new ArrayList<>();

    @Option(names = "--hls-master-playlist",
            description = "Master playlist filename (as on the product banner: master.m3u8)")
    private String hlsMasterPlaylist = "master.m3u8";

    /** Легаси-флаг: folders теперь всегда включены; оставлен, чтобы не ломать старые скрипты. */
    @Deprecated
    @Option(names = "--hls-folder-structure",
            description = "Reserved: files are always organized in folders")
    private boolean hlsFolderStructure = true;

    /* ==================== Видео-лестница ==================== */

    @Option(names = "--video-widths", split = ",", description = "Video widths (pixels)")
    private List<Integer> videoWidths = new ArrayList<>(List.of(1920, 1280, 854, 640, 428));

    @Option(names = "--video-bitrates", split = ",", description = "Video bitrates (kbps)")
    private List<Integer> videoBaseBitrates = new ArrayList<>(List.of(10000, 7000, 4000, 2500, 1000));

    @Option(names = "--video-quality-factors", split = ",",
            description = "Quality factors for each resolution")
    private List<Double> videoQualityFactors = new ArrayList<>(List.of(1.0, 0.7, 0.5, 0.4, 0.2));

    @Option(names = "--video-codecs", split = ",", description = "Video codecs per variant")
    private List<String> videoCodecs = new ArrayList<>(List.of("libx264", "libx264", "libx264", "libx264", "libx264"));

    @Option(names = "--video-profiles", split = ",",
            description = "Encoding profiles (profile@level)")
    private List<String> videoProfiles = new ArrayList<>(List.of("high@5.2", "high@5.2", "high@5.1", "high@4.2", "high@4.0"));

    @Option(names = "--video-names", split = ",",
            description = "Variant names (also used as output folder names)")
    private List<String> videoNames = new ArrayList<>(List.of("1080p", "720p", "480p", "360p", "240p"));

    @Option(names = "--video-bitrate-factor", description = "Bitrate multiplier")
    private double videoBitrateFactor = 1.0;

    @Option(names = "--video-presets", split = ",", description = "Encoding presets")
    private List<String> videoPresets = new ArrayList<>(List.of("slow", "slow", "slow", "slow", "slow"));

    /* ==================== Аудио ==================== */

    @Option(names = "--audio-disable", negatable = true, description = "Disable audio completely")
    private boolean audioDisable;

    @Option(names = "--audio-sampling", description = "Sampling rate (Hz)")
    private Integer audioSampling;

    @Option(names = "--audio-bitrate", description = "Audio bitrate per rendition (kbps)")
    private int audioBitrate = 128;

    @Option(names = "--audio-codec", description = "Audio codec")
    private String audioCodec = "aac";

    @Option(names = "--audio-profile", description = "Audio profile")
    private String audioProfile = "aac_low";

    @Option(names = "--audio-only", description = "Audio-only mode (single audio playlist)")
    private boolean audioOnly;

    @Option(names = "--no-audio", description = "Strip audio from video variants")
    private boolean noAudio;

    @Option(names = "--audio-stream",
            description = "Audio stream selector for single-file mode (index:N)")
    private String audioStream;

    @Option(names = "--all-audio-tracks",
            description = "Process all audio tracks separately (requires --single-file)")
    private boolean allAudioTracks;

    @Option(names = "--audio-languages", split = ",",
            description = "Comma-separated languages to keep as HLS audio renditions "
                    + "(default: all found tracks), e.g. --audio-languages jpn,rus")
    private List<String> audioLanguages = new ArrayList<>();

    @Option(names = "--audio-default-language",
            description = "Language marked DEFAULT=YES in the master playlist "
                    + "(default: the first track)")
    private String audioDefaultLanguage;

    /* ==================== Субтитры (новая возможность) ==================== */

    @Option(names = "--subs-enabled", negatable = true, arity = "0..1", fallbackValue = "true",
            description = "Extract subtitle tracks to WebVTT and add them to HLS, so viewers "
                    + "can switch them in the player (default: enabled). "
                    + "Disable with --subs-enabled=false or --no-subs-enabled")
    private boolean subsEnabled = true;

    @Option(names = "--subs-languages", split = ",",
            description = "Comma-separated subtitle languages to extract "
                    + "(default: all found tracks), e.g. --subs-languages rus,eng")
    private List<String> subsLanguages = new ArrayList<>();

    @Option(names = "--subs-default-language",
            description = "Subtitle language marked DEFAULT=YES in the master playlist "
                    + "(default: none, player chooses automatically)")
    private String subsDefaultLanguage;

    /* ==================== MP4 (режим single-file) ==================== */

    @Option(names = "--mp4-width", description = "MP4 width (pixels)")
    private Integer mp4Width;

    @Option(names = "--mp4-max-width", description = "Max MP4 width")
    private int mp4MaxWidth = 1920;

    @Option(names = "--mp4-bitrate-factor", description = "MP4 bitrate multiplier")
    private double mp4BitrateFactor = 1.5;

    @Option(names = "--mp4-bitrate", description = "MP4 bitrate (kbps)")
    private Integer mp4Bitrate;

    @Option(names = "--mp4-codec", description = "MP4 codec")
    private String mp4Codec = "h264";

    @Option(names = "--mp4-profile", description = "MP4 profile")
    private String mp4Profile = "high@5.2";

    /* ==================== Постер ==================== */

    @Option(names = "--poster-enabled", negatable = true, arity = "0..1", fallbackValue = "true",
            description = "Generate poster (preview.jpg)")
    private boolean posterEnabled = true;

    @Option(names = "--poster-filename", description = "Poster filename")
    private String posterFilename = "preview.jpg";

    @Option(names = "--poster-seek",
            description = "Seek position (percent or seconds, e.g. 45% or 120s)")
    private String posterSeek = "45%";

    @Option(names = "--poster-width", description = "Poster width (pixels)")
    private Integer posterWidth;

    @Option(names = "--poster-max-width", description = "Max poster width")
    private int posterMaxWidth = 1920;

    /* ==================== Плейлист ==================== */

    @Option(names = "--playlist-disabled", negatable = true,
            description = "Disable master playlist creation")
    private boolean playlistDisable;

    /* ==================== Внешние утилиты ==================== */

    @Option(names = "--ffmpeg", description = "FFmpeg executable")
    private String ffmpeg = "ffmpeg";

    @Option(names = "--ffprobe", description = "FFprobe executable")
    private String ffprobe = "ffprobe";

    @Option(names = "--mp4file", description = "MP4file executable")
    private String mp4file = "mp4file";

    @Option(names = "--ratio", description = "Aspect ratio used for playlist RESOLUTION tags")
    private String ratio = "16:9";

    public enum HlsType {
        mpegts
    }

    /* ==================== Производные геттеры ==================== */

    /**
     * Эффективный входной файл: флаг-форма приоритетнее позиционной.
     * Если указаны обе — побеждает флаг (поведение задокументировано).
     */
    public Path getInput() {
        return inputOption != null ? inputOption : inputPositional;
    }

    /* Защитные копии: picocli инжектит списки прямо в поля,
       а внешний код не должен иметь возможности молча менять состояние CLI. */

    public List<String> getHlsPlaylistPrefix() {
        return new ArrayList<>(hlsPlaylistPrefix);
    }

    public void setHlsPlaylistPrefix(List<String> value) {
        this.hlsPlaylistPrefix = value != null ? new ArrayList<>(value) : new ArrayList<>();
    }

    public List<Integer> getVideoWidths() {
        return new ArrayList<>(videoWidths);
    }

    public void setVideoWidths(List<Integer> value) {
        this.videoWidths = value != null ? new ArrayList<>(value) : new ArrayList<>();
    }

    public List<Integer> getVideoBaseBitrates() {
        return new ArrayList<>(videoBaseBitrates);
    }

    public void setVideoBaseBitrates(List<Integer> value) {
        this.videoBaseBitrates = value != null ? new ArrayList<>(value) : new ArrayList<>();
    }

    public List<Double> getVideoQualityFactors() {
        return new ArrayList<>(videoQualityFactors);
    }

    public List<String> getVideoCodecs() {
        return new ArrayList<>(videoCodecs);
    }

    public void setVideoCodecs(List<String> value) {
        this.videoCodecs = value != null ? new ArrayList<>(value) : new ArrayList<>();
    }

    public List<String> getVideoProfiles() {
        return new ArrayList<>(videoProfiles);
    }

    public void setVideoProfiles(List<String> value) {
        this.videoProfiles = value != null ? new ArrayList<>(value) : new ArrayList<>();
    }

    public List<String> getVideoNames() {
        return new ArrayList<>(videoNames);
    }

    public void setVideoNames(List<String> value) {
        this.videoNames = value != null ? new ArrayList<>(value) : new ArrayList<>();
    }

    public List<String> getVideoPresets() {
        return new ArrayList<>(videoPresets);
    }

    public void setVideoPresets(List<String> value) {
        this.videoPresets = value != null ? new ArrayList<>(value) : new ArrayList<>();
    }

    public List<String> getAudioLanguages() {
        return new ArrayList<>(audioLanguages);
    }

    public void setAudioLanguages(List<String> value) {
        this.audioLanguages = value != null ? new ArrayList<>(value) : new ArrayList<>();
    }

    public List<String> getSubsLanguages() {
        return new ArrayList<>(subsLanguages);
    }

    public void setSubsLanguages(List<String> value) {
        this.subsLanguages = value != null ? new ArrayList<>(value) : new ArrayList<>();
    }
}
