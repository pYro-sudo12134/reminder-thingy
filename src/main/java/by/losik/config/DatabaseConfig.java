package by.losik.config;

import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

@Singleton
public class DatabaseConfig {
    private static final Logger log = LoggerFactory.getLogger(DatabaseConfig.class);

    private final Map<String, String> properties = new HashMap<>();

    public DatabaseConfig() {
        loadFromEnvironment();
        loadFromPropertiesFile();
        setDefaults();
        logConfiguration();
    }

    private void loadFromEnvironment() {
        setPropertyFromEnv("DB_URL", "db.url");
        setPropertyFromEnv("DB_USERNAME", "db.username");
        setPropertyFromEnv("DB_PASSWORD", "db.password");
        setPropertyFromEnv("DB_HOST", "db.host");
        setPropertyFromEnv("DB_PORT", "db.port");
        setPropertyFromEnv("DB_NAME", "db.name");
        setPropertyFromEnv("DB_POOL_SIZE", "db.pool.size");
        setPropertyFromEnv("DB_POOL_MIN_IDLE", "db.pool.minIdle");
        setPropertyFromEnv("DB_POOL_MAX_LIFETIME", "db.pool.maxLifetime");
        setPropertyFromEnv("DB_CONNECTION_TIMEOUT", "db.connection.timeout");
        setPropertyFromEnv("JPA_SHOW_SQL", "jpa.show_sql");
        setPropertyFromEnv("JPA_FORMAT_SQL", "jpa.format_sql");
        setPropertyFromEnv("JPA_DDL_GENERATION", "jpa.ddl.generation");
        setPropertyFromEnv("FLYWAY_ENABLED", "flyway.enabled");
        setPropertyFromEnv("FLYWAY_BASELINE_VERSION", "flyway.baseline.version");
        setPropertyFromEnv("FLYWAY_LOCATIONS", "flyway.locations");
        setPropertyFromEnv("JPA_CACHE_ENABLED", "jpa.cache.enabled");
        setPropertyFromEnv("JPA_CACHE_TYPE", "jpa.cache.type");
        setPropertyFromEnv("JPA_CACHE_SIZE", "jpa.cache.size");
        setPropertyFromEnv("JPA_CACHE_EXPIRY", "jpa.cache.expiry");
        setPropertyFromEnv("JPA_QUERY_CACHE_ENABLED", "jpa.query.cache.enabled");
    }

    private void setPropertyFromEnv(String envVar, String propertyKey) {
        String envValue = System.getenv(envVar);
        if (envValue != null && !envValue.trim().isEmpty()) {
            properties.put(propertyKey, envValue.trim());
            log.debug("Loaded from env {}: {} = {}", envVar, propertyKey,
                    propertyKey.contains("password") ? "***" : envValue);
        }
    }

    private void loadFromPropertiesFile() {
        try (InputStream input = getClass().getClassLoader()
                .getResourceAsStream("database.properties")) {
            if (input != null) {
                Properties fileProps = new Properties();
                fileProps.load(input);

                fileProps.forEach((key, value) -> {
                    String keyStr = key.toString();
                    if (!properties.containsKey(keyStr)) {
                        properties.put(keyStr, value.toString());
                    }
                });
                log.info("Database configuration loaded from file");
            }
        } catch (IOException e) {
            log.debug("database.properties not found, using defaults");
        }
    }

    private void setDefaults() {
        properties.putIfAbsent("db.host", "localhost");
        properties.putIfAbsent("db.port", "5432");
        properties.putIfAbsent("db.name", "voice_reminder");
        properties.putIfAbsent("db.url",
                String.format("jdbc:postgresql://%s:%s/%s",
                        properties.get("db.host"),
                        properties.get("db.port"),
                        properties.get("db.name")));
        properties.putIfAbsent("db.username", "postgres");
        properties.putIfAbsent("db.password", "password");
        properties.putIfAbsent("db.driver", "org.postgresql.Driver");
        properties.putIfAbsent("db.pool.size", "10");
        properties.putIfAbsent("db.pool.minIdle", "5");
        properties.putIfAbsent("db.pool.maxLifetime", "1800000");
        properties.putIfAbsent("db.connection.timeout", "30000");
        properties.putIfAbsent("jpa.show_sql", "false");
        properties.putIfAbsent("jpa.format_sql", "true");
        properties.putIfAbsent("jpa.ddl.generation", "none");
        properties.putIfAbsent("flyway.enabled", "true");
        properties.putIfAbsent("flyway.baseline.version", "1");
        properties.putIfAbsent("flyway.locations", "classpath:db/migration");
        properties.putIfAbsent("jpa.cache.enabled", "true");
        properties.putIfAbsent("jpa.cache.type", "SOFT");
        properties.putIfAbsent("jpa.cache.size", "10000");
        properties.putIfAbsent("jpa.cache.expiry", "3600000");
        properties.putIfAbsent("jpa.query.cache.enabled", "true");
    }

