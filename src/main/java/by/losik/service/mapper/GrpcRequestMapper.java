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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.stream.Collectors;

@Singleton
public class GrpcRequestMapper {

    private static final Logger log = LoggerFactory.getLogger(GrpcRequestMapper.class);

    private static final ZoneId USER_ZONE = ZoneId.of("UTC+3");

    public ParseRequest createParseRequest(String text, String language, String userId) {
        return ParseRequest.newBuilder()
                .setText(text)
                .setLanguageCode(language != null ? language : "")
                .setUserId(userId != null ? userId : "anonymous")
                .build();
    }

    public ParsedResult mapResponseToResult(ParseResponse response) {
        try {
            ZonedDateTime scheduledTime = extractDateTime(response.getParsed().getTimeExpression());

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

    public Entity mapProtoEntity(by.losik.grpc.Entity protoEntity) {
        return new Entity(
                protoEntity.getText(),
                protoEntity.getType(),
                protoEntity.getStart(),
                protoEntity.getEnd(),
                protoEntity.getConfidence()
        );
    }

    private static final DateTimeFormatter[] DATETIME_FORMATS = {
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            DateTimeFormatter.ISO_DATE_TIME,
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"),
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
    };

    public ZonedDateTime extractDateTime(TemporalExpression timeExpr) {
        try {
            if (timeExpr.hasAbsolute()) {
                AbsoluteTime absolute = timeExpr.getAbsolute();
                String isoDatetime = absolute.getIsoDatetime();

                log.warn("NLP returned isoDatetime: '{}'", isoDatetime);

                if (isoDatetime == null || isoDatetime.isBlank()) {
                    log.warn("NLP returned empty datetime, using default: now + 1 hour");
                    return ZonedDateTime.now(USER_ZONE).plusHours(1);
                }

                isoDatetime = isoDatetime.replace("{today}", LocalDate.now(USER_ZONE).toString());

                for (DateTimeFormatter formatter : DATETIME_FORMATS) {
                    try {
                        LocalDateTime ldt = LocalDateTime.parse(isoDatetime, formatter);
                        return ldt.atZone(USER_ZONE);
                    } catch (DateTimeParseException e) {
                        // пробуем следующий формат
                    }
                }

                log.warn("Could not parse datetime '{}' with any known format, using default: now + 1 hour", isoDatetime);
                return ZonedDateTime.now(USER_ZONE).plusHours(1);

            } else if (timeExpr.hasRelative()) {
                RelativeTime relative = timeExpr.getRelative();
                ZonedDateTime now = ZonedDateTime.now(USER_ZONE);

                return switch (relative.getUnit()) {
                    case "seconds" -> now.plusSeconds(relative.getAmount());
                    case "minutes" -> now.plusMinutes(relative.getAmount());
                    case "hours" -> now.plusHours(relative.getAmount());
                    case "days" -> now.plusDays(relative.getAmount());
                    case "weeks" -> now.plusWeeks(relative.getAmount());
                    default -> now.plusHours(1);
                };

            } else if (timeExpr.hasRecurring()) {
                return ZonedDateTime.now(USER_ZONE).plusDays(1);
            } else {
                log.warn("NLP returned empty TemporalExpression, using default: now + 1 hour");
                return ZonedDateTime.now(USER_ZONE).plusHours(1);
            }
        } catch (Exception e) {
            log.warn("Failed to parse date from gRPC response", e);
            return ZonedDateTime.now(USER_ZONE).plusHours(1);
        }
    }
}
