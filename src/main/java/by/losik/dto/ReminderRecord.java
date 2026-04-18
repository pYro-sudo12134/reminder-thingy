package by.losik.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

/**
 * Запись напоминания для передачи между слоями системы.
 * @param reminderId ID напоминания
 * @param userId ID пользователя
 * @param userEmail Email пользователя для уведомлений
 * @param originalText Исходный текст напоминания
 * @param extractedAction Извлечённое действие
 * @param scheduledTime Запланированное время выполнения
 * @param createdAt Время создания
 * @param status Статус напоминания
 * @param notificationSent Отправлено ли уведомление
 * @param intent Намерение (reminder, alert, etc.)
 * @param eventBridgeRuleName Имя правила EventBridge
 */
public record ReminderRecord(
        @JsonProperty("reminder_id")
        String reminderId,

        @JsonProperty("user_id")
        String userId,

        @JsonProperty("user_email")
        String userEmail,
        @JsonProperty("original_text")
        String originalText,

        @JsonProperty("extracted_action")
        String extractedAction,

        @JsonProperty("scheduled_time")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime scheduledTime,

        @JsonProperty("created_at")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime createdAt,

        @JsonProperty("status")
        ReminderStatus status,

        @JsonProperty("notification_sent")
        boolean notificationSent,

        @JsonProperty("intent")
        String intent,

        @JsonProperty("rule_name")
        String eventBridgeRuleName
) {

    /**
     * Статус напоминания в системе.
     */
    public enum ReminderStatus {
        /** Ожидает обработки */
        PENDING,
        /** Запланировано */
        SCHEDULED,
        /** Активировано (уведомление отправлено) */
        TRIGGERED,
        /** Отменено пользователем */
        CANCELLED,
        /** Ошибка обработки */
        FAILED,
        /** Успешно выполнено */
        COMPLETED
    }
}