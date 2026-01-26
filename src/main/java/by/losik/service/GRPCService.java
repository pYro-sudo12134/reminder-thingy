package by.losik.service;

import by.losik.config.GRPCConfig;
import by.losik.dto.Entity;
import by.losik.dto.ParsedResult;
import by.losik.grpc.AbsoluteTime;
import by.losik.grpc.HealthRequest;
import by.losik.grpc.HealthResponse;
import by.losik.grpc.ParseRequest;
import by.losik.grpc.ParseResponse;
import by.losik.grpc.RelativeTime;
import by.losik.grpc.ReminderParserServiceGrpc;
import by.losik.grpc.TemporalExpression;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Singleton
public class GRPCService {
    private static final Logger log = LoggerFactory.getLogger(GRPCService.class);
    private final GRPCConfig grpcConfig;
    private final ReminderParserServiceGrpc.ReminderParserServiceBlockingStub blockingStub;
    private volatile boolean grpcAvailable = true;
    private long lastHealthCheck = 0;
    private static final long HEALTH_CHECK_INTERVAL_MS = 30000;

    @Inject
    public GRPCService(GRPCConfig grpcConfig) {
        this.grpcConfig = grpcConfig;
        ManagedChannel channel = grpcConfig.getChannel();
        this.blockingStub = ReminderParserServiceGrpc.newBlockingStub(channel);

        checkGRPCAvailability();
    }

    public ParsedResult parse(String text, String language, String userId) {
        if (!isGRPCAvailable()) {
            log.warn("gRPC service unavailable, using fallback parsing");
            return fallbackParse(text, language);
        }

        try {
            ParseRequest request = ParseRequest.newBuilder()
                    .setText(text)
                    .setLanguageCode(language != null ? language : "")
                    .setUserId(userId != null ? userId : "anonymous")
                    .build();

            ParseResponse response = blockingStub
                    .withDeadlineAfter(5, TimeUnit.SECONDS)
                    .parseReminder(request);

            return mapResponseToResult(response);

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

    private ParsedResult mapResponseToResult(ParseResponse response) {
        try {
            LocalDateTime scheduledTime = extractDateTime(response.getParsed().getTimeExpression());

            List<Entity> entities = response.getParsed().getEntitiesList().stream()
                    .map(this::mapProtoEntity)
                    .collect(Collectors.toList());

            return new ParsedResult(
                    scheduledTime,
                    response.getParsed().getAction(),
                    response.getConfidence(),
                    response.getLanguageDetected(),
                    response.getReminderId(),
                    "reminder",
                    entities,
                    response.getParsed().getNormalizedText(),
                    response.getParsed().getNormalizedText()
            );

        } catch (Exception e) {
            log.error("Error mapping gRPC response", e);
            throw new RuntimeException("Invalid gRPC response format", e);
        }
    }

    private LocalDateTime extractDateTime(TemporalExpression timeExpr) {
        try {
            if (timeExpr.hasAbsolute()) {
                AbsoluteTime absolute = timeExpr.getAbsolute();
                return LocalDateTime.parse(absolute.getIsoDatetime());

            } else if (timeExpr.hasRelative()) {
                RelativeTime relative = timeExpr.getRelative();
                LocalDateTime now = LocalDateTime.now();

                return switch (relative.getUnit()) {
                    case "seconds" -> now.plusSeconds(relative.getAmount());
                    case "minutes" -> now.plusMinutes(relative.getAmount());
                    case "hours" -> now.plusHours(relative.getAmount());
                    case "days" -> now.plusDays(relative.getAmount());
                    case "weeks" -> now.plusWeeks(relative.getAmount());
                    default -> now.plusHours(1);
                };

            } else if (timeExpr.hasRecurring()) {
                return LocalDateTime.now().plusDays(1);
            }
        } catch (DateTimeParseException e) {
            log.warn("Failed to parse date from gRPC response", e);
        }

        return LocalDateTime.now().plusHours(1);
    }

    private Entity mapProtoEntity(by.losik.grpc.Entity protoEntity) {
        return new Entity(
                protoEntity.getText(),
                protoEntity.getType(),
                protoEntity.getStart(),
                protoEntity.getEnd(),
                protoEntity.getConfidence()
        );
    }

    private ParsedResult fallbackParse(String text, String language) {
        LocalDateTime scheduledTime = LocalDateTime.now().plusHours(1);

        return new ParsedResult(
                scheduledTime,
                text,
                0.5,
                language != null ? language : "ru",
                null,
                "reminder",
                List.of(),
                text,
                text.toLowerCase()
        );
    }

    public void checkHealth() {
        try {
            HealthResponse response = blockingStub
                    .withDeadlineAfter(3, TimeUnit.SECONDS)
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

    private void checkGRPCAvailability() {
        long now = System.currentTimeMillis();
        if (now - lastHealthCheck > HEALTH_CHECK_INTERVAL_MS) {
            checkHealth();
        }
    }

    private boolean isGRPCAvailable() {
        if (!grpcAvailable) {
            checkGRPCAvailability();
        }
        return grpcAvailable;
    }

    private void markGRPCUnavailable() {
        grpcAvailable = false;
        lastHealthCheck = System.currentTimeMillis();
    }

    public List<String> getSupportedLanguages() {
        try {
            HealthResponse response = blockingStub
                    .withDeadlineAfter(3, TimeUnit.SECONDS)
                    .healthCheck(HealthRequest.newBuilder().build());

            return response.getSupportedLanguagesList();

        } catch (Exception e) {
            log.warn("Failed to get supported languages", e);
            return List.of("ru", "en");
        }
    }

    public String formatForDisplay(LocalDateTime time) {
        return time.format(DateTimeFormatter.ofPattern("HH:mm"));
    }

    public void shutdown() {
        grpcConfig.shutdown();
    }
}