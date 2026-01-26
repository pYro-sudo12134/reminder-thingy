package by.losik.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ParsedResult(
        LocalDateTime scheduledTime,
        String action,
        double confidence,
        String language,
        String reminderId,
        String intent,
        List<Entity> entities,
        String rawText,
        String normalizedText
) {}