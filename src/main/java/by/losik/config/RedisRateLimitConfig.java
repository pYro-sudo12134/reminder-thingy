package by.losik.config;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    @Inject
    public RedisRateLimitConfig(SecretsManagerConfig secretsManager) {
        this.host = secretsManager.getSecret("REDIS_HOST", "redis");
        this.port = Integer.parseInt(secretsManager.getSecret("REDIS_PORT", "6379"));
        this.password = secretsManager.getSecret("REDIS_PASSWORD", System.getenv("REDIS_PASSWORD"));
        this.timeout = Integer.parseInt(secretsManager.getSecret("REDIS_TIMEOUT", "2000"));
        this.maxTotal = Integer.parseInt(secretsManager.getSecret("REDIS_MAX_TOTAL", "50"));
        this.maxIdle = Integer.parseInt(secretsManager.getSecret("REDIS_MAX_IDLE", "10"));
        this.minIdle = Integer.parseInt(secretsManager.getSecret("REDIS_MIN_IDLE", "5"));
        this.useSsl = Boolean.parseBoolean(secretsManager.getSecret("REDIS_USE_SSL", "true"));
        this.sslTruststorePath = secretsManager.getSecret("REDIS_SSL_TRUSTSTORE_PATH", "/app/redis_certs/redis_truststore.jks");
        this.sslTruststorePassword = secretsManager.getSecret("REDIS_SSL_TRUSTSTORE_PASSWORD", "changeit");
        this.sslKeystorePath = secretsManager.getSecret("REDIS_SSL_KEYSTORE_PATH", "/app/redis_certs/redis_keystore.jks");
        this.sslKeystorePassword = secretsManager.getSecret("REDIS_SSL_KEYSTORE_PASSWORD", "changeit");
        this.sslVerifyMode = secretsManager.getSecret("REDIS_SSL_VERIFY_MODE", "full");
        this.sslProtocol = secretsManager.getSecret("REDIS_SSL_PROTOCOL", "TLSv1.2");
        this.sslCipherSuites = secretsManager.getSecret("REDIS_SSL_CIPHER_SUITES",
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