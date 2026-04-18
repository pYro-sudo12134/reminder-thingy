package by.losik.config;

import by.losik.util.ConfigUtils;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Конфигурация Redis подключения для rate limiting.
 * <p>
 * Загружает настройки из переменных окружения:
 * <ul>
 *     <li>REDIS_HOST — хост Redis (redis)</li>
 *     <li>REDIS_PORT — порт Redis (6379)</li>
 *     <li>REDIS_PASSWORD — пароль Redis</li>
 *     <li>REDIS_TIMEOUT — таймаут подключения (2000 мс)</li>
 *     <li>REDIS_MAX_TOTAL — максимальное количество соединений (50)</li>
 *     <li>REDIS_MAX_IDLE — максимальное количество idle соединений (10)</li>
 *     <li>REDIS_MIN_IDLE — минимальное количество idle соединений (5)</li>
 *     <li>REDIS_USE_SSL — использовать ли SSL (true)</li>
 *     <li>REDIS_SSL_TRUSTSTORE_PATH — путь к truststore</li>
 *     <li>REDIS_SSL_PROTOCOL — SSL протокол (TLSv1.2)</li>
 * </ul>
 *
 * @see by.losik.config.RedisConnectionFactory
 */
@Singleton
public class RedisRateLimitConfig {
    private static final Logger log = LoggerFactory.getLogger(RedisRateLimitConfig.class);

    private final String host;
    private final int port;
    private final String password;
    private final int timeout;
    private final int maxTotal;
    private final int maxIdle;
    private final int minIdle;
    private final boolean useSsl;
    private final String sslTruststorePath;
    private final String sslTruststorePassword;
    private final String sslKeystorePath;
    private final String sslKeystorePassword;
    private final String sslVerifyMode;
    private final String sslProtocol;
    private final String sslCipherSuites;

    /**
     * Создаёт конфигурацию Redis подключения.
     * <p>
     * Настройки загружаются из переменных окружения через ConfigUtils.
     */
    public RedisRateLimitConfig() {
        this.host = ConfigUtils.getEnvOrDefault("REDIS_HOST", "redis");
        this.port = ConfigUtils.getIntEnvOrDefault("REDIS_PORT", 6379);
        this.password = ConfigUtils.getEnvOrDefault("REDIS_PASSWORD", "");
        this.timeout = ConfigUtils.getIntEnvOrDefault("REDIS_TIMEOUT", 2000);
        this.maxTotal = ConfigUtils.getIntEnvOrDefault("REDIS_MAX_TOTAL", 50);
        this.maxIdle = ConfigUtils.getIntEnvOrDefault("REDIS_MAX_IDLE", 10);
        this.minIdle = ConfigUtils.getIntEnvOrDefault("REDIS_MIN_IDLE", 5);
        this.useSsl = ConfigUtils.getBooleanEnvOrDefault("REDIS_USE_SSL", true);
        this.sslTruststorePath = ConfigUtils.getEnvOrDefault("REDIS_SSL_TRUSTSTORE_PATH", "/app/redis_certs/redis_truststore.jks");
        this.sslTruststorePassword = ConfigUtils.getEnvOrDefault("REDIS_SSL_TRUSTSTORE_PASSWORD", "changeit");
        this.sslKeystorePath = ConfigUtils.getEnvOrDefault("REDIS_SSL_KEYSTORE_PATH", "/app/redis_certs/redis_keystore.jks");
        this.sslKeystorePassword = ConfigUtils.getEnvOrDefault("REDIS_SSL_KEYSTORE_PASSWORD", "changeit");
        this.sslVerifyMode = ConfigUtils.getEnvOrDefault("REDIS_SSL_VERIFY_MODE", "full");
        this.sslProtocol = ConfigUtils.getEnvOrDefault("REDIS_SSL_PROTOCOL", "TLSv1.2");
        this.sslCipherSuites = ConfigUtils.getEnvOrDefault("REDIS_SSL_CIPHER_SUITES",
                "TLS_AES_256_GCM_SHA384,TLS_CHACHA20_POLY1305_SHA256,TLS_AES_128_GCM_SHA256");

        log.info("Redis Rate Limit Config: {}:{}, SSL={}, timeout={}ms",
                host, port, useSsl, timeout);

        if (useSsl) {
            log.info("Redis SSL Config: truststore={}, keystore={}, protocol={}",
                    sslTruststorePath, sslKeystorePath, sslProtocol);
        }
    }
    public String getHost() { return host; }
    public int getPort() { return port; }
    public String getPassword() { return password; }
    public int getTimeout() { return timeout; }
    public int getMaxTotal() { return maxTotal; }
    public int getMaxIdle() { return maxIdle; }
    public int getMinIdle() { return minIdle; }
    public boolean isUseSsl() { return useSsl; }
    public String getSslTruststorePath() { return sslTruststorePath; }
    public String getSslTruststorePassword() { return sslTruststorePassword; }
    public String getSslKeystorePath() { return sslKeystorePath; }
    public String getSslKeystorePassword() { return sslKeystorePassword; }
    public String getSslVerifyMode() { return sslVerifyMode; }
    public String getSslProtocol() { return sslProtocol; }

    public String getSslCipherSuites() {
        return sslCipherSuites;
    }
}