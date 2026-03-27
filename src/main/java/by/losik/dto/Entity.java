package by.losik.dto;

/**
 * Сущность, извлечённая из текста напоминания.
 * @param text Текст сущности
 * @param type Тип сущности (time, location, person, etc.)
 * @param start Позиция начала в тексте
 * @param end Позиция конца в тексте
 * @param confidence Уверенность распознавания (0.0-1.0)
 */
public record Entity(
        String text,
        String type,
        int start,
        int end,
        double confidence
) {}