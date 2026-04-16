package team.cinenetwork.ffmpeg.exceptions;

public enum ErrorCode {

    AUDIO_ONLY("Input contains only audio streams", ErrorCategory.PROCESSING),
    AUDIO_STREAM_NOT_FOUND("Required audio stream not found", ErrorCategory.PROCESSING),
    INVALID_SAMPLING_RATE("Invalid or unsupported sampling rate", ErrorCategory.VALIDATION),
    ZERO_WIDTH("Video stream width is zero or undefined", ErrorCategory.VALIDATION),
    INVALID_ASPECT_RATIO("Invalid aspect ratio format or value", ErrorCategory.VALIDATION),
    MISSING_REQUIRED_FIELD("A required configuration field is missing", ErrorCategory.VALIDATION),
    NO_MATCHING_BITRATE("No bitrate profile matches the target resolution", ErrorCategory.PROCESSING),
    FRAME_RATE_NOT_FOUND("Frame rate could not be detected", ErrorCategory.PROCESSING),
    VIDEO_PROCESSING_FAILED("Video processing failed", ErrorCategory.PROCESSING),
    PROCESSOR_INITIALIZATION_FAILED("Processor initialization failed", ErrorCategory.PROCESSING),
    DIRECTORY_EXISTS("Output directory already exists and overwrite is disabled", ErrorCategory.VALIDATION),
    INVALID_DIRECTORY_PATH("Specified path is not a valid directory", ErrorCategory.VALIDATION),
    NO_VIDEO_STREAM("Source file contains no video streams", ErrorCategory.VALIDATION),
    COMMAND_EXECUTION_FAILED("External command failed with non-zero exit code", ErrorCategory.PROCESSING),
    INVALID_COMMAND_ARGUMENTS("Command or arguments are invalid", ErrorCategory.VALIDATION),
    EXECUTION_INTERRUPTED("Command execution was interrupted", ErrorCategory.SYSTEM),
    NULL_PATH_PROVIDED("Path argument cannot be null", ErrorCategory.VALIDATION);

    private final String defaultMessage;
    private final ErrorCategory category;

    ErrorCode(String defaultMessage, ErrorCategory category) {
        this.defaultMessage = defaultMessage;
        this.category = category;
    }

    public String getDefaultMessage() { return defaultMessage; }
    public ErrorCategory getCategory() { return category; }

}