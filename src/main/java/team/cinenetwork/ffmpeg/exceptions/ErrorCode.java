package team.cinenetwork.ffmpeg.exceptions;

public enum ErrorCode {

    AUDIO_ONLY("Input contains only audio streams", ErrorCategory.PROCESSING),
    AUDIO_STREAM_NOT_FOUND("Required audio stream not found", ErrorCategory.PROCESSING),
    INVALID_SAMPLING_RATE("Invalid or unsupported sampling rate", ErrorCategory.VALIDATION),
    ZERO_WIDTH("Video stream width is zero or undefined", ErrorCategory.VALIDATION),
    INVALID_ASPECT_RATIO("Invalid aspect ratio format or value", ErrorCategory.VALIDATION),
    MISSING_REQUIRED_FIELD("A required configuration field is missing", ErrorCategory.VALIDATION),
    NO_MATCHING_BITRATE("No bitrate profile matches the target resolution", ErrorCategory.PROCESSING),
    FRAME_RATE_NOT_FOUND("Frame rate could not be detected", ErrorCategory.PROCESSING);

    private final String defaultMessage;
    private final ErrorCategory category;

    ErrorCode(String defaultMessage, ErrorCategory category) {
        this.defaultMessage = defaultMessage;
        this.category = category;
    }

    public String getDefaultMessage() { return defaultMessage; }
    public ErrorCategory getCategory() { return category; }

}