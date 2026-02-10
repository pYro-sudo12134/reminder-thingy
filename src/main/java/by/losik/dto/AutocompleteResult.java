package by.losik.dto;

import java.util.List;

public record AutocompleteResult(String userId, String query, List<Suggestion> suggestions, int total) {
    public record Suggestion(String reminderId, String action, String text, double score) {
    }
}