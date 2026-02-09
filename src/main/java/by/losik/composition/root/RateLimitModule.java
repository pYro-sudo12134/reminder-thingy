package by.losik.composition.root;

import by.losik.config.RateLimitConfig;
import by.losik.config.RedisConnectionFactory;
import by.losik.config.RedisRateLimitConfig;
import by.losik.config.SecretsManagerConfig;
import by.losik.filter.RateLimiterFilter;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;

public class RateLimitModule extends AbstractModule {

    @Provides
    @Singleton
    public RateLimiterFilter createRateLimiterFilter(
            RateLimitConfig rateLimitConfig,
            RedisRateLimitConfig redisRateLimitConfig,
            RedisConnectionFactory redisConnectionFactory) {
        return new RateLimiterFilter(rateLimitConfig, redisRateLimitConfig, redisConnectionFactory);
    }

    @Provides
    @Singleton
    public RateLimitConfig createRateLimitConfig(SecretsManagerConfig secretsManagerConfig) {
        return new RateLimitConfig(secretsManagerConfig);
    }

    @Provides
    @Singleton
    public RedisRateLimitConfig createRedisRateLimitConfig(SecretsManagerConfig secretsManagerConfig) {
        return new RedisRateLimitConfig(secretsManagerConfig);
    }

    @Provides
    @Singleton
    public RedisConnectionFactory createRedisConnectionFactory(RedisRateLimitConfig redisRateLimitConfig) {
        return new RedisConnectionFactory(redisRateLimitConfig);
    }
}