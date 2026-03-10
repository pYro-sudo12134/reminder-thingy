package by.losik.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UpdateReminderRequest (
    @JsonProperty(required = true)
    String reminderId,
    String extractedAction,
    String scheduledTime,
    String reminderTime,
    String status,
    String userEmail
){}