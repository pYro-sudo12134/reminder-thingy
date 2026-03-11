package by.losik.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonFormat;

public record UpdateReminderRequest (
        @JsonProperty(required = true, value = "reminderId")
        String reminderId,
        @JsonProperty("extractedAction")
        String extractedAction,
        @JsonProperty("scheduledTime")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        String scheduledTime,
        @JsonProperty("reminderTime")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm")
        String reminderTime,
        @JsonProperty("status")
        String status,
        @JsonProperty("userEmail")
        String userEmail
){}