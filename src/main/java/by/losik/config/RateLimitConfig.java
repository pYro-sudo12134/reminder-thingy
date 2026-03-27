package by.losik.config;

import by.losik.util.ConfigUtils;
import com.google.inject.Singleton;

/**
 * Конфигурация rate limiting для ограничения частоты запросов.
 * <p>
 * Загружает настройки из переменных окружения через ConfigUtils:
 * <ul>
 *     <li>RATE_LIMIT_PER_MINUTE — лимит запросов в минуту (60)</li>
 *     <li>RATE_LIMIT_PER_HOUR — лимит запросов в час (1000)</li>
 *     <li>RATE_LIMIT_PER_DAY — лимит запросов в день (5000)</li>
 *     <li>RATE_LIMIT_UPLOAD_PER_MINUTE — лимит загрузок аудио (10)</li>
 *     <li>RATE_LIMIT_API_PER_MINUTE — лимит API запросов (60)</li>
 *     <li>RATE_LIMIT_ENABLED — включён ли rate limiting</li>
 *     <li>RATE_LIMIT_WHITELIST — список whitelisted IP</li>
 * </ul>
 *
 * @see by.losik.filter.RateLimiterFilter
 */
@Singleton
public class RateLimitConfig {

    /**
     * Создаёт конфигурацию rate limiting.
     */
    public RateLimitConfig() {
    }

    /**
     * Получает лимит запросов в минуту.
     * @return лимит (по умолчанию 60)
     */
    public int getMaxRequestsPerMinute() {
        return ConfigUtils.getIntEnvOrDefault("RATE_LIMIT_PER_MINUTE", 60);
    }

    /**
     * Получает лимит запросов в час.
     * @return лимит (по умолчанию 1000)
     */
    public int getMaxRequestsPerHour() {
        return ConfigUtils.getIntEnvOrDefault("RATE_LIMIT_PER_HOUR", 1000);
    }

    /**
     * Получает лимит запросов в день.
     * @return лимит (по умолчанию 5000)
     */
    public int getMaxRequestsPerDay() {
        return ConfigUtils.getIntEnvOrDefault("RATE_LIMIT_PER_DAY", 5000);
    }

    /**
     * Проверяет, включён ли rate limiting.
     * @return true если включён
     */
    public boolean isEnabled() {
        return ConfigUtils.getBooleanEnvOrDefault("RATE_LIMIT_ENABLED", true);
    }

    /**
     * Получает лимит загрузок аудио в минуту.
     * @return лимит (по умолчанию 10)
     */
    public int getUploadLimitPerMinute() {
        return ConfigUtils.getIntEnvOrDefault("RATE_LIMIT_UPLOAD_PER_MINUTE", 10);
    }

    /**
     * Получает лимит API запросов в минуту.
     * @return лимит (по умолчанию 60)
     */
    public int getApiLimitPerMinute() {
        return ConfigUtils.getIntEnvOrDefault("RATE_LIMIT_API_PER_MINUTE", 60);
    }

    /**
     * Получает список whitelisted IP адресов.
     * @return массив IP адресов
     */
    public String[] getWhitelistedIps() {
        String whitelist = ConfigUtils.getEnvOrNull("RATE_LIMIT_WHITELIST");
        if (whitelist == null || whitelist.isBlank()) {
            return new String[0];
        }
        return whitelist.split(",");
    }
}