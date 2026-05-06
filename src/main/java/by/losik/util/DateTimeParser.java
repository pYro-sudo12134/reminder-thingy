package by.losik.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.Optional;

public final class DateTimeParser {

    private static final Logger log = LoggerFactory.getLogger(DateTimeParser.class);

    private static final DateTimeFormatter ISO_DATE_TIME_FORMATTER = DateTimeFormatter.ISO_DATE_TIME;

    private static final DateTimeFormatter FLEXIBLE_ZONED_FORMATTER = new DateTimeFormatterBuilder()
            .append(DateTimeFormatter.ofPattern("yyyy-MM-dd['T'][ ]HH:mm:ss"))
            .optionalStart()
            .appendOffset("+HH:MM", "+00:00")
            .optionalEnd()
            .optionalStart()
            .appendOffset("+HHMM", "+0000")
            .optionalEnd()
            .optionalStart()
            .appendZoneRegionId()
            .optionalEnd()
            .toFormatter();

    public static LocalDateTime parseLocalDateTime(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.trim().isEmpty()) {
            throw new DateTimeParseException("Date string is null or empty", dateTimeStr, 0);
        }

        String trimmedStr = dateTimeStr.trim();

        try {
            return LocalDateTime.parse(trimmedStr, ISO_DATE_TIME_FORMATTER);
        } catch (DateTimeParseException e) {
            log.debug("Failed to parse as ISO_DATE_TIME: {}", e.getMessage());
        }

        try {
            OffsetDateTime offsetDateTime = OffsetDateTime.parse(trimmedStr);
            return offsetDateTime.toLocalDateTime();
        } catch (DateTimeParseException e) {
            log.debug("Failed to parse as OffsetDateTime: {}", e.getMessage());
        }

        try {
            return LocalDateTime.parse(trimmedStr);
        } catch (DateTimeParseException e) {
            log.debug("Failed to parse as simple LocalDateTime: {}", e.getMessage());
        }

        log.error("Failed to parse date string: {}", dateTimeStr);
        throw new DateTimeParseException(
                "Unable to parse date string: " + dateTimeStr,
                dateTimeStr,
                0
        );
    }

    public static ZonedDateTime parseZonedDateTime(String dateTimeStr, ZoneId defaultZone) {
        if (dateTimeStr == null || dateTimeStr.trim().isEmpty()) {
            throw new DateTimeParseException("Date string is null or empty", dateTimeStr, 0);
        }

        String trimmedStr = dateTimeStr.trim();

        try {
            return ZonedDateTime.parse(trimmedStr);
        } catch (DateTimeParseException e) {
            log.debug("Failed to parse as ZonedDateTime with zone info: {}", e.getMessage());
        }

        try {
            OffsetDateTime offsetDateTime = OffsetDateTime.parse(trimmedStr);
            return offsetDateTime.atZoneSameInstant(defaultZone);
        } catch (DateTimeParseException e) {
            log.debug("Failed to parse as OffsetDateTime: {}", e.getMessage());
        }

        try {
            LocalDateTime ldt = LocalDateTime.parse(trimmedStr);
            return ldt.atZone(defaultZone);
        } catch (DateTimeParseException e) {
            log.debug("Failed to parse as LocalDateTime: {}", e.getMessage());
        }

        log.error("Failed to parse date string as ZonedDateTime: {}", dateTimeStr);
        throw new DateTimeParseException(
                "Unable to parse date string: " + dateTimeStr,
                dateTimeStr,
                0
        );
    }

    public static OffsetDateTime parseOffsetDateTime(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.trim().isEmpty()) {
            throw new DateTimeParseException("Date string is null or empty", dateTimeStr, 0);
        }

        return OffsetDateTime.parse(dateTimeStr.trim());
    }

    private DateTimeParser() {
        throw new UnsupportedOperationException("Utility class");
    }
}
