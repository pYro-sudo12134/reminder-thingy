package by.losik.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Результат семантического анализа текста напоминания.
 * @param scheduledTime Запланированное время выполнения
 * @param action Извлечённое действие
 * @param confidence Уверенность распознавания (0.0-1.0)
 * @param язык Распознанный язык
 * @param reminderId ID напоминания
 * @param intent Намерение (reminder, alert, etc.)
 * @param entities Список извлечённых сущностей
 * @param rawText Исходный текст
 * @param normalizedText Нормализованный текст
 */
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