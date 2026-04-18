package by.losik.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

/**
 * Ответ от сервиса транскрибации (AWS Transcribe).
 * @param jobId ID задачи транскрибации
 * @param jobStatus Статус задачи (QUEUED, IN_PROGRESS, COMPLETED, FAILED)
 * @param transcriptionText Расшифрованный текст
 * @param transcriptionFileUri URI файла с транскрипцией
 * @param failureReason Причина ошибки (если failed)
 * @param createdAt Время создания задачи
 * @param completedAt Время завершения задачи
 */
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