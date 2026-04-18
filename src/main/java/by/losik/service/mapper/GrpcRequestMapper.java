package by.losik.service.mapper;

import by.losik.dto.Entity;
import by.losik.dto.ParsedResult;
import by.losik.grpc.AbsoluteTime;
import by.losik.grpc.ParseRequest;
import by.losik.grpc.ParseResponse;
import by.losik.grpc.RelativeTime;
import by.losik.grpc.TemporalExpression;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Маппер для преобразования между gRPC protobuf и DTO.
 * <p>
 * Отвечает за:
 * <ul>
 *     <li>Создание ParseRequest из параметров</li>
 *     <li>Преобразование ParseResponse в ParsedResult</li>
 *     <li>Преобразование protobuf Entity в DTO Entity</li>
 *     <li>Извлечение LocalDateTime из TemporalExpression</li>
 * </ul>
 */
@Singleton
public class GrpcRequestMapper {

    private static final Logger log = LoggerFactory.getLogger(GrpcRequestMapper.class);

    /**
     * Создаёт запрос на парсинг напоминания.
     *
     * @param text текст напоминания
     * @param language код языка (например, "ru", "en")
     * @param userId ID пользователя
     * @return ParseRequest для отправки в gRPC сервис
     */
    public ParseRequest createParseRequest(String text, String language, String userId) {
        return ParseRequest.newBuilder()
                .setText(text)
                .setLanguageCode(language != null ? language : "")
                .setUserId(userId != null ? userId : "anonymous")
                .build();
    }

    /**
     * Преобразует ответ gRPC сервиса в ParsedResult.
     *
     * @param response ответ от gRPC сервиса
     * @return ParsedResult для использования в приложении
     * @throws RuntimeException если не удалось распарсить ответ
     */
    public ParsedResult mapResponseToResult(ParseResponse response) {
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

    /**
     * Преобразует protobuf Entity в DTO Entity.
     *
     * @param protoEntity protobuf сущность
     * @return DTO сущность
     */
    public Entity mapProtoEntity(by.losik.grpc.Entity protoEntity) {
        return new Entity(
                protoEntity.getText(),
                protoEntity.getType(),
                protoEntity.getStart(),
                protoEntity.getEnd(),
                protoEntity.getConfidence()
        );
    }

    /**
     * Извлекает LocalDateTime из protobuf TemporalExpression.
     * <p>
     * Поддерживает типы:
     * <ul>
     *     <li>AbsoluteTime — абсолютная дата и время</li>
     *     <li>RelativeTime — относительное время (через N секунд/минут/часов)</li>
     *     <li>RecurringTime — повторяющееся время (возвращает +1 день)</li>
     * </ul>
     *
     * @param timeExpr protobuf выражение времени
     * @return LocalDateTime или null если не удалось извлечь
     */
    public LocalDateTime extractDateTime(TemporalExpression timeExpr) {
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
        } catch (Exception e) {
            log.warn("Failed to parse date from gRPC response", e);
        }

        return null;
    }
}
