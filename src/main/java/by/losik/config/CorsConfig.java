package by.losik.config;

import by.losik.util.ConfigUtils;
import com.google.inject.Singleton;

/**
 * Конфигурация CORS фильтра (CorsFilter).
 * <p>
 * Определяет настройки для кросс-доменных запросов:
 * <ul>
 *     <li>Разрешённые origin (источники)</li>
 *     <li>Разрешённые методы (GET, POST, PUT, DELETE, OPTIONS)</li>
 *     <li>Разрешённые заголовки</li>
 *     <li>Время кэширования preflight запросов (max-age)</li>
 *     <li>Разрешение credentials (cookies, authorization headers)</li>
 * </ul>
 * <p>
 * Настройки загружаются из переменных окружения через ConfigUtils.
 *
 * @see by.losik.filter.CorsFilter
 */
@Singleton
public class CorsConfig {

    /** Allowed origins по умолчанию */
    private static final String DEFAULT_ALLOWED_ORIGINS = "*";

    /** Allowed methods по умолчанию */
    private static final String DEFAULT_ALLOWED_METHODS = "GET, POST, PUT, DELETE, OPTIONS";

    /** Allowed headers по умолчанию */
    private static final String DEFAULT_ALLOWED_HEADERS = "Origin, X-Requested-With, Content-Type, Accept, Authorization";

    /** Max age по умолчанию (1 час) */
    private static final String DEFAULT_MAX_AGE = "3600";

    private final String allowedOrigins;
    private final String allowedMethods;
    private final String allowedHeaders;
    private final String maxAge;
    private final boolean allowCredentials;

    /**
     * Создаёт конфигурацию CORS с загрузкой настроек из переменных окружения.
     * <p>
     * Переменные окружения:
     * <ul>
     *     <li>{@code CORS_ALLOWED_ORIGINS} — разрешённые origin (по умолчанию "*")</li>
     *     <li>{@code CORS_ALLOWED_METHODS} — разрешённые методы</li>
     *     <li>{@code CORS_ALLOWED_HEADERS} — разрешённые заголовки</li>
     *     <li>{@code CORS_MAX_AGE} — время кэширования preflight запросов в секундах</li>
     *     <li>{@code CORS_ALLOW_CREDENTIALS} — разрешить credentials (true/false)</li>
     * </ul>
     */
    public CorsConfig() {
        this.allowedOrigins = ConfigUtils.getEnvOrDefault("CORS_ALLOWED_ORIGINS", DEFAULT_ALLOWED_ORIGINS);
        this.allowedMethods = ConfigUtils.getEnvOrDefault("CORS_ALLOWED_METHODS", DEFAULT_ALLOWED_METHODS);
        this.allowedHeaders = ConfigUtils.getEnvOrDefault("CORS_ALLOWED_HEADERS", DEFAULT_ALLOWED_HEADERS);
        this.maxAge = ConfigUtils.getEnvOrDefault("CORS_MAX_AGE", DEFAULT_MAX_AGE);
        this.allowCredentials = ConfigUtils.getBooleanEnvOrDefault("CORS_ALLOW_CREDENTIALS", true);
    }

    /**
     * Получает разрешённые origin.
     *
     * @return строка с разрешёнными origin (например, "*" или "https://example.com")
     */
    public String getAllowedOrigins() {
        return allowedOrigins;
    }

    /**
     * Получает разрешённые методы.
     *
     * @return строка с разрешёнными методами (например, "GET, POST, PUT, DELETE, OPTIONS")
     */
    public String getAllowedMethods() {
        return allowedMethods;
    }

    /**
     * Получает разрешённые заголовки.
     *
     * @return строка с разрешёнными заголовками
     */
    public String getAllowedHeaders() {
        return allowedHeaders;
    }

    /**
     * Получает время кэширования preflight запросов.
     *
     * @return max-age в секундах (например, "3600")
     */
    public String getMaxAge() {
        return maxAge;
    }

    /**
     * Проверяет, разрешены ли credentials (cookies, authorization headers).
     *
     * @return true если credentials разрешены
     */
    public boolean isAllowCredentials() {
        return allowCredentials;
    }
}
