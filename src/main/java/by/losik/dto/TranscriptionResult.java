package by.losik.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

public record TranscriptionResult(
        @JsonProperty("transcription_id")
        String transcriptionId,

        @JsonProperty("original_audio_key")
        String originalAudioKey,

        @JsonProperty("transcribed_text")
        String transcribedText,

        @JsonProperty("confidence")
        Double confidence,

        @JsonProperty("language")
        String language,

        @JsonProperty("duration_seconds")
        Double durationSeconds,

        @JsonProperty("completed_at")
        LocalDateTime completedAt
) {}