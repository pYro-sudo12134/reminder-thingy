package by.losik.dto;

public record PasswordResetResponse(
        boolean success,
        String message,
        String token) {
}