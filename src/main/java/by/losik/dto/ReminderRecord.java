package by.losik.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.ZonedDateTime;

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
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ssXXX")
        ZonedDateTime scheduledTime,

        @JsonProperty("created_at")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ssXXX")
        ZonedDateTime createdAt,

        @JsonProperty("status")
        ReminderStatus status,

        @Deprecated
        @JsonProperty("notification_sent")
        boolean notificationSent,

        @JsonProperty("intent")
        String intent,

        @JsonProperty("rule_name")
        String eventBridgeRuleName
) {

    public enum ReminderStatus {
        PENDING,
        SCHEDULED,
        TRIGGERED,
        CANCELLED,
        FAILED,
        COMPLETED
    }
}