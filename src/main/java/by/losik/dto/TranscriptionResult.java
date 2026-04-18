package by.losik.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

/**
 * Результат транскрибации аудиофайла.
 * @param transcriptionId ID транскрипции
 * @param originalAudioKey Ключ аудиофайла в S3
 * @param transcribedText Расшифрованный текст
 * @param confidence Уверенность распознавания (0.0-1.0)
 * @param language Распознанный язык
 * @param durationSeconds Длительность аудио в секундах
 * @param completedAt Время завершения транскрибации
 */
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