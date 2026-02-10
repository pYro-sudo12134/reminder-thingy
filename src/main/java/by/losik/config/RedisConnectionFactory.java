package by.losik.config;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
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
            System.setProperty("javax.net.debug", "ssl");

            JedisPoolConfig poolConfig = createPoolConfig();

            if (redisConfig.isUseSsl()) {
                log.info("Creating SSL JedisPool to {}:{}",
                        redisConfig.getHost(), redisConfig.getPort());
                return createSslJedisPool(poolConfig);
            } else {
                log.info("Creating non-SSL JedisPool to {}:{}",
                        redisConfig.getHost(), redisConfig.getPort());
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
        String truststorePath = redisConfig.getSslTruststorePath();
        String truststorePassword = redisConfig.getSslTruststorePassword();

        log.info("Loading truststore from: {}", truststorePath);

        KeyStore trustStore = KeyStore.getInstance("JKS");
        try (FileInputStream fis = new FileInputStream(truststorePath)) {
            trustStore.load(fis, truststorePassword.toCharArray());
            log.info("Truststore loaded successfully");
        }

        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(trustStore);
        log.info("TrustManagerFactory initialized");

        SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
        sslContext.init(null, trustManagerFactory.getTrustManagers(), null);
        SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();
        log.info("SSLContext created");

        javax.net.ssl.HostnameVerifier hostnameVerifier = (hostname, session) -> {
            log.info("Hostname verification for: {}", hostname);
            return true;
        };

        String password = redisConfig.getPassword();
        log.info("Creating JedisPool with password: {}",
                password != null && !password.isEmpty() ? "YES" : "NO");

        if (password != null && !password.isEmpty()) {
            return new JedisPool(
                    poolConfig,
                    redisConfig.getHost(),
                    redisConfig.getPort(),
                    5000,
                    password,
                    true,
                    sslSocketFactory,
                    null,
                    hostnameVerifier
            );
        } else {
            return new JedisPool(
                    poolConfig,
                    redisConfig.getHost(),
                    redisConfig.getPort(),
                    5000,
                    true,
                    sslSocketFactory,
                    null,
                    hostnameVerifier
            );
        }
    }

    private JedisPool createNonSslJedisPool(JedisPoolConfig poolConfig) {
        String password = redisConfig.getPassword();
        if (password != null && !password.isEmpty()) {
            return new JedisPool(
                    poolConfig,
                    redisConfig.getHost(),
                    redisConfig.getPort(),
                    redisConfig.getTimeout(),
                    password
            );
        } else {
            return new JedisPool(
                    poolConfig,
                    redisConfig.getHost(),
                    redisConfig.getPort(),
                    redisConfig.getTimeout()
            );
        }
    }
}