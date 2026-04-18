package by.losik.composition.root;

import by.losik.config.DatabaseConfig;
import by.losik.repository.UserRepository;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * Guice модуль для конфигурации JPA, базы данных и миграций Flyway.
 * <p>
 * Предоставляет следующие зависимости:
 * <ul>
 *     <li>{@link DatabaseConfig} — конфигурация подключения к БД</li>
 *     <li>{@link DataSource} — пул соединений HikariCP</li>
 *     <li>{@link Flyway} — миграции базы данных</li>
 *     <li>{@link EntityManagerFactory} — фабрика EntityManager для JPA</li>
 *     <li>{@link EntityManager} — EntityManager для работы с сущностями</li>
 *     <li>{@link UserRepository} — репозиторий для работы с пользователями</li>
 * </ul>
 * <p>
 * Конфигурация загружается из переменных окружения:
 * <ul>
 *     <li>{@code DB_URL}, {@code DB_USER}, {@code DB_PASSWORD} — подключение к БД</li>
 *     <li>{@code DB_POOL_SIZE}, {@code DB_POOL_MIN_IDLE} — настройки пула соединений</li>
 *     <li>{@code FLYWAY_ENABLED}, {@code FLYWAY_BASELINE_VERSION} — настройки Flyway</li>
 *     <li>{@code JPA_CACHE_ENABLED}, {@code JPA_CACHE_SIZE} — настройки кэша EclipseLink</li>
 * </ul>
 *
 * @see AbstractModule
 * @see Provides
 */
public class JpaModule extends AbstractModule {
    private static final Logger log = LoggerFactory.getLogger(JpaModule.class);

