package by.losik.service;

import by.losik.config.GRPCConfig;
import by.losik.dto.ParsedResult;
import by.losik.grpc.HealthRequest;
import by.losik.grpc.HealthResponse;
import by.losik.grpc.ParseRequest;
import by.losik.grpc.ParseResponse;
import by.losik.grpc.ReminderParserServiceGrpc;
import by.losik.service.mapper.GrpcRequestMapper;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Сервис для взаимодействия с NLP сервисом через gRPC.
 * <p>
 * Предоставляет методы для:
 * <ul>
 *     <li>Парсинга текста напоминания (извлечение действия и времени)</li>
 *     <li>Проверки здоровья NLP сервиса (health check)</li>
 *     <li>Получения списка поддерживаемых языков</li>
 * </ul>
 * <p>
 * Использует Circuit breaker паттерн для обработки недоступности сервиса.
 * При недоступности gRPC используется fallback (упрощённый парсинг).
 *
 * @see GRPCConfig
 * @see GrpcRequestMapper
 */
@Singleton
public class GRPCService implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(GRPCService.class);
    private final GRPCConfig grpcConfig;
    private final GrpcRequestMapper mapper;
    private final ReminderParserServiceGrpc.ReminderParserServiceBlockingStub blockingStub;
    private volatile boolean grpcAvailable = true;
    private long lastHealthCheck = 0;

    /**
     * Создаёт gRPC сервис с конфигурацией и маппером.
     *
     * @param grpcConfig конфигурация gRPC клиента
     * @param mapper маппер для преобразования protobuf ↔ DTO
     */
    @Inject
    public GRPCService(GRPCConfig grpcConfig, GrpcRequestMapper mapper) {
        this.grpcConfig = grpcConfig;
        this.mapper = mapper;
        this.blockingStub = ReminderParserServiceGrpc.newBlockingStub(grpcConfig.getChannel());
        this.lastHealthCheck = 0;
    }

    /**
     * Парсит текст напоминания через gRPC сервис.
     * <p>
     * Если gRPC сервис недоступен, используется fallback парсинг.
     *
     * @param text текст напоминания
     * @param language код языка (например, "ru", "en")
     * @param userId ID пользователя
     * @return ParsedResult с извлечёнными данными
     */
    public ParsedResult parse(String text, String language, String userId) {
        if (!isGRPCAvailable()) {
            log.warn("gRPC service unavailable, using fallback parsing");
            return fallbackParse(text, language);
        }

        try {
            ParseRequest request = mapper.createParseRequest(text, language, userId);

            ParseResponse response = blockingStub
                    .withDeadlineAfter(grpcConfig.getParseDeadlineSec(), TimeUnit.SECONDS)
                    .parseReminder(request);

            return mapper.mapResponseToResult(response);

        } catch (StatusRuntimeException e) {
            log.error("gRPC parsing error: {} - {}", e.getStatus().getCode(), e.getStatus().getDescription());

            if (e.getStatus().getCode() == io.grpc.Status.Code.UNAVAILABLE ||
                    e.getStatus().getCode() == io.grpc.Status.Code.DEADLINE_EXCEEDED) {
                markGRPCUnavailable();
            }

            return fallbackParse(text, language);

        } catch (Exception e) {
            log.error("Error parsing via gRPC", e);
            return fallbackParse(text, language);
        }
    }

    /**
     * Fallback парсинг при недоступности gRPC сервиса.
     * <p>
     * Возвращает упрощённый результат с текущим временем +1 час.
     *
     * @param text текст напоминания
     * @param language код языка
     * @return ParsedResult с дефолтными значениями
     */
    private ParsedResult fallbackParse(String text, String language) {
        LocalDateTime scheduledTime = LocalDateTime.now().plusHours(1);

        return new ParsedResult(
                scheduledTime,
                text,
                0.0,
                language != null ? language : "ru",
                null,
                "reminder",
                List.of(),
                text,
                text.toLowerCase()
        );
    }

    /**
     * Получает список поддерживаемых языков от NLP сервиса.
     *
     * @return список кодов языков (например, ["ru", "en"])
     */
    public List<String> getSupportedLanguages() {
        try {
            HealthResponse response = blockingStub
                    .withDeadlineAfter(grpcConfig.getHealthCheckDeadlineSec(), TimeUnit.SECONDS)
                    .healthCheck(HealthRequest.newBuilder().build());

            return response.getSupportedLanguagesList();

        } catch (Exception e) {
            log.warn("Failed to get supported languages", e);
            return List.of("ru", "en");
        }
    }

    /**
     * Форматирует время для отображения пользователю.
     *
     * @param time время для форматирования
     * @return отформатированное время (например, "09:00")
     */
    public String formatForDisplay(LocalDateTime time) {
        return time.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
    }

    /**
     * Проверяет здоровье NLP сервиса через gRPC health check.
     */
    public void checkHealth() {
        try {
            HealthResponse response = blockingStub
                    .withDeadlineAfter(grpcConfig.getHealthCheckDeadlineSec(), TimeUnit.SECONDS)
                    .healthCheck(HealthRequest.newBuilder().build());

            grpcAvailable = response.getHealthy();
            lastHealthCheck = System.currentTimeMillis();

            log.info("NLP gRPC service health: {}, version: {}",
                    response.getHealthy(), response.getModelVersion());

        } catch (Exception e) {
            log.warn("NLP gRPC service unavailable: {}", e.getMessage());
            grpcAvailable = false;
            lastHealthCheck = System.currentTimeMillis();
        }
    }

    /**
     * Проверяет доступность gRPC сервиса.
     * <p>
     * Если прошло больше интервала health check, выполняет проверку.
     *
     * @return true если сервис доступен
     */
    private boolean isGRPCAvailable() {
        if (!grpcAvailable) {
            checkGRPCAvailability();
        }
        return grpcAvailable;
    }

    /**
     * Проверяет доступность gRPC сервиса если прошло достаточно времени.
     */
    private void checkGRPCAvailability() {
        long now = System.currentTimeMillis();
        if (now - lastHealthCheck > grpcConfig.getHealthCheckIntervalMs()) {
            checkHealth();
        }
    }

    /**
     * Помечает gRPC сервис как недоступный.
     */
    private void markGRPCUnavailable() {
        grpcAvailable = false;
        lastHealthCheck = System.currentTimeMillis();
    }

    @Override
    public void close() {
        grpcConfig.shutdown();
    }
}