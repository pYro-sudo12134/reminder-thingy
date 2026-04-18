package by.losik.dto;

/**
 * Ответ на запрос сброса пароля.
 * @param success Успешно ли выполнена операция
 * @param message Сообщение для пользователя
 * @param token Токен сброса (если есть)
 */
public record PasswordResetResponse(
        boolean success,
        String message,
        String token) {
}