    /**
     * Конфигурирует привязки зависимостей для базы данных и JPA.
     * <p>
     * Привязывает следующие именованные зависимости:
     * <ul>
     *     <li>{@code db.url}, {@code db.username}, {@code db.password} — подключение к БД</li>
     *     <li>{@code db.schema}, {@code db.driver} — схема и драйвер</li>
     *     <li>{@code db.pool.size}, {@code db.pool.minIdle} — настройки пула соединений</li>
     *     <li>{@code db.pool.maxLifetime}, {@code db.connection.timeout} — таймауты</li>
     *     <li>{@code jpa.show_sql}, {@code jpa.format_sql} — логирование SQL</li>
     *     <li>{@code jpa.ddl.generation} — генерация DDL (none/create/drop-and-create)</li>
     *     <li>{@code flyway.enabled}, {@code flyway.baseline.version} — настройки Flyway</li>
     *     <li>{@code jpa.cache.enabled}, {@code jpa.cache.type} — настройки кэша</li>
     * </ul>
     */
    @Override
    public void configure() {
        bind(UserRepository.class).in(Singleton.class);

        bind(String.class).annotatedWith(Names.named("db.url"))
                .toInstance(getEnvOrDefault("DB_URL",
                        "jdbc:postgresql://postgres:5432/voice_reminder"));
        bind(String.class).annotatedWith(Names.named("db.username"))
                .toInstance(getEnvOrDefault("DB_USER", "postgres"));
        bind(String.class).annotatedWith(Names.named("db.password"))
                .toInstance(getEnvOrDefault("DB_PASSWORD", "password"));
        bind(String.class).annotatedWith(Names.named("db.schema"))
                .toInstance(getEnvOrDefault("DB_SCHEMA", "voice_schema"));
        bind(String.class).annotatedWith(Names.named("db.driver"))
                .toInstance(getEnvOrDefault("DB_DRIVER", "org.postgresql.Driver"));

        bind(Integer.class).annotatedWith(Names.named("db.pool.size"))
                .toInstance(Integer.parseInt(getEnvOrDefault("DB_POOL_SIZE", "10")));
        bind(Integer.class).annotatedWith(Names.named("db.pool.minIdle"))
                .toInstance(Integer.parseInt(getEnvOrDefault("DB_POOL_MIN_IDLE", "5")));
        bind(Long.class).annotatedWith(Names.named("db.pool.maxLifetime"))
                .toInstance(Long.parseLong(getEnvOrDefault("DB_POOL_MAX_LIFETIME", "1800000")));
        bind(Long.class).annotatedWith(Names.named("db.connection.timeout"))
                .toInstance(Long.parseLong(getEnvOrDefault("DB_CONNECTION_TIMEOUT", "30000")));

        bind(Boolean.class).annotatedWith(Names.named("jpa.show_sql"))
                .toInstance(Boolean.parseBoolean(getEnvOrDefault("JPA_SHOW_SQL", "false")));
        bind(Boolean.class).annotatedWith(Names.named("jpa.format_sql"))
                .toInstance(Boolean.parseBoolean(getEnvOrDefault("JPA_FORMAT_SQL", "true")));
        bind(String.class).annotatedWith(Names.named("jpa.ddl.generation"))
                .toInstance(getEnvOrDefault("JPA_DDL_GENERATION", "none"));

        bind(Boolean.class).annotatedWith(Names.named("flyway.enabled"))
                .toInstance(Boolean.parseBoolean(getEnvOrDefault("FLYWAY_ENABLED", "true")));
        bind(String.class).annotatedWith(Names.named("flyway.baseline.version"))
                .toInstance(getEnvOrDefault("FLYWAY_BASELINE_VERSION", "1"));
        bind(String.class).annotatedWith(Names.named("flyway.locations"))
                .toInstance(getEnvOrDefault("FLYWAY_LOCATIONS", "classpath:db/migration"));

        bind(Boolean.class).annotatedWith(Names.named("jpa.cache.enabled"))
                .toInstance(Boolean.parseBoolean(getEnvOrDefault("JPA_CACHE_ENABLED", "true")));
        bind(String.class).annotatedWith(Names.named("jpa.cache.type"))
                .toInstance(getEnvOrDefault("JPA_CACHE_TYPE", "SOFT"));
        bind(Integer.class).annotatedWith(Names.named("jpa.cache.size"))
                .toInstance(Integer.parseInt(getEnvOrDefault("JPA_CACHE_SIZE", "10000")));
        bind(Long.class).annotatedWith(Names.named("jpa.cache.expiry"))
                .toInstance(Long.parseLong(getEnvOrDefault("JPA_CACHE_EXPIRY", "3600000")));
        bind(Boolean.class).annotatedWith(Names.named("jpa.query.cache.enabled"))
                .toInstance(Boolean.parseBoolean(getEnvOrDefault("JPA_QUERY_CACHE_ENABLED", "true")));
    }

    /**
     * Создаёт и предоставляет {@link DatabaseConfig} для конфигурации базы данных.
     * <p>
     * DatabaseConfig содержит настройки для:
     * <ul>
     *     <li>Подключения к PostgreSQL</li>
     *     <li>Настроек Flyway миграций</li>
     *     <li>Настроек кэша EclipseLink</li>
     *     <li>Логирования SQL</li>
     * </ul>
     *
     * @param flywayEnabled включены ли миграции Flyway
     * @param flywayBaselineVersion базовая версия Flyway
     * @param flywayLocations расположение миграций Flyway
     * @param showSql показывать ли SQL запросы в логах
     * @param formatSql форматировать ли SQL
     * @param ddlGeneration генерация DDL (none/create/drop-and-create)
     * @param cacheEnabled включён ли кэш EclipseLink
     * @param cacheType тип кэша (SOFT, WEAK, HARD)
     * @param cacheSize размер кэша в количестве записей
     * @param cacheExpiry время жизни кэша в миллисекундах
     * @param queryCacheEnabled включён ли кэш запросов
     * @return настроенный DatabaseConfig
     * @see DatabaseConfig
     */
    @Provides
    @Singleton
    public DatabaseConfig provideDatabaseConfig(
            @Named("flyway.enabled") boolean flywayEnabled,
            @Named("flyway.baseline.version") String flywayBaselineVersion,
            @Named("flyway.locations") String flywayLocations,
            @Named("jpa.show_sql") boolean showSql,
            @Named("jpa.format_sql") boolean formatSql,
            @Named("jpa.ddl.generation") String ddlGeneration,
            @Named("jpa.cache.enabled") boolean cacheEnabled,
            @Named("jpa.cache.type") String cacheType,
            @Named("jpa.cache.size") int cacheSize,
            @Named("jpa.cache.expiry") long cacheExpiry,
            @Named("jpa.query.cache.enabled") boolean queryCacheEnabled) {

        DatabaseConfig config = new DatabaseConfig();
        config.setFlywayEnabled(flywayEnabled);
        config.setFlywayBaselineVersion(flywayBaselineVersion);
        config.setFlywayLocations(flywayLocations);
        config.setShowSql(showSql);
        config.setFormatSql(formatSql);
        config.setDdlGeneration(ddlGeneration);
        config.setCacheEnabled(cacheEnabled);
        config.setCacheType(cacheType);
        config.setCacheSize(cacheSize);
        config.setCacheExpiry(cacheExpiry);
        config.setQueryCacheEnabled(queryCacheEnabled);

        log.info("DatabaseConfig initialized. Flyway enabled: {}", flywayEnabled);
        return config;
    }

