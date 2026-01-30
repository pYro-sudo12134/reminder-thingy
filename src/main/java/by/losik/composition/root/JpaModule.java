package by.losik.composition.root;

import by.losik.config.DatabaseConfig;
import by.losik.repository.UserRepository;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import org.eclipse.persistence.config.PersistenceUnitProperties;
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
        return new DatabaseConfig();
    }

    @Provides
    @Singleton
    public DataSource provideDataSource(DatabaseConfig config) {
        com.zaxxer.hikari.HikariDataSource ds = new com.zaxxer.hikari.HikariDataSource();
        ds.setJdbcUrl(config.getUrl());
        ds.setUsername(config.getUsername());
        ds.setPassword(config.getPassword());
        ds.setDriverClassName(config.getDriver());
        ds.setMaximumPoolSize(config.getPoolSize());
        ds.setMinimumIdle(config.getPoolMinIdle());
        ds.setConnectionTimeout(config.getConnectionTimeout());
        ds.setMaxLifetime(config.getPoolMaxLifetime());
        ds.setPoolName("VoiceReminderPool");
        ds.addDataSourceProperty("cachePrepStmts", "true");
        ds.addDataSourceProperty("prepStmtCacheSize", "250");
        ds.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        ds.addDataSourceProperty("useServerPrepStmts", "true");
        ds.addDataSourceProperty("useLocalSessionState", "true");
        ds.addDataSourceProperty("rewriteBatchedStatements", "true");
        ds.addDataSourceProperty("cacheResultSetMetadata", "true");
        ds.addDataSourceProperty("cacheServerConfiguration", "true");
        ds.addDataSourceProperty("elideSetAutoCommits", "true");
        ds.addDataSourceProperty("maintainTimeStats", "false");

        log.info("DataSource initialized for database: {}", config.getDatabaseName());
        return ds;
    }

    @Provides
    @Singleton
    public Flyway provideFlyway(DataSource dataSource, DatabaseConfig config) {
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations(config.getFlywayLocations())
                .baselineOnMigrate(true)
                .baselineVersion(config.getFlywayBaselineVersion())
                .validateOnMigrate(true)
                .outOfOrder(false)
                .placeholderReplacement(true)
                .sqlMigrationPrefix("V")
                .sqlMigrationSeparator("__")
                .sqlMigrationSuffixes(".sql")
                .table("flyway_schema_history")
                .load();

        if (config.isFlywayEnabled()) {
            log.info("Running Flyway database migrations...");
            try {
                flyway.migrate();
                log.info("Database migrations completed successfully");
            } catch (Exception e) {
                log.error("Failed to run database migrations", e);
                throw new RuntimeException("Database migration failed", e);
            }
        } else {
            log.info("Flyway migrations are disabled");
        }

        return flyway;
    }

    @Provides
    @Singleton
    public EntityManagerFactory provideEntityManagerFactory(
            DatabaseConfig config,
            DataSource dataSource) {

        Map<String, Object> properties = new HashMap<>();
        properties.put(PersistenceUnitProperties.NON_JTA_DATASOURCE, dataSource);
        properties.putAll(config.getJpaProperties());
        EntityManagerFactory emf = Persistence.createEntityManagerFactory(
                "voice_reminder_pu", properties);

        log.info("EntityManagerFactory created successfully");
        log.debug("JPA properties: {}",
                properties.entrySet().stream()
                        .filter(e -> !e.getKey().toString().contains("password"))
                        .toList());

        return emf;
    }

    @Provides
    public EntityManager provideEntityManager(EntityManagerFactory emf) {
        EntityManager em = emf.createEntityManager();
        log.debug("Created EntityManager: {}", em);
        return em;
    }
}