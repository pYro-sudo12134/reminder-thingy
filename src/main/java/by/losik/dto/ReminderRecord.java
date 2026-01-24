package by.losik.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

public record ReminderRecord(
        @JsonProperty("reminder_id")
        String reminderId,

        @JsonProperty("user_id")
        String userId,

        @JsonProperty("original_text")
        String originalText,

        @JsonProperty("extracted_action")
        String extractedAction,

        @JsonProperty("scheduled_time")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime scheduledTime,

        @JsonProperty("reminder_time")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm")
        String reminderTime,

        @JsonProperty("created_at")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime createdAt,

        @JsonProperty("status")
        ReminderStatus status,

        @JsonProperty("notification_sent")
        boolean notificationSent,

        @JsonProperty("rule_name")
        String eventBridgeRuleName
) {
    public enum ReminderStatus {
        PENDING, SCHEDULED, TRIGGERED, CANCELLED, FAILED, COMPLETED
    }
}