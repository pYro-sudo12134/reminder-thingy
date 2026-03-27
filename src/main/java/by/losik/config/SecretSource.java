package by.losik.config;

/**
 * Источник загрузки секретов.
 * <p>
 * Используется в {@link SecretsManagerConfig} для логирования
 * того, откуда были загружены секреты.
 *
 * @see SecretsManagerConfig
 */
public enum SecretSource {
    /** Docker Secrets (/run/secrets/) */
    DOCKER_SECRETS,
    /** Локальные файлы (./secrets/) */
    LOCAL_FILES,
    /** AWS Secrets Manager */
    AWS_SECRETS_MANAGER,
    /** Переменные окружения */
    ENVIRONMENT
}