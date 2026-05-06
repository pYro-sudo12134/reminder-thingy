package by.losik.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateReminderRequest(
        @NotNull(message = "Reminder ID is required")
        @JsonProperty(value = "reminderId", required = true)
        String reminderId,

        @JsonProperty("extractedAction")
        String extractedAction,

        @NotBlank(message = "Scheduled time is required")
        @JsonProperty("scheduledTime")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        String scheduledTime,

        @JsonProperty("status")
        String status,

        @JsonProperty("userEmail")
        String userEmail
) {}