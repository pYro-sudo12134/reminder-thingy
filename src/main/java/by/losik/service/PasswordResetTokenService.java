package by.losik.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.UUID;

/**
 * Сервис для генерации и проверки токенов сброса пароля.
 * <p>
 * Использует JWT (JSON Web Tokens) для создания безопасных токенов сброса пароля.
 * Токены содержат:
 * <ul>
 *     <li>Issuer (эмитент) — идентификатор приложения</li>
 *     <li>Subject (тема) — имя пользователя</li>
 *     <li>JWT ID — уникальный идентификатор токена</li>
 *     <li>Issued At — время создания токена</li>
 *     <li>Expires At — время истечения токена</li>
 *     <li>Claim "type" — тип токена (password_reset)</li>
 * </ul>
 * <p>
 * Время жизни токена настраивается через {@code TOKEN_EXPIRATION} (по умолчанию 24 часа).
 *
 * @see EmailPasswordResetService
 */
@Singleton
public class PasswordResetTokenService {
    private static final Logger log = LoggerFactory.getLogger(PasswordResetTokenService.class);

    private final Algorithm algorithm;
    private final long expirationTime;
    private final String issuer;

    /**
     * Создаёт сервис токенов с внедрёнными параметрами.
     *
     * @param secretKey секретный ключ для HMAC256 подписи
     * @param expirationTime время жизни токена в миллисекундах
     * @param issuer идентификатор эмитента (приложения)
     */
    @Inject
    public PasswordResetTokenService(@Named("secret.key") String secretKey,
                                     @Named("token.expiration") long expirationTime,
                                     @Named("issuer") String issuer) {
        this.algorithm = Algorithm.HMAC256(secretKey);
        this.expirationTime = expirationTime;
        this.issuer = issuer;
    }

    /**
     * Генерирует токен сброса пароля для пользователя.
     *
     * @param username имя пользователя
     * @return JWT токен для сброса пароля
     */
    public String generateToken(String username) {
        String token = JWT.create()
                .withIssuer(issuer)
                .withSubject(username)
                .withJWTId(UUID.randomUUID().toString())
                .withIssuedAt(new Date())
                .withExpiresAt(new Date(System.currentTimeMillis() + expirationTime))
                .withClaim("type", "password_reset")
                .sign(algorithm);

        log.debug("Generated password reset token for user: {}", username);
        return token;
    }

    /**
     * Проверяет валидность токена сброса пароля.
     * <p>
     * Проверяет:
     * <ul>
     *     <li>Подпись токена (HMAC256)</li>
     *     <li>Эмитента токена</li>
     *     <li>Тип токена (password_reset)</li>
     *     <li>Время истечения токена</li>
     * </ul>
     *
     * @param token JWT токен для проверки
     * @return имя пользователя если токен валиден, null если невалиден
     */
    public String validateToken(String token) {
        try {
            DecodedJWT jwt = JWT.require(algorithm)
                    .withIssuer(issuer)
                    .withClaim("type", "password_reset")
                    .build()
                    .verify(token);

            String username = jwt.getSubject();
            Date expiresAt = jwt.getExpiresAt();

            log.debug("Token validated for user: {}, expires: {}", username, expiresAt);
            return username;

        } catch (JWTVerificationException e) {
            log.warn("Invalid password reset token: {}", e.getMessage());
            return null;
        }
    }
}
