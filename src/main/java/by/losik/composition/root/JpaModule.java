package by.losik.composition.root;

import by.losik.config.DatabaseConfig;
import by.losik.repository.UserRepository;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

public class JpaModule extends AbstractModule {
    private static final Logger log = LoggerFactory.getLogger(JpaModule.class);

    @Override
    protected void configure() {
        bind(UserRepository.class).in(Singleton.class);
    }

    @Provides
    @Singleton
    public DatabaseConfig provideDatabaseConfig() {
        DatabaseConfig config = new DatabaseConfig();
        log.info("DatabaseConfig initialized. Flyway enabled: {}", config.isFlywayEnabled());
        log.info("Flyway locations: {}", config.getFlywayLocations());
        return config;
    }

    @Provides
    @Singleton
    public DataSource provideDataSource(DatabaseConfig config) {
        log.info("Creating DataSource for database: {}", config.getDatabaseName());
        log.info("Database URL: {}", config.getUrl());

        com.zaxxer.hikari.HikariDataSource ds = new com.zaxxer.hikari.HikariDataSource();
        ds.setJdbcUrl(config.getUrl());
        ds.setUsername(config.getUsername());
        ds.setPassword(config.getPassword());
        ds.setDriverClassName(config.getDriver());
        ds.setMaximumPoolSize(config.getPoolSize());
        ds.setMinimumIdle(config.getPoolMinIdle());
        ds.setConnectionTimeout(config.getConnectionTimeout());
        ds.setMaxLifetime(config.getPoolMaxLifetime());

        log.info("DataSource initialized successfully");
        return ds;
    }

    @Provides
    @Singleton
    public Flyway provideFlyway(DataSource dataSource, DatabaseConfig config) {
        log.info("Initializing Flyway...");
        log.info("Flyway enabled: {}", config.isFlywayEnabled());
        log.info("Flyway locations: {}", config.getFlywayLocations());
        log.info("Flyway baseline version: {}", config.getFlywayBaselineVersion());

        if (!config.isFlywayEnabled()) {
            log.warn("Flyway migrations are DISABLED in configuration!");
            return null;
        }

        try {
            Flyway flyway = Flyway.configure()
                    .dataSource(dataSource)
                    .locations(config.getFlywayLocations())
                    .baselineOnMigrate(true)
                    .baselineVersion(config.getFlywayBaselineVersion())
                    .loggers("slf4j")
                    .load();

            log.info("Flyway configured. Checking migration status...");

            var info = flyway.info();
            log.info("Current migration: {}", info.current());
            log.info("Pending migrations: {}", info.pending().length);

            if (info.pending().length > 0) {
                log.info("Pending migrations to apply:");
                for (var migration : info.pending()) {
                    log.info("  - {}: {}", migration.getVersion(), migration.getDescription());
                }
            }

            log.info("Starting Flyway migrations...");
            flyway.migrate();
            log.info("Flyway migrations COMPLETED successfully!");

            return flyway;
        } catch (Exception e) {
            log.error("Flyway migration FAILED!", e);
            throw new RuntimeException("Flyway migration failed", e);
        }
    }

    @Provides
    @Singleton
    public EntityManagerFactory provideEntityManagerFactory(
            DatabaseConfig config,
            DataSource dataSource,
            Flyway flyway
    ) {
        try {
            log.info("Creating EntityManagerFactory...");

            if (config.isFlywayEnabled() && flyway != null) {
                log.info("Flyway migrations have been executed");
            } else if (config.isFlywayEnabled()) {
                log.warn("Flyway is enabled but Flyway instance is null!");
            }

            Map<String, String> properties = new HashMap<>();
            properties.put("jakarta.persistence.jdbc.url", config.getUrl());
            properties.put("jakarta.persistence.jdbc.user", config.getUsername());
            properties.put("jakarta.persistence.jdbc.password", config.getPassword());
            properties.put("jakarta.persistence.jdbc.driver", config.getDriver());
            properties.putAll(config.getJpaProperties());

            log.debug("Creating EntityManagerFactory with properties:");
            properties.forEach((k, v) -> {
                if (k.contains("password")) {
                    log.debug("  {} = ***", k);
                } else {
                    log.debug("  {} = {}", k, v);
                }
            });

            EntityManagerFactory emf = Persistence.createEntityManagerFactory(
                    "voice_reminder_pu", properties);

            log.info("EntityManagerFactory created successfully");
            return emf;
        } catch (Exception e) {
            log.error("Failed to create EntityManagerFactory: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to initialize JPA", e);
        }
    }

    @Provides
    public EntityManager provideEntityManager(EntityManagerFactory emf) {
        try {
            EntityManager em = emf.createEntityManager();
            log.info("EntityManager created successfully");
            return em;
        } catch (Exception e) {
            log.error("FAILED to create EntityManager: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create EntityManager", e);
        }
    }
}