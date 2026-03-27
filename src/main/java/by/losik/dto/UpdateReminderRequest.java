package by.losik.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Запрос на обновление напоминания.
 * @param reminderId ID напоминания
 * @param extractedAction Извлечённое действие
 * @param scheduledTime Время выполнения (ISO 8601: yyyy-MM-dd'T'HH:mm:ss)
 * @param status Статус напоминания
 * @param userEmail Email пользователя
 */
public record UpdateReminderRequest(
        @NotNull(message = "Reminder ID is required")
        @JsonProperty(value = "reminderId", required = true)
        String reminderId,
        
        @JsonProperty("extracted_action")
        String extractedAction,
        
        @NotBlank(message = "Scheduled time is required")
        @JsonProperty("scheduled_time")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        String scheduledTime,
        
        @JsonProperty("status")
        String status,
        
        @JsonProperty("user_email")
        String userEmail
) {}