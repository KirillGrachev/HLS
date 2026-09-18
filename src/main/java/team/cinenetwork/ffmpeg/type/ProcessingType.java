package team.cinenetwork.ffmpeg.type;

/**
 * Виды работ FFmpeg, которые умеет выполнять исполнитель.
 */
public enum ProcessingType {

    /** Кадр-превью (preview.jpg). */
    POSTER,

    /** «Лестница» видео-вариантов (video/1080p, video/720p, ...). */
    TRANSCODE,

    /** Отдельные аудио-рендишены по дорожкам (audio/jpn, audio/rus, ...). */
    AUDIO,

    /** Конвертация дорожек субтитров в WebVTT (subtitles/rus, ...). */
    SUBTITLES,

    /** MP4-remux (режим {@code --single-file}). */
    SINGLE_FILE

}
