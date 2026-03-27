package by.losik.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Запрос на автодополнение напоминаний.
 * @param userId ID пользователя
 * @param query Поисковый запрос
 * @param limit Максимальное количество результатов
 */
public record AutocompleteRequest(
        @JsonProperty("userId") String userId,
        @JsonProperty("query") String query,
        @JsonProperty("limit") int limit
) {}