package by.losik.dto;

import java.time.ZonedDateTime;
import java.util.List;

public record ParsedResult(
        ZonedDateTime scheduledTime,
        String action,
        double confidence,
        String language,
        String reminderId,
        String intent,
        List<Entity> entities,
        String rawText,
        String normalizedText
) {
    public String ruleName() {
        return "reminder-" + (reminderId != null ? reminderId : "");
    }
}