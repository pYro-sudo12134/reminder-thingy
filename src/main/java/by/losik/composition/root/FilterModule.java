package by.losik.composition.root;

import by.losik.config.AuthFilterConfig;
import by.losik.config.CorsConfig;
import by.losik.config.RateLimitConfig;
import by.losik.config.RedisConnectionFactory;
import by.losik.config.RedisRateLimitConfig;
import by.losik.filter.CorsFilter;
import by.losik.filter.EndpointLimit;
import by.losik.filter.RateLimiterFilter;
import by.losik.filter.RateLimitResult;
import by.losik.filter.SessionAuthFilter;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

/**
 * Guice модуль для конфигурации фильтров.
 * <p>
 * Предоставляет следующие зависимости:
 * <ul>
 *     <li>{@link AuthFilterConfig} — конфигурация public paths для аутентификации</li>
 *     <li>{@link CorsConfig} — конфигурация CORS настроек</li>
 *     <li>{@link RateLimiterFilter} — фильтр для rate limiting через Redis</li>
 *     <li>{@link SessionAuthFilter} — фильтр для аутентификации через сессии</li>
 *     <li>{@link CorsFilter} — фильтр для CORS заголовков</li>
 * </ul>
 * <p>
 * Фильтры применяются в следующем порядке:
 * <ol>
 *     <li>CorsFilter — добавляет CORS заголовки (самый первый)</li>
 *     <li>RateLimiterFilter — ограничивает частоту запросов</li>
 *     <li>SessionAuthFilter — проверяет аутентификацию</li>
 * </ol>
 *
 * @see by.losik.server.WebServer
 */
public class FilterModule extends AbstractModule {

    /**
     * Конфигурирует привязки для конфигурационных классов фильтров.
     * <p>
     * Привязывает:
     * <ul>
     *     <li>{@link AuthFilterConfig} — Singleton</li>
     *     <li>{@link CorsConfig} — Singleton</li>
     * </ul>
     */
    @Override
    public void configure() {
        bind(AuthFilterConfig.class).in(Singleton.class);
        bind(CorsConfig.class).in(Singleton.class);
    }

    /**
     * Создаёт и предоставляет {@link SessionAuthFilter} для аутентификации пользователей.
     * <p>
     * SessionAuthFilter проверяет наличие активной сессии (JSESSIONID) для защищённых путей.
     * Публичные пути (login, register, health, metrics) доступны без аутентификации.
     *
     * @param config конфигурация public paths
     * @return настроенный SessionAuthFilter
     * @see SessionAuthFilter
     */
    @Provides
    @Singleton
    public SessionAuthFilter createSessionAuthFilter(AuthFilterConfig config) {
        return new SessionAuthFilter(config);
    }

    /**
     * Создаёт и предоставляет {@link CorsFilter} для кросс-доменных запросов.
     * <p>
     * CorsFilter добавляет CORS заголовки ко всем ответам:
     * <ul>
     *     <li>Access-Control-Allow-Origin</li>
     *     <li>Access-Control-Allow-Methods</li>
     *     <li>Access-Control-Allow-Headers</li>
     *     <li>Access-Control-Allow-Credentials</li>
     *     <li>Access-Control-Max-Age</li>
     * </ul>
     *
     * @param config конфигурация CORS настроек
     * @return настроенный CorsFilter
     * @see CorsFilter
     */
    @Provides
    @Singleton
    public CorsFilter createCorsFilter(CorsConfig config) {
        return new CorsFilter(config);
    }

    /**
     * Создаёт и предоставляет {@link EndpointLimit} для категории "upload".
     * <p>
     * Upload эндпоинты (например, /reminder/record) имеют более строгие лимиты:
     * <ul>
     *     <li>10 запросов в минуту</li>
     *     <li>500 запросов в час</li>
     *     <li>2500 запросов в день</li>
     * </ul>
     *
     * @param rateLimitConfig конфигурация лимитов
     * @return EndpointLimit для upload категории
     * @see EndpointLimit
     */
    @Provides
    @Singleton
    @Named("upload")
    public EndpointLimit provideUploadEndpointLimit(RateLimitConfig rateLimitConfig) {
        return new EndpointLimit(
                rateLimitConfig.getUploadLimitPerMinute(),
                rateLimitConfig.getMaxRequestsPerHour() / 2,
                rateLimitConfig.getMaxRequestsPerDay() / 2
        );
    }

    /**
     * Создаёт и предоставляет {@link EndpointLimit} для категории "api".
     * <p>
     * API эндпоинты имеют стандартные лимиты:
     * <ul>
     *     <li>60 запросов в минуту</li>
     *     <li>1000 запросов в час</li>
     *     <li>5000 запросов в день</li>
     * </ul>
     *
     * @param rateLimitConfig конфигурация лимитов
     * @return EndpointLimit для api категории
     * @see EndpointLimit
     */
    @Provides
    @Singleton
    @Named("api")
    public EndpointLimit provideApiEndpointLimit(RateLimitConfig rateLimitConfig) {
        return new EndpointLimit(
                rateLimitConfig.getApiLimitPerMinute(),
                rateLimitConfig.getMaxRequestsPerHour(),
                rateLimitConfig.getMaxRequestsPerDay()
        );
    }

    /**
     * Создаёт и предоставляет {@link EndpointLimit} для категории "stats".
     * <p>
     * Stats эндпоинты имеют более строгие лимиты (половина от стандартных):
     * <ul>
     *     <li>30 запросов в минуту</li>
     *     <li>500 запросов в час</li>
     *     <li>5000 запросов в день</li>
     * </ul>
     *
     * @param rateLimitConfig конфигурация лимитов
     * @return EndpointLimit для stats категории
     * @see EndpointLimit
     */
    @Provides
    @Singleton
    @Named("stats")
    public EndpointLimit provideStatsEndpointLimit(RateLimitConfig rateLimitConfig) {
        return new EndpointLimit(
                rateLimitConfig.getMaxRequestsPerMinute() / 2,
                rateLimitConfig.getMaxRequestsPerHour() / 2,
                rateLimitConfig.getMaxRequestsPerDay()
        );
    }

    /**
     * Создаёт и предоставляет {@link EndpointLimit} для категории "general".
     * <p>
     * General эндпоинты имеют стандартные лимиты:
     * <ul>
     *     <li>60 запросов в минуту</li>
     *     <li>1000 запросов в час</li>
     *     <li>5000 запросов в день</li>
     * </ul>
     *
     * @param rateLimitConfig конфигурация лимитов
     * @return EndpointLimit для general категории
     * @see EndpointLimit
     */
    @Provides
    @Singleton
    @Named("general")
    public EndpointLimit provideGeneralEndpointLimit(RateLimitConfig rateLimitConfig) {
        return new EndpointLimit(
                rateLimitConfig.getMaxRequestsPerMinute(),
                rateLimitConfig.getMaxRequestsPerHour(),
                rateLimitConfig.getMaxRequestsPerDay()
        );
    }

    /**
     * Создаёт и предоставляет {@link RateLimitResult} для разрешённого запроса.
     *
     * @return RateLimitResult с allowed=true
     * @see RateLimitResult
     */
    @Provides
    public RateLimitResult provideAllowedRateLimitResult() {
        return RateLimitResult.allowed();
    }
}