    /**
     * Создаёт и предоставляет {@link DataSource} с пулом соединений HikariCP.
     * <p>
     * DataSource настраивается с:
     * <ul>
     *     <li>Максимальным размером пула ({@code db.pool.size})</li>
     *     <li>Минимальным количеством idle соединений ({@code db.pool.minIdle})</li>
     *     <li>Таймаутом подключения ({@code db.connection.timeout})</li>
     *     <li>Максимальным временем жизни соединения ({@code db.pool.maxLifetime})</li>
     * </ul>
     * <p>
     * Пароль в логах маскируется.
     *
     * @param url JDBC URL подключения к базе данных
     * @param username имя пользователя БД
     * @param password пароль пользователя БД
     * @param driver JDBC драйвер (org.postgresql.Driver)
     * @param poolSize максимальный размер пула соединений
     * @param minIdle минимальное количество idle соединений
     * @param maxLifetime максимальное время жизни соединения (мс)
     * @param connectionTimeout таймаут подключения (мс)
     * @return настроенный HikariDataSource
     * @see DataSource
     */
    @Provides
    @Singleton
    public DataSource provideDataSource(
            @Named("db.url") String url,
            @Named("db.username") String username,
            @Named("db.password") String password,
            @Named("db.driver") String driver,
            @Named("db.pool.size") int poolSize,
            @Named("db.pool.minIdle") int minIdle,
            @Named("db.pool.maxLifetime") long maxLifetime,
            @Named("db.connection.timeout") long connectionTimeout) {

        log.info("Creating DataSource for database: {}", url.replaceAll("password=[^&]*", "password=***"));

        com.zaxxer.hikari.HikariDataSource ds = new com.zaxxer.hikari.HikariDataSource();
        ds.setJdbcUrl(url);
        ds.setUsername(username);
        ds.setPassword(password);
        ds.setDriverClassName(driver);
        ds.setMaximumPoolSize(poolSize);
        ds.setMinimumIdle(minIdle);
        ds.setConnectionTimeout(connectionTimeout);
        ds.setMaxLifetime(maxLifetime);

        log.info("DataSource initialized successfully");
        return ds;
    }

