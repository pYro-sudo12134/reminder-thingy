package by.losik.composition.root;

import by.losik.config.RateLimitConfig;
import by.losik.config.RedisConnectionFactory;
import by.losik.config.RedisRateLimitConfig;
import by.losik.filter.RateLimiterFilter;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;

/**
 * Guice модуль для конфигурации rate limiting через Redis.
 * <p>
 * Предоставляет следующие зависимости:
 * <ul>
 *     <li>{@link RateLimitConfig} — конфигурация лимитов запросов</li>
 *     <li>{@link RedisRateLimitConfig} — конфигурация Redis подключения</li>
 *     <li>{@link RedisConnectionFactory} — фабрика подключений к Redis</li>
 *     <li>{@link RateLimiterFilter} — servlet фильтр для rate limiting</li>
 * </ul>
 * <p>
 * Rate limiting использует Redis с Lua scripts для атомарного подсчёта запросов.
 * Поддерживает три уровня лимитов: в минуту, в час, в день.
 *
 * @see AbstractModule
 * @see Provides
 */
public class RateLimitModule extends AbstractModule {

    /**
     * Создаёт и предоставляет {@link RateLimiterFilter} для ограничения частоты запросов.
     * <p>
     * RateLimiterFilter использует Redis для подсчёта запросов с ключами:
     * <ul>
     *     <li>{@code rl:min:{clientId}} — лимит в минуту (TTL 60s)</li>
     *     <li>{@code rl:hour:{clientId}} — лимит в час (TTL 3600s)</li>
     *     <li>{@code rl:day:{clientId}} — лимит в день (TTL 86400s)</li>
     * </ul>
     * <p>
     * Client ID определяется по приоритету:
     * <ol>
     *     <li>API key (X-API-Key header)</li>
     *     <li>Bearer token (Authorization header)</li>
     *     <li>User ID (из URL /user/{userId}/...)</li>
     *     <li>IP адрес клиента</li>
     * </ol>
     *
     * @param rateLimitConfig конфигурация лимитов
     * @param redisRateLimitConfig конфигурация Redis подключения
     * @param redisConnectionFactory фабрика подключений к Redis
     * @return настроенный RateLimiterFilter
     * @see RateLimiterFilter
     */
    @Provides
    @Singleton
    public RateLimiterFilter createRateLimiterFilter(
            RateLimitConfig rateLimitConfig,
            RedisRateLimitConfig redisRateLimitConfig,
            RedisConnectionFactory redisConnectionFactory) {
        return new RateLimiterFilter(rateLimitConfig, redisRateLimitConfig, redisConnectionFactory);
    }

    /**
     * Создаёт и предоставляет {@link RateLimitConfig} для настройки лимитов.
     * <p>
     * RateLimitConfig загружает настройки из переменных окружения через ConfigUtils.
     *
     * @return настроенный RateLimitConfig
     * @see RateLimitConfig
     */
    @Provides
    @Singleton
    public RateLimitConfig createRateLimitConfig() {
        return new RateLimitConfig();
    }

    /**
     * Создаёт и предоставляет {@link RedisRateLimitConfig} для подключения к Redis.
     * <p>
     * RedisRateLimitConfig загружает настройки из переменных окружения через ConfigUtils.
     *
     * @return настроенный RedisRateLimitConfig
     * @see RedisRateLimitConfig
     */
    @Provides
    @Singleton
    public RedisRateLimitConfig createRedisRateLimitConfig() {
        return new RedisRateLimitConfig();
    }

    /**
     * Создаёт и предоставляет {@link RedisConnectionFactory} для подключений к Redis.
     * <p>
     * RedisConnectionFactory создаёт пул подключений Jedis с:
     * <ul>
     *     <li>SSL/TLS поддержкой (если включено)</li>
     *     <li>Truststore для проверки сервера</li>
     *     <li>Паролем для аутентификации</li>
     *     <li>Таймаутом подключения</li>
     * </ul>
     *
     * @param redisRateLimitConfig конфигурация Redis подключения
     * @return настроенный RedisConnectionFactory
     * @see RedisConnectionFactory
     */
    @Provides
    @Singleton
    public RedisConnectionFactory createRedisConnectionFactory(RedisRateLimitConfig redisRateLimitConfig) {
        return new RedisConnectionFactory(redisRateLimitConfig);
    }
}