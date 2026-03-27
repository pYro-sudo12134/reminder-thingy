package by.losik.config;

import by.losik.util.ConfigUtils;
import com.google.inject.Singleton;

/**
 * Конфигурация AWS S3.
 * <p>
 * Определяет настройки для работы с объектным хранилищем S3:
 * <ul>
 *     <li>Имя бакета для хранения аудиофайлов</li>
 *     <li>Переопределение endpoint (для LocalStack или кастомных S3 совместимых сервисов)</li>
 *     <li>Режим доступа к бакетам (path-style vs virtual-hosted-style)</li>
 * </ul>
 * <p>
 * Настройки загружаются из переменных окружения через ConfigUtils:
 * <ul>
 *     <li>{@code S3_BUCKET_NAME} — имя бакета (по умолчанию "chatbot-audio-recordings")</li>
 *     <li>{@code S3_ENDPOINT_OVERRIDE} — переопределение endpoint (по умолчанию null)</li>
 *     <li>{@code S3_PATH_STYLE_ACCESS} — режим path-style access (по умолчанию true для LocalStack)</li>
 * </ul>
 *
 * @see by.losik.service.S3Service
 */
@Singleton
public class S3Config {

    /** Имя бакета по умолчанию */
    private static final String DEFAULT_BUCKET_NAME = "chatbot-audio-recordings";

    private final String bucketName;
    private final String endpointOverride;
    private final boolean pathStyleAccess;

    /**
     * Создаёт конфигурацию S3 с загрузкой настроек из переменных окружения.
     */
    public S3Config() {
        this.bucketName = ConfigUtils.getEnvOrDefault("S3_BUCKET_NAME", DEFAULT_BUCKET_NAME);
        this.endpointOverride = ConfigUtils.getEnvOrDefault("S3_ENDPOINT_OVERRIDE", null);
        this.pathStyleAccess = ConfigUtils.getBooleanEnvOrDefault("S3_PATH_STYLE_ACCESS", true);
    }

    /**
     * Получает имя бакета S3.
     *
     * @return имя бакета (например, "chatbot-audio-recordings")
     */
    public String getBucketName() {
        return bucketName;
    }

    /**
     * Получает переопределение endpoint.
     * <p>
     * Если null, используется endpoint из LocalStackConfig.
     *
     * @return endpoint override или null
     */
    public String getEndpointOverride() {
        return endpointOverride;
    }

    /**
     * Проверяет, включён ли path-style access.
     * <p>
     * Path-style access (true) используется для LocalStack и некоторых S3 совместимых сервисов.
     * Virtual-hosted-style (false) используется для AWS S3.
     *
     * @return true если path-style access включён
     */
    public boolean isPathStyleAccess() {
        return pathStyleAccess;
    }
}
