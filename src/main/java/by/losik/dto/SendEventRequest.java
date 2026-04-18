package by.losik.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Запрос на отправку события в EventBridge.
 * @param source Источник события
 * @param detailType Тип события
 * @param detailJson Детали события в формате JSON
 * @param eventBusName Имя шины событий (по умолчанию "default")
 */
public record SendEventRequest(
        @JsonProperty("source")
        String source,

        @JsonProperty("detail_type")
        String detailType,

        @JsonProperty("detail")
        String detailJson,

        @JsonProperty("event_bus_name")
        String eventBusName
) {
    /**
     * Конструктор по умолчанию с шиной событий "default".
     * @param source Источник события
     * @param detailType Тип события
     * @param detailJson Детали события в формате JSON
     * @return SendEventRequest с eventBusName="default"
     */
    public static SendEventRequest ofDefault(String source, String detailType, String detailJson) {
        return new SendEventRequest(source, detailType, detailJson, "default");
    }
}