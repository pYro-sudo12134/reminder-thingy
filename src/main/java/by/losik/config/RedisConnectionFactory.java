package by.losik.config;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.apache.http.conn.ssl.DefaultHostnameVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.time.Duration;

@Singleton
public class RedisConnectionFactory {
    private static final Logger log = LoggerFactory.getLogger(RedisConnectionFactory.class);
    private final RedisRateLimitConfig redisConfig;

    @Inject
    public RedisConnectionFactory(RedisRateLimitConfig redisConfig) {
        this.redisConfig = redisConfig;
    }

    public JedisPool createJedisPool() {
        try {
            JedisPoolConfig poolConfig = createPoolConfig();

            if (redisConfig.isUseSsl()) {
                return createSslJedisPool(poolConfig);
            } else {
                return createNonSslJedisPool(poolConfig);
            }
        } catch (Exception e) {
            log.error("Failed to create Redis pool", e);
            throw new RuntimeException("Redis initialization failed", e);
        }
    }

    private JedisPoolConfig createPoolConfig() {
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(redisConfig.getMaxTotal());
        poolConfig.setMaxIdle(redisConfig.getMaxIdle());
        poolConfig.setMinIdle(redisConfig.getMinIdle());
        poolConfig.setTestOnBorrow(true);
        poolConfig.setTestWhileIdle(true);
        poolConfig.setMinEvictableIdleTime(Duration.ofMinutes(5));
        poolConfig.setTimeBetweenEvictionRuns(Duration.ofSeconds(30));
        return poolConfig;
    }

    private JedisPool createSslJedisPool(JedisPoolConfig poolConfig) throws Exception {
        SSLParameters sslParameters = new SSLParameters();
        sslParameters.setProtocols(new String[]{redisConfig.getSslProtocol()});

        HostnameVerifier hostnameVerifier = getHostnameVerifier(redisConfig.getSslVerifyMode());
        SSLContext sslContext = createSslContext();
        SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

        if (redisConfig.getPassword() != null && !redisConfig.getPassword().isEmpty()) {
            return new JedisPool(poolConfig,
                    redisConfig.getHost(),
                    redisConfig.getPort(),
                    redisConfig.getTimeout(),
                    redisConfig.getPassword(),
                    true,
                    sslSocketFactory,
                    sslParameters,
                    hostnameVerifier);
        } else {
            return new JedisPool(poolConfig,
                    redisConfig.getHost(),
                    redisConfig.getPort(),
                    redisConfig.getTimeout(),
                    true,
                    sslSocketFactory,
                    sslParameters,
                    hostnameVerifier);
        }
    }

    private JedisPool createNonSslJedisPool(JedisPoolConfig poolConfig) {
        if (redisConfig.getPassword() != null && !redisConfig.getPassword().isEmpty()) {
            return new JedisPool(poolConfig,
                    redisConfig.getHost(),
                    redisConfig.getPort(),
                    redisConfig.getTimeout(),
                    redisConfig.getPassword());
        } else {
            return new JedisPool(poolConfig,
                    redisConfig.getHost(),
                    redisConfig.getPort(),
                    redisConfig.getTimeout());
        }
    }

    private SSLContext createSslContext() throws Exception {
        SSLContext sslContext = SSLContext.getInstance(redisConfig.getSslProtocol());

        TrustManager[] trustManagers = createTrustManagers(
                redisConfig.getSslTruststorePath(),
                redisConfig.getSslTruststorePassword());

        KeyManager[] keyManagers = null;
        if (redisConfig.getSslKeystorePath() != null &&
                !redisConfig.getSslKeystorePath().isEmpty()) {
            keyManagers = createKeyManagers(
                    redisConfig.getSslKeystorePath(),
                    redisConfig.getSslKeystorePassword());
        }

        sslContext.init(keyManagers, trustManagers, null);
        return sslContext;
    }

    private TrustManager[] createTrustManagers(String truststorePath, String password) throws Exception {
        KeyStore trustStore = KeyStore.getInstance("JKS");
        try (FileInputStream fis = new FileInputStream(truststorePath)) {
            trustStore.load(fis, password.toCharArray());
        }

        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(trustStore);

        return trustManagerFactory.getTrustManagers();
    }

    private KeyManager[] createKeyManagers(String keystorePath, String password) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("JKS");
        try (FileInputStream fis = new FileInputStream(keystorePath)) {
            keyStore.load(fis, password.toCharArray());
        }

        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(
                KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keyStore, password.toCharArray());

        return keyManagerFactory.getKeyManagers();
    }

    private HostnameVerifier getHostnameVerifier(String verifyMode) {
        return switch (verifyMode.toLowerCase()) {
            case "none" -> (hostname, session) -> true;
            case "full" -> new DefaultHostnameVerifier();
            default -> new DefaultHostnameVerifier();
        };
    }
}