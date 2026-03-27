package by.losik.dto;

import java.util.List;

/**
 * Результат автодополнения напоминаний.
 * @param userId ID пользователя
 * @param query Поисковый запрос
 * @param suggestions Список предложений
 * @param total Общее количество результатов
 */
public record AutocompleteResult(String userId, String query, List<Suggestion> suggestions, int total) {

    /**
     * Предложение для автодополнения.
     * @param reminderId ID напоминания
     * @param action Извлечённое действие
     * @param text Исходный текст напоминания
     * @param score релевантность (0.0-1.0)
     */
    public record Suggestion(String reminderId, String action, String text, double score) {
    }
}