package by.losik.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public record AutocompleteRequest(String userId, String query, int limit) {
    @JsonCreator
    public AutocompleteRequest(
            @JsonProperty("userId") String userId,
            @JsonProperty("query") String query,
            @JsonProperty("limit") int limit) {
        this.userId = userId;
        this.query = query;
        this.limit = limit;
    }
}