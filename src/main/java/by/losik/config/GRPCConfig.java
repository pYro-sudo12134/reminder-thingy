package by.losik.config;

import by.losik.util.ConfigUtils;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;

import java.util.concurrent.TimeUnit;

/**
 * Конфигурация gRPC клиента для связи с NLP сервисом.
 * <p>
 * Предоставляет:
 * <ul>
 *     <li>Управление каналом связи (ManagedChannel)</li>
 *     <li>Настройки TLS/SSL</li>
 *     <li>Аутентификацию через API key</li>
 *     <li>Keep-alive настройки</li>
 *     <li>Таймауты для запросов и health check</li>
 * </ul>
 * <p>
 * Используется в {@link by.losik.service.GRPCService} для парсинга напоминаний.
 *
 * @see by.losik.service.GRPCService
 * @see ManagedChannel
 */
@Singleton
public class GRPCConfig {
    private final SecretsManagerConfig secretsManager;
    private final String nlpServiceHost;
    private final int nlpServicePort;
    private final boolean useTLS;
    private final long healthCheckIntervalMs;
    private final long parseDeadlineSec;
    private final long healthCheckDeadlineSec;
    private ManagedChannel channel;

    /** Таймаут парсинга по умолчанию (30 секунд) */
    private static final long DEFAULT_PARSE_DEADLINE_SEC = 180L;

    /** Таймаут health check по умолчанию (5 секунд) */
    private static final long DEFAULT_HEALTH_CHECK_DEADLINE_SEC = 5L;

    /** Интервал health check по умолчанию (30 секунд) */
    private static final long DEFAULT_HEALTH_CHECK_INTERVAL_MS = 30000L;

    /**
     * Создаёт конфигурацию gRPC клиента.
     *
     * @param secretsManager SecretsManagerConfig для получения настроек
     */
    @Inject
    public GRPCConfig(SecretsManagerConfig secretsManager) {
        this.secretsManager = secretsManager;
        this.nlpServiceHost = ConfigUtils.getEnvOrDefault("NLP_SERVICE_HOST", "localhost");
        this.nlpServicePort = ConfigUtils.getIntEnvOrDefault("NLP_SERVICE_PORT", 50051);
        this.useTLS = ConfigUtils.getBooleanEnvOrDefault("GRPC_USE_TLS", false);
        this.healthCheckIntervalMs = ConfigUtils.getLongEnvOrDefault("GRPC_HEALTH_CHECK_INTERVAL_MS", DEFAULT_HEALTH_CHECK_INTERVAL_MS);
        this.parseDeadlineSec = ConfigUtils.getLongEnvOrDefault("GRPC_PARSE_DEADLINE_SEC", DEFAULT_PARSE_DEADLINE_SEC);
        this.healthCheckDeadlineSec = ConfigUtils.getLongEnvOrDefault("GRPC_HEALTH_CHECK_DEADLINE_SEC", DEFAULT_HEALTH_CHECK_DEADLINE_SEC);
    }

    /**
     * Получает или создаёт gRPC канал.
     * <p>
     * Канал создаётся лениво (lazy initialization) с настройками:
     * <ul>
     *     <li>TLS если useTLS=true, иначе plaintext</li>
     *     <li>Аутентификация через Bearer token (если API key задан)</li>
     *     <li>Максимальный размер сообщения 100MB</li>
     *     <li>Keep-alive 30 секунд</li>
     * </ul>
     *
     * @return ManagedChannel для связи с NLP сервисом
     */
    public ManagedChannel getChannel() {
        if (channel == null || channel.isShutdown() || channel.isTerminated()) {
            synchronized (this) {
                if (channel == null || channel.isShutdown() || channel.isTerminated()) {
                    ManagedChannelBuilder<?> builder = ManagedChannelBuilder
                            .forAddress(nlpServiceHost, nlpServicePort);

                    if (useTLS) {
                        builder.useTransportSecurity();
                    } else {
                        builder.usePlaintext();
                    }

                    String apiKey = secretsManager.getSecret("NLP_GRPC_API_KEY");
                    if (apiKey != null && !apiKey.isEmpty()) {
                        builder.intercept(createAuthInterceptor(apiKey));
                    }

                    channel = builder
                            .maxInboundMessageSize(100 * 1024 * 1024)
                            .keepAliveTime(30, TimeUnit.SECONDS)
                            .keepAliveTimeout(30, TimeUnit.SECONDS)
                            .build();
                }
            }
        }
        return channel;
    }

    /**
     * Создаёт interceptor для аутентификации через Bearer token.
     *
     * @param apiKey API key для аутентификации
     * @return ClientInterceptor с заголовками Authorization
     */
    private io.grpc.ClientInterceptor createAuthInterceptor(String apiKey) {
        return MetadataUtils.newAttachHeadersInterceptor(createHeadersWithAuth(apiKey));
    }

    /**
     * Создаёт заголовки для аутентификации.
     *
     * @param apiKey API key
     * @return Metadata с заголовками Authorization и X-Service-Name
     */
    private Metadata createHeadersWithAuth(String apiKey) {
        Metadata headers = new Metadata();
        Metadata.Key<String> authKey = Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);
        headers.put(authKey, "Bearer " + apiKey);

        Metadata.Key<String> serviceKey = Metadata.Key.of("x-service-name", Metadata.ASCII_STRING_MARSHALLER);
        headers.put(serviceKey, "voice-reminder-service");

        return headers;
    }

    /**
     * Закрывает gRPC канал.
     * <p>
     * Пытается корректно завершить соединения с таймаутом 5 секунд.
     */
    public void shutdown() {
        if (channel != null && !channel.isShutdown()) {
            channel.shutdown();
            try {
                if (!channel.awaitTermination(5, TimeUnit.SECONDS)) {
                    channel.shutdownNow();
                }
            } catch (InterruptedException e) {
                channel.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Получает хост NLP сервиса.
     * @return хост NLP сервиса
     */
    public String getNlpServiceHost() {
        return nlpServiceHost;
    }

    /**
     * Получает порт NLP сервиса.
     * @return порт NLP сервиса
     */
    public int getNlpServicePort() {
        return nlpServicePort;
    }

    /**
     * Проверяет доступность gRPC канала.
     * @return true если канал доступен
     */
    public boolean isAvailable() {
        try {
            ManagedChannel testChannel = getChannel();
            return !testChannel.isShutdown() && !testChannel.isTerminated();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Получает интервал health check в миллисекундах.
     *
     * @return интервал health check (по умолчанию 30000 мс)
     */
    public long getHealthCheckIntervalMs() {
        return healthCheckIntervalMs;
    }

    /**
     * Получает таймаут на парсинг в секундах.
     *
     * @return таймаут парсинга (по умолчанию 30 сек)
     */
    public long getParseDeadlineSec() {
        return parseDeadlineSec;
    }

    /**
     * Получает таймаут на health check в секундах.
     *
     * @return таймаут health check (по умолчанию 5 сек)
     */
    public long getHealthCheckDeadlineSec() {
        return healthCheckDeadlineSec;
    }
}