    private void logConfiguration() {
        log.info("=== Database Configuration ===");
        log.info("URL: {}", properties.get("db.url").replaceAll("password=[^&]*", "password=***"));
        log.info("Username: {}", properties.get("db.username"));
        log.info("Pool size: {}", properties.get("db.pool.size"));
        log.info("JPA DDL generation: {}", properties.get("jpa.ddl.generation"));
        log.info("Flyway enabled: {}", properties.get("flyway.enabled"));
        log.info("==============================");
    }

    public String getUrl() { return properties.get("db.url"); }
    public String getUsername() { return properties.get("db.username"); }
    public String getPassword() { return properties.get("db.password"); }
    public String getDriver() { return properties.get("db.driver"); }
    public String getHost() { return properties.get("db.host"); }
    public String getPort() { return properties.get("db.port"); }
    public String getDatabaseName() { return properties.get("db.name"); }

    public int getPoolSize() {
        return Integer.parseInt(properties.get("db.pool.size"));
    }

    public int getPoolMinIdle() {
        return Integer.parseInt(properties.getOrDefault("db.pool.minIdle", "5"));
    }

    public long getPoolMaxLifetime() {
        return Long.parseLong(properties.getOrDefault("db.pool.maxLifetime", "1800000"));
    }

    public long getConnectionTimeout() {
        return Long.parseLong(properties.getOrDefault("db.connection.timeout", "30000"));
    }

    public boolean isShowSql() {
        return Boolean.parseBoolean(properties.get("jpa.show_sql"));
    }

    public boolean isFormatSql() {
        return Boolean.parseBoolean(properties.get("jpa.format_sql"));
    }

    public String getDdlGeneration() {
        return properties.get("jpa.ddl.generation");
    }

    public boolean isFlywayEnabled() {
        return Boolean.parseBoolean(properties.get("flyway.enabled"));
    }

    public String getFlywayBaselineVersion() {
        return properties.get("flyway.baseline.version");
    }

    public String getFlywayLocations() {
        return properties.get("flyway.locations");
    }

    public boolean isCacheEnabled() {
        return Boolean.parseBoolean(
                properties.getOrDefault("jpa.cache.enabled", "true")
        );
    }

    public String getCacheType() {
        return properties.getOrDefault("jpa.cache.type", "SOFT");
    }

    public int getCacheSize() {
        return Integer.parseInt(
                properties.getOrDefault("jpa.cache.size", "10000")
        );
    }

    public long getCacheExpiry() {
        return Long.parseLong(
                properties.getOrDefault("jpa.cache.expiry", "3600000")
        );
    }

    public boolean isQueryCacheEnabled() {
        return Boolean.parseBoolean(
                properties.getOrDefault("jpa.query.cache.enabled", "true")
        );
    }
    public Map<String, String> getJpaProperties() {
        Map<String, String> jpaProps = new HashMap<>();
        jpaProps.put("jakarta.persistence.jdbc.url", getUrl());
        jpaProps.put("jakarta.persistence.jdbc.user", getUsername());
        jpaProps.put("jakarta.persistence.jdbc.password", getPassword());
        jpaProps.put("jakarta.persistence.jdbc.driver", getDriver());

        if (isCacheEnabled()) {
            jpaProps.put("eclipselink.cache.shared.default", "true");
            jpaProps.put("eclipselink.cache.type.default", getCacheType());
            jpaProps.put("eclipselink.cache.size.default",
                    String.valueOf(getCacheSize()));
            jpaProps.put("eclipselink.cache.expiry.default",
                    String.valueOf(getCacheExpiry()));

            if (isQueryCacheEnabled()) {
                jpaProps.put("eclipselink.query-results-cache", "true");
                jpaProps.put("eclipselink.query-results-cache-size", "1000");
            }
        } else {
            jpaProps.put("eclipselink.cache.shared.default", "false");
            jpaProps.put("eclipselink.query-results-cache", "false");
        }

        jpaProps.put("eclipselink.logging.level", isShowSql() ? "FINE" : "INFO");
        jpaProps.put("eclipselink.logging.parameters", "true");
        jpaProps.put("eclipselink.logging.timestamp", "false");
        jpaProps.put("eclipselink.logging.session", "false");
        jpaProps.put("eclipselink.logging.thread", "false");
        jpaProps.put("eclipselink.ddl-generation", getDdlGeneration());
        jpaProps.put("eclipselink.ddl-generation.output-mode", "database");
        jpaProps.put("eclipselink.target-database", "PostgreSQL");
        jpaProps.put("eclipselink.cache.shared.default", "false");
        jpaProps.put("eclipselink.query-results-cache", "false");
        jpaProps.put("eclipselink.connection-pool.default.initial",
                String.valueOf(getPoolMinIdle()));
        jpaProps.put("eclipselink.connection-pool.default.min",
                String.valueOf(getPoolMinIdle()));
        jpaProps.put("eclipselink.connection-pool.default.max",
                String.valueOf(getPoolSize()));

        if (isShowSql() && isFormatSql()) {
            jpaProps.put("eclipselink.logging.sql", "true");
        }

        return jpaProps;
    }
}