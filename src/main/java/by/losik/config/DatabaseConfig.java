package by.losik.config;

import com.google.inject.Singleton;

/**
 * Конфигурация базы данных и JPA настроек.
 * <p>
 * Содержит настройки для:
 * <ul>
 *     <li>Подключения к PostgreSQL</li>
 *     <li>Миграций Flyway</li>
 *     <li>Кэша EclipseLink</li>
 *     <li>Логирования SQL</li>
 * </ul>
 * <p>
 * Используется в {@link by.losik.composition.root.JpaModule} для создания
 * {@link javax.sql.DataSource}, {@link org.flywaydb.core.Flyway} и
 * {@link jakarta.persistence.EntityManagerFactory}.
 *
 * @see by.losik.composition.root.JpaModule
 */
@Singleton
public class DatabaseConfig {
    
    private boolean flywayEnabled = true;
    private String flywayBaselineVersion = "1";
    private String flywayLocations = "classpath:db/migration";
    private boolean showSql = false;
    private boolean formatSql = true;
    private String ddlGeneration = "none";
    private boolean cacheEnabled = true;
    private String cacheType = "SOFT";
    private int cacheSize = 10000;
    private long cacheExpiry = 3600000;
    private boolean queryCacheEnabled = true;

    /**
     * Проверяет, включены ли миграции Flyway.
     * @return true если миграции включены
     */
    public boolean isFlywayEnabled() { return flywayEnabled; }
    
    /**
     * Устанавливает флаг включения миграций Flyway.
     * @param flywayEnabled true если миграции включены
     */
    public void setFlywayEnabled(boolean flywayEnabled) { this.flywayEnabled = flywayEnabled; }
    
    /**
     * Получает базовую версию Flyway.
     * @return базовая версия (по умолчанию "1")
     */
    public String getFlywayBaselineVersion() { return flywayBaselineVersion; }
    
    /**
     * Устанавливает базовую версию Flyway.
     * @param flywayBaselineVersion базовая версия
     */
    public void setFlywayBaselineVersion(String flywayBaselineVersion) { this.flywayBaselineVersion = flywayBaselineVersion; }
    
    /**
     * Получает расположение миграций Flyway.
     * @return путь к миграциям (classpath:db/migration)
     */
    public String getFlywayLocations() { return flywayLocations; }
    
    /**
     * Устанавливает расположение миграций Flyway.
     * @param flywayLocations путь к миграциям
     */
    public void setFlywayLocations(String flywayLocations) { this.flywayLocations = flywayLocations; }
    
    /**
     * Проверяет, включено ли логирование SQL.
     * @return true если SQL запросы логируются
     */
    public boolean isShowSql() { return showSql; }
    
    /**
     * Устанавливает флаг логирования SQL.
     * @param showSql true если нужно логировать SQL
     */
    public void setShowSql(boolean showSql) { this.showSql = showSql; }
    
    /**
     * Проверяет, включено ли форматирование SQL.
     * @return true если SQL форматируется
     */
    public boolean isFormatSql() { return formatSql; }
    
    /**
     * Устанавливает флаг форматирования SQL.
     * @param formatSql true если нужно форматировать SQL
     */
    public void setFormatSql(boolean formatSql) { this.formatSql = formatSql; }
    
    /**
     * Получает настройку генерации DDL.
     * @return "none", "create", или "drop-and-create"
     */
    public String getDdlGeneration() { return ddlGeneration; }
    
    /**
     * Устанавливает настройку генерации DDL.
     * @param ddlGeneration "none", "create", или "drop-and-create"
     */
    public void setDdlGeneration(String ddlGeneration) { this.ddlGeneration = ddlGeneration; }
    
    /**
     * Проверяет, включён ли кэш EclipseLink.
     * @return true если кэш включён
     */
    public boolean isCacheEnabled() { return cacheEnabled; }
    
    /**
     * Устанавливает флаг включения кэша.
     * @param cacheEnabled true если кэш включён
     */
    public void setCacheEnabled(boolean cacheEnabled) { this.cacheEnabled = cacheEnabled; }
    
    /**
     * Получает тип кэша EclipseLink.
     * @return тип кэша (SOFT, WEAK, HARD)
     */
    public String getCacheType() { return cacheType; }
    
    /**
     * Устанавливает тип кэша EclipseLink.
     * @param cacheType тип кэша
     */
    public void setCacheType(String cacheType) { this.cacheType = cacheType; }
    
    /**
     * Получает размер кэша в количестве записей.
     * @return размер кэша (по умолчанию 10000)
     */
    public int getCacheSize() { return cacheSize; }
    
    /**
     * Устанавливает размер кэша.
     * @param cacheSize количество записей
     */
    public void setCacheSize(int cacheSize) { this.cacheSize = cacheSize; }
    
    /**
     * Получает время жизни кэша в миллисекундах.
     * @return время жизни (по умолчанию 3600000 мс = 1 час)
     */
    public long getCacheExpiry() { return cacheExpiry; }
    
    /**
     * Устанавливает время жизни кэша.
     * @param cacheExpiry время жизни в миллисекундах
     */
    public void setCacheExpiry(long cacheExpiry) { this.cacheExpiry = cacheExpiry; }
    
    /**
     * Проверяет, включён ли кэш запросов.
     * @return true если кэш запросов включён
     */
    public boolean isQueryCacheEnabled() { return queryCacheEnabled; }
    
    /**
     * Устанавливает флаг включения кэша запросов.
     * @param queryCacheEnabled true если кэш включён
     */
    public void setQueryCacheEnabled(boolean queryCacheEnabled) { this.queryCacheEnabled = queryCacheEnabled; }
}