    /**
     * Создаёт и предоставляет {@link Flyway} для миграций базы данных.
     * <p>
     * Flyway настраивается с:
     * <ul>
     *     <li>Расположением миграций ({@code flyway.locations})</li>
     *     <li>Базовой версией ({@code flyway.baseline.version})</li>
     *     <li>Автоматическим baseline при первой миграции</li>
     * </ul>
     * <p>
     * Если миграции не выполнены, выбрасывается {@link RuntimeException}.
     *
     * @param dataSource DataSource для подключения к БД
     * @param flywayEnabled включены ли миграции Flyway
     * @param baselineVersion базовая версия Flyway
     * @param locations расположение миграций (classpath:db/migration)
     * @return настроенный Flyway или null, если отключён
     * @throws RuntimeException если миграции не выполнены
     * @see Flyway
     */
    @Provides
    @Singleton
    public Flyway provideFlyway(
            DataSource dataSource,
            @Named("flyway.enabled") boolean flywayEnabled,
            @Named("flyway.baseline.version") String baselineVersion,
            @Named("flyway.locations") String locations) {

        log.info("Initializing Flyway...");
        log.info("Flyway enabled: {}", flywayEnabled);
        log.info("Flyway locations: {}", locations);

        if (!flywayEnabled) {
            log.warn("Flyway migrations are disabled in configuration!");
            return null;
        }

        try {
            Flyway flyway = Flyway.configure()
                    .dataSource(dataSource)
                    .locations(locations)
                    .baselineOnMigrate(true)
                    .baselineVersion(baselineVersion)
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

    /**
     * Создаёт и предоставляет {@link EntityManagerFactory} для JPA.
     * <p>
     * EntityManagerFactory настраивается с:
     * <ul>
     *     <li>Подключением к PostgreSQL через EclipseLink</li>
     *     <li>Настройками кэша (тип, размер, время жизни)</li>
     *     <li>Логированием SQL (если включено)</li>
     *     <li>Настройками пула соединений</li>
     * </ul>
     * <p>
     * Если Flyway включён и успешно выполнен, логируется подтверждение.
     *
     * @param url JDBC URL подключения
     * @param username имя пользователя БД
     * @param password пароль БД
     * @param driver JDBC драйвер
     * @param showSql показывать ли SQL запросы
     * @param formatSql форматировать ли SQL
     * @param ddlGeneration генерация DDL (none/create/drop-and-create)
     * @param cacheEnabled включён ли кэш
     * @param cacheType тип кэша
     * @param cacheSize размер кэша
     * @param cacheExpiry время жизни кэша
     * @param queryCacheEnabled включён ли кэш запросов
     * @param poolSize размер пула соединений
     * @param minIdle минимальное количество idle соединений
     * @param flyway экземпляр Flyway (если включён)
     * @param flywayEnabled включён ли Flyway
     * @return настроенный EntityManagerFactory
     * @throws RuntimeException если не удалось создать EntityManagerFactory
     * @see EntityManagerFactory
     */
    @Provides
    @Singleton
    public EntityManagerFactory provideEntityManagerFactory(
            @Named("db.url") String url,
            @Named("db.username") String username,
            @Named("db.password") String password,
            @Named("db.driver") String driver,
            @Named("jpa.show_sql") boolean showSql,
            @Named("jpa.format_sql") boolean formatSql,
            @Named("jpa.ddl.generation") String ddlGeneration,
            @Named("jpa.cache.enabled") boolean cacheEnabled,
            @Named("jpa.cache.type") String cacheType,
            @Named("jpa.cache.size") int cacheSize,
            @Named("jpa.cache.expiry") long cacheExpiry,
            @Named("jpa.query.cache.enabled") boolean queryCacheEnabled,
            @Named("db.pool.size") int poolSize,
            @Named("db.pool.minIdle") int minIdle,
            Flyway flyway,
            @Named("flyway.enabled") boolean flywayEnabled) {

        try {
            log.info("Creating EntityManagerFactory...");

            if (flywayEnabled && flyway != null) {
                log.info("Flyway migrations have been executed");
            }

            Map<String, String> properties = buildJpaProperties(
                    url, username, password, driver,
                    showSql, formatSql, ddlGeneration,
                    cacheEnabled, cacheType, cacheSize, cacheExpiry, queryCacheEnabled,
                    poolSize, minIdle);

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

    /**
     * Создаёт и предоставляет {@link EntityManager} для работы с сущностями.
     * <p>
     * EntityManager создаётся из EntityManagerFactory для каждого запроса.
     *
     * @param emf фабрика EntityManager
     * @return новый EntityManager
     * @throws RuntimeException если не удалось создать EntityManager
     * @see EntityManager
     */
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

    /**
     * Строит карту свойств JPA для EclipseLink.
     * <p>
     * Включает настройки:
     * <ul>
     *     <li>Подключения к базе данных (URL, user, password, driver)</li>
     *     <li>Кэширования (тип, размер, время жизни)</li>
     *     <li>Логирования (уровень, параметры, SQL)</li>
     *     <li>DDL генерации</li>
     *     <li>Пула соединений</li>
     * </ul>
     *
     * @param url JDBC URL
     * @param username имя пользователя
     * @param password пароль
     * @param driver JDBC драйвер
     * @param showSql показывать ли SQL
     * @param formatSql форматировать ли SQL
     * @param ddlGeneration генерация DDL
     * @param cacheEnabled включён ли кэш
     * @param cacheType тип кэша
     * @param cacheSize размер кэша
     * @param cacheExpiry время жизни кэша
     * @param queryCacheEnabled включён ли кэш запросов
     * @param poolSize размер пула
     * @param minIdle минимальное количество idle соединений
     * @return карта свойств JPA для EclipseLink
     */
    private Map<String, String> buildJpaProperties(
            String url, String username, String password, String driver,
            boolean showSql, boolean formatSql, String ddlGeneration,
            boolean cacheEnabled, String cacheType, int cacheSize, long cacheExpiry,
            boolean queryCacheEnabled, int poolSize, int minIdle) {

        Map<String, String> props = new HashMap<>();
        props.put("jakarta.persistence.jdbc.url", url);
        props.put("jakarta.persistence.jdbc.user", username);
        props.put("jakarta.persistence.jdbc.password", password);
        props.put("jakarta.persistence.jdbc.driver", driver);

        if (cacheEnabled) {
            props.put("eclipselink.cache.shared.default", "true");
            props.put("eclipselink.cache.type.default", cacheType);
            props.put("eclipselink.cache.size.default", String.valueOf(cacheSize));
            props.put("eclipselink.cache.expiry.default", String.valueOf(cacheExpiry));
            if (queryCacheEnabled) {
                props.put("eclipselink.query-results-cache", "true");
                props.put("eclipselink.query-results-cache-size", "1000");
            }
        } else {
            props.put("eclipselink.cache.shared.default", "false");
            props.put("eclipselink.query-results-cache", "false");
        }

        props.put("eclipselink.logging.level", showSql ? "FINE" : "INFO");
        props.put("eclipselink.logging.parameters", "true");
        props.put("eclipselink.logging.timestamp", "false");
        props.put("eclipselink.logging.session", "false");
        props.put("eclipselink.logging.thread", "false");
        props.put("eclipselink.ddl-generation", ddlGeneration);
        props.put("eclipselink.ddl-generation.output-mode", "database");
        props.put("eclipselink.target-database", "PostgreSQL");
        props.put("eclipselink.connection-pool.default.initial", String.valueOf(minIdle));
        props.put("eclipselink.connection-pool.default.min", String.valueOf(minIdle));
        props.put("eclipselink.connection-pool.default.max", String.valueOf(poolSize));
        props.put("eclipselink.logging.sql", showSql && formatSql ? "true" : "false");
        props.put("eclipselink.connection-pool.default.wait", "30000");
        props.put("eclipselink.jdbc.connections.wait", "true");
        props.put("eclipselink.jdbc.connections.retry", "3");

        return props;
    }

    /**
     * Получает значение переменной окружения или системного свойства.
     * <p>
     * Системные свойства имеют приоритет над переменными окружения.
     *
     * @param envVar имя переменной окружения или системного свойства
     * @param defaultValue значение по умолчанию
     * @return значение переменной окружения, системного свойства или defaultValue
     */
    private String getEnvOrDefault(String envVar, String defaultValue) {
        return java.util.Optional.ofNullable(System.getenv(envVar))
                .or(() -> java.util.Optional.ofNullable(System.getProperty(envVar)))
                .orElse(defaultValue);
    }
}