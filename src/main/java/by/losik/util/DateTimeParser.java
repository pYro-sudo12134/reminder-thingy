package by.losik.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Утилитный класс для парсинга дат и времени.
 * <p>
 * Поддерживает различные форматы дат:
 * <ul>
 *     <li>ISO_DATE_TIME (yyyy-MM-dd'T'HH:mm:ss)</li>
 *     <li>OffsetDateTime с часовым поясом</li>
 *     <li>Простой LocalDateTime (yyyy-MM-dd HH:mm:ss)</li>
 * </ul>
 */
public final class DateTimeParser {

    private static final Logger log = LoggerFactory.getLogger(DateTimeParser.class);

    /** Формат ISO_DATE_TIME */
    private static final DateTimeFormatter ISO_DATE_TIME_FORMATTER = DateTimeFormatter.ISO_DATE_TIME;

    /**
     * Парсит строку в LocalDateTime.
     * <p>
     * Поддерживает форматы:
     * <ul>
     *     <li>ISO_DATE_TIME (yyyy-MM-dd'T'HH:mm:ss)</li>
     *     <li>OffsetDateTime с часовым поясом (yyyy-MM-dd'T'HH:mm:ssZ)</li>
     *     <li>Простой LocalDateTime (yyyy-MM-dd HH:mm:ss)</li>
     * </ul>
     *
     * @param dateTimeStr строка с датой и временем
     * @return LocalDateTime
     * @throws DateTimeParseException если ни один формат не подошёл
     */
    public static LocalDateTime parseLocalDateTime(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.trim().isEmpty()) {
            throw new DateTimeParseException("Date string is null or empty", dateTimeStr, 0);
        }

        String trimmedStr = dateTimeStr.trim();

        // Попытка 1: ISO_DATE_TIME
        try {
            return LocalDateTime.parse(trimmedStr, ISO_DATE_TIME_FORMATTER);
        } catch (DateTimeParseException e) {
            log.debug("Failed to parse as ISO_DATE_TIME: {}", e.getMessage());
        }

        // Попытка 2: OffsetDateTime с конвертацией
        try {
            OffsetDateTime offsetDateTime = OffsetDateTime.parse(trimmedStr);
            return offsetDateTime.toLocalDateTime();
        } catch (DateTimeParseException e) {
            log.debug("Failed to parse as OffsetDateTime: {}", e.getMessage());
        }

        // Попытка 3: Простой формат
        try {
            return LocalDateTime.parse(trimmedStr);
        } catch (DateTimeParseException e) {
            log.debug("Failed to parse as simple LocalDateTime: {}", e.getMessage());
        }

        // Все попытки исчерпаны
        log.error("Failed to parse date string: {}", dateTimeStr);
        throw new DateTimeParseException(
                "Unable to parse date string: " + dateTimeStr,
                dateTimeStr,
                0
        );
    }

    /**
     * Парсит строку в OffsetDateTime.
     *
     * @param dateTimeStr строка с датой и временем
     * @return OffsetDateTime
     * @throws DateTimeParseException если не удалось распарсить
     */
    public static OffsetDateTime parseOffsetDateTime(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.trim().isEmpty()) {
            throw new DateTimeParseException("Date string is null or empty", dateTimeStr, 0);
        }

        return OffsetDateTime.parse(dateTimeStr.trim());
    }

    // Запрещаем создание экземпляров
    private DateTimeParser() {
        throw new UnsupportedOperationException("Utility class");
    }
}
