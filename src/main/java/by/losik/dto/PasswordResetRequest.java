package by.losik.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PasswordResetRequest(
        String email,
        @JsonProperty("newPassword")
        String newPassword,
        @JsonProperty("token")
        String token) {
}
