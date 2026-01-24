package by.losik.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

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
    public SendEventRequest(String source, String detailType, String detailJson) {
        this(source, detailType, detailJson, "default");
    }
}