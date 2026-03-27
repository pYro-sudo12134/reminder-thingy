package by.losik.composition.root;

import by.losik.config.CorsConfig;
import by.losik.config.GRPCConfig;
import by.losik.config.LocalStackConfig;
import by.losik.config.MonitoringConfig;
import by.losik.config.SecretsManagerConfig;
import by.losik.filter.RateLimiterFilter;
import by.losik.filter.SessionAuthFilter;
import by.losik.resource.AuthResource;
import by.losik.resource.MetricsResource;
import by.losik.resource.PasswordResetResource;
import by.losik.resource.ReminderResource;
import by.losik.server.WebServer;
import by.losik.service.LambdaHandler;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.inject.name.Names;

import java.util.Optional;
import software.amazon.awssdk.services.lambda.LambdaAsyncClient;

/**
 * Guice модуль для конфигурации AWS сервисов и LocalStack.
 * <p>
 * Предоставляет следующие зависимости:
 * <ul>
 *     <li>{@link SecretsManagerConfig} — конфигурация для AWS Secrets Manager</li>
 *     <li>{@link LocalStackConfig} — конфигурация для LocalStack (эмулятор AWS)</li>
 *     <li>{@link GRPCConfig} — конфигурация для gRPC клиента (NLP сервис)</li>
 *     <li>{@link WebServer} — веб-сервер Jetty с Jersey</li>
 *     <li>{@link MonitoringConfig} — конфигурация для Micrometer (CloudWatch, Prometheus)</li>
 * </ul>
 * <p>
 * Все конфигурационные значения загружаются из переменных окружения или системных свойств.
 * Системные свойства имеют приоритет над переменными окружения.
 *
 * @see AbstractModule
 * @see Provides
 */
public class AWSModule extends AbstractModule {

    /**
     * Конфигурирует привязки зависимостей для AWS сервисов.
     * <p>
     * Привязывает следующие именованные строковые зависимости:
     * <ul>
     *     <li>{@code aws.endpoint.url} — URL эндпоинта AWS/LocalStack</li>
     *     <li>{@code aws.region} — AWS регион (по умолчанию us-east-1)</li>
     *     <li>{@code aws.access.key} — AWS access key</li>
     *     <li>{@code aws.secret.key} — AWS secret key</li>
     *     <li>{@code opensearch.host} — хост OpenSearch</li>
     *     <li>{@code opensearch.port} — порт OpenSearch</li>
     *     <li>{@code opensearch.truststore.path} — путь к truststore для SSL</li>
     *     <li>{@code opensearch.truststore.password} — пароль truststore</li>
     *     <li>{@code environment.name} — имя окружения (dev/prod)</li>
     *     <li>{@code email} — email администратора</li>
     *     <li>{@code ws.port} — порт веб-сервера</li>
     * </ul>
     */
    @Override
    public void configure() {
        bind(String.class).annotatedWith(Names.named("aws.endpoint.url"))
                .toInstance(getEnvOrDefault("AWS_ENDPOINT_URL", "http://localstack:4566"));
        bind(String.class).annotatedWith(Names.named("aws.region"))
                .toInstance(getEnvOrDefault("AWS_REGION", "us-east-1"));
        bind(String.class).annotatedWith(Names.named("aws.access.key"))
                .toInstance(getEnvOrDefault("AWS_ACCESS_KEY_ID", "test"));
        bind(String.class).annotatedWith(Names.named("aws.secret.key"))
                .toInstance(getEnvOrDefault("AWS_SECRET_ACCESS_KEY", "test"));

        bind(String.class).annotatedWith(Names.named("opensearch.host"))
                .toInstance(getEnvOrDefault("OPENSEARCH_HOST", "localstack"));
        bind(String.class).annotatedWith(Names.named("opensearch.port"))
                .toInstance(getEnvOrDefault("OPENSEARCH_PORT", "4510"));
        bind(String.class).annotatedWith(Names.named("opensearch.truststore.path"))
                .toInstance(getEnvOrDefault("OPENSEARCH_TRUSTSTORE_PATH", "/app/certs/truststore.jks"));
        bind(String.class).annotatedWith(Names.named("opensearch.truststore.password"))
                .toInstance(getEnvOrDefault("OPENSEARCH_TRUSTSTORE_PASSWORD", "changeit"));

        bind(String.class).annotatedWith(Names.named("environment.name"))
                .toInstance(getEnvOrDefault("ENVIRONMENT_NAME", "dev"));
        bind(String.class).annotatedWith(Names.named("email"))
                .toInstance(getEnvOrDefault("EMAIL", "losik2006@gmail.com"));

        bind(String.class).annotatedWith(Names.named("ws.port"))
                .toInstance(getEnvOrDefault("WS_PORT", "8090"));
    }

    /**
     * Создаёт и предоставляет {@link SecretsManagerConfig} для управления секретами.
     * <p>
     * SecretsManagerConfig отвечает за загрузку секретов из:
     * <ul>
     *     <li>Docker Secrets</li>
     *     <li>Локальных файлов</li>
     *     <li>AWS Secrets Manager</li>
     *     <li>Переменных окружения</li>
     * </ul>
     *
     * @param endpoint URL эндпоинта AWS/LocalStack
     * @param region AWS регион
     * @param accessKey AWS access key
     * @param secretKey AWS secret key
     * @param environmentName имя окружения (dev/prod)
     * @return настроенный SecretsManagerConfig
     * @see SecretsManagerConfig
     */
    @Provides
    @Singleton
    public SecretsManagerConfig createSecretsManagerConfig(
            @Named("aws.endpoint.url") String endpoint,
            @Named("aws.region") String region,
            @Named("aws.access.key") String accessKey,
            @Named("aws.secret.key") String secretKey,
            @Named("environment.name") String environmentName) {

        return new SecretsManagerConfig(endpoint, region, accessKey, secretKey, environmentName);
    }

    /**
     * Создаёт и предоставляет {@link LocalStackConfig} для работы с LocalStack.
     * <p>
     * LocalStackConfig предоставляет клиенты для:
     * <ul>
     *     <li>S3 — объектное хранилище</li>
     *     <li>Transcribe — распознавание речи</li>
     *     <li>EventBridge — планирование событий</li>
     *     <li>Lambda — serverless функции</li>
     *     <li>OpenSearch — поисковый движок</li>
     *     <li>CloudWatch — мониторинг</li>
     * </ul>
     *
     * @param secretsManagerConfig конфигурация Secrets Manager
     * @param endpoint URL эндпоинта AWS/LocalStack
     * @param region AWS регион
     * @param accessKey AWS access key
     * @param secretKey AWS secret key
     * @param email email администратора
     * @param openSearchPort порт OpenSearch
     * @param openSearchHost хост OpenSearch
     * @param truststorePath путь к truststore для SSL
     * @param truststorePassword пароль truststore
     * @return настроенный LocalStackConfig
     * @see LocalStackConfig
     */
    @Provides
    @Singleton
    public LocalStackConfig createLocalStackConfig(
            SecretsManagerConfig secretsManagerConfig,
            @Named("aws.endpoint.url") String endpoint,
            @Named("aws.region") String region,
            @Named("aws.access.key") String accessKey,
            @Named("aws.secret.key") String secretKey,
            @Named("email") String email,
            @Named("opensearch.port") String openSearchPort,
            @Named("opensearch.host") String openSearchHost,
            @Named("opensearch.truststore.path") String truststorePath,
            @Named("opensearch.truststore.password") String truststorePassword) {

        return new LocalStackConfig(endpoint, region, accessKey, secretKey,
                email, openSearchPort, openSearchHost,
                truststorePath, truststorePassword,
                secretsManagerConfig);
    }

    /**
     * Создаёт и предоставляет {@link GRPCConfig} для gRPC клиента.
     * <p>
     * GRPCConfig настраивает соединение с NLP сервисом через gRPC:
     * <ul>
     *     <li>Хост и порт NLP сервиса</li>
     *     <li>TLS/SSL настройки</li>
     *     <li>Аутентификация через API key</li>
     *     <li>Keep-alive настройки</li>
     * </ul>
     *
     * @param secretsManagerConfig конфигурация Secrets Manager для получения API key
     * @return настроенный GRPCConfig
     * @see GRPCConfig
     */
    @Provides
    @Singleton
    public GRPCConfig createGRPCConfig(SecretsManagerConfig secretsManagerConfig) {
        return new GRPCConfig(secretsManagerConfig);
    }

    /**
     * Создаёт и предоставляет {@link WebServer} для обработки HTTP запросов.
     * <p>
     * WebServer настраивает Jetty сервер с:
     * <ul>
     *     <li>Jersey JAX-RS для REST API</li>
     *     <li>Фильтрами (RateLimiterFilter, CorsFilter, SessionAuthFilter)</li>
     *     <li>Статическими ресурсами (веб-интерфейс)</li>
     * </ul>
     *
     * @param reminderResource ресурс для управления напоминаниями
     * @param authResource ресурс для аутентификации
     * @param metricsResource ресурс для метрик Prometheus
     * @param passwordResetResource ресурс для сброса пароля
     * @param rateLimiterFilter фильтр для rate limiting
     * @param portStr порт веб-сервера (из переменных окружения)
     * @return настроенный WebServer
     * @see WebServer
     */
    @Provides
    @Singleton
    public WebServer createWebServer(
            ReminderResource reminderResource,
            AuthResource authResource,
            MetricsResource metricsResource,
            PasswordResetResource passwordResetResource,
            RateLimiterFilter rateLimiterFilter,
            SessionAuthFilter sessionAuthFilter,
            CorsConfig corsConfig,
            @Named("ws.port") String portStr) {

        int webServerPort;
        try {
            webServerPort = Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
            webServerPort = 8090;
        }

        return new WebServer(webServerPort, reminderResource, authResource,
                metricsResource, passwordResetResource, rateLimiterFilter, sessionAuthFilter, corsConfig);
    }

    /**
     * Создаёт и предоставляет {@link MonitoringConfig} для Micrometer.
     * <p>
     * MonitoringConfig настраивает:
     * <ul>
     *     <li>CloudWatch Meter Registry для AWS CloudWatch</li>
     *     <li>Prometheus Meter Registry для Prometheus scraping</li>
     *     <li>JVM метрики (memory, GC, threads)</li>
     * </ul>
     *
     * @param localStackConfig конфигурация LocalStack для CloudWatch клиента
     * @param secretsManagerConfig конфигурация Secrets Manager
     * @return настроенный MonitoringConfig
     * @see MonitoringConfig
     */
    @Provides
    @Singleton
    public MonitoringConfig createCloudWatchConfig(
            LocalStackConfig localStackConfig,
            SecretsManagerConfig secretsManagerConfig) {
        return new MonitoringConfig(localStackConfig, secretsManagerConfig);
    }

    /**
     * Создаёт и предоставляет {@link LambdaAsyncClient} для работы с AWS Lambda.
     *
     * @param localStackConfig конфигурация LocalStack
     * @return LambdaAsyncClient для вызова Lambda функций
     * @see LambdaAsyncClient
     */
    @Provides
    @Singleton
    public LambdaAsyncClient provideLambdaAsyncClient(LocalStackConfig localStackConfig) {
        return localStackConfig.getLambdaAsyncClient();
    }

    /**
     * Получает значение переменной окружения или системного свойства.
     * <p>
     * Системные свойства имеют приоритет над переменными окружения.
     *
     * @param envVar имя переменной окружения или системного свойства
     * @param defaultValue значение по умолчанию, если не найдено
     * @return значение переменной окружения, системного свойства или defaultValue
     */
    private String getEnvOrDefault(String envVar, String defaultValue) {
        return Optional.ofNullable(System.getenv(envVar))
                .or(() -> Optional.ofNullable(System.getProperty(envVar)))
                .orElse(defaultValue);
    }
}