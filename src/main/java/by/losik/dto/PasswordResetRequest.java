package by.losik.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Запрос на сброс пароля.
 * @param email Email пользователя
 * @param newPassword Новый пароль (минимум 8 символов)
 * @param token Токен сброса пароля
 */
public record PasswordResetRequest(
        @Email @NotBlank(message = "Email is required")
        String email,
        
        @NotBlank(message = "Password is required")
        @Size(min = 8, message = "Password must be at least 8 characters")
        @JsonProperty("newPassword")
        String newPassword,
        
        @NotBlank(message = "Token is required")
        @JsonProperty("token")
        String token
) {
    /**
     * Переопределённый toString() для безопасности.
     * Скрывает пароль, чтобы он не попал в логи.
     */
    @Override
    public String toString() {
        return "PasswordResetRequest{email='%s', newPassword='***', token='%s'}"
                .formatted(
                        email,
                        token != null && token.length() > 8 
                            ? token.substring(0, 8) + "..." 
                            : token
                );
    }
}
