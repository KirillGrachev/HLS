package team.cinenetwork.exception;

/**
 * Реестр всех кодов ошибок приложения.
 *
 * <p>Каждый код знает своё сообщение по умолчанию и категорию, поэтому исключение
 * создаётся в одну строку: {@code throw ProcessingException.of(ErrorCode.NO_VIDEO_STREAM);}
 *
 * <p>Это единственное место, где описано «что может пойти не так»: новый сценарий
 * ошибки начинается отсюда, а не с места броска.
 */
public enum ErrorCode {

    /* --- Валидация входных данных --- */
    NO_VIDEO_STREAM("Source file contains no video streams", ErrorCategory.VALIDATION),
    AUDIO_ONLY("Input contains only audio streams", ErrorCategory.PROCESSING),
    INVALID_ASPECT_RATIO("Invalid aspect ratio format or value", ErrorCategory.VALIDATION),
    ZERO_WIDTH("Video stream width is zero or undefined", ErrorCategory.VALIDATION),
    MISSING_REQUIRED_FIELD("A required configuration field is missing", ErrorCategory.VALIDATION),
    NULL_PATH_PROVIDED("Path argument cannot be null", ErrorCategory.VALIDATION),
    INVALID_DIRECTORY_PATH("Specified path is not a valid directory", ErrorCategory.VALIDATION),
    DIRECTORY_EXISTS("Output directory already exists and overwrite is disabled", ErrorCategory.VALIDATION),

    /* --- Обработка --- */
    FRAME_RATE_NOT_FOUND("Frame rate could not be detected", ErrorCategory.PROCESSING),
    AUDIO_STREAM_NOT_FOUND("Required audio stream not found", ErrorCategory.PROCESSING),
    VIDEO_PROCESSING_FAILED("Video processing failed", ErrorCategory.PROCESSING),
    COMMAND_EXECUTION_FAILED("External command failed with non-zero exit code", ErrorCategory.PROCESSING),
    INVALID_COMMAND_ARGUMENTS("Command or arguments are invalid", ErrorCategory.VALIDATION),

    /* --- Система --- */
    EXECUTION_INTERRUPTED("Command execution was interrupted", ErrorCategory.SYSTEM);

    /** Сообщение по умолчанию (английский: уходит в логи и метрики сервиса). */
    private final String defaultMessage;

    /** Категория ошибки для быстрой маршрутизации. */
    private final ErrorCategory category;

    ErrorCode(String defaultMessage, ErrorCategory category) {
        this.defaultMessage = defaultMessage;
        this.category = category;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }

    public ErrorCategory getCategory() {
        return category;
    }

}
