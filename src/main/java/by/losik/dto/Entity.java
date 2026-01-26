package by.losik.dto;

public record Entity(
        String text,
        String type,
        int start,
        int end,
        double confidence
) {}