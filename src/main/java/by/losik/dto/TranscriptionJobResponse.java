package by.losik.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

public record TranscriptionJobResponse(
        @JsonProperty("job_id")
        String jobId,

        @JsonProperty("job_status")
        String jobStatus,

        @JsonProperty("transcription_text")
        String transcriptionText,

        @JsonProperty("transcription_file_uri")
        String transcriptionFileUri,

        @JsonProperty("failure_reason")
        String failureReason,

        @JsonProperty("created_at")
        LocalDateTime createdAt,

        @JsonProperty("completed_at")
        LocalDateTime completedAt
) {}