package by.losik.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Утилитный класс для создания cron выражений AWS EventBridge.
 * <p>
 * Поддерживаемые форматы:
 * <ul>
 *     <li>Абсолютное время — запуск в конкретную дату и время</li>
 *     <li>Rate выражения — запуск через интервал (например, "5 minutes")</li>
 *     <li>Ежедневные выражения — запуск каждый день в указанное время</li>
 *     <li>Еженедельные выражения — запуск каждый неделю в указанный день</li>
 * </ul>
 * <p>
 * Формат cron AWS EventBridge:
 * <pre>{@code cron(Minutes Hours Day-of-month Month Day-of-week Year)}</pre>
 *
 * @see <a href="https://docs.aws.amazon.com/eventbridge/latest/userguide/eb-create-rule-schedule.html">AWS EventBridge Schedule Expressions</a>
 */
public final class CronExpressionBuilder {

    /** Формат для года в cron выражении */
    private static final DateTimeFormatter YEAR_FORMATTER = DateTimeFormatter.ofPattern("yyyy");

    /** Формат для месяца в cron выражении */
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("MM");

    /** Формат для дня месяца в cron выражении */
    private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.ofPattern("dd");

    /** Формат для часа в cron выражении */
    private static final DateTimeFormatter HOUR_FORMATTER = DateTimeFormatter.ofPattern("HH");

    /** Формат для минуты в cron выражении */
    private static final DateTimeFormatter MINUTE_FORMATTER = DateTimeFormatter.ofPattern("mm");

    /**
     * Создаёт cron выражение для абсолютного времени.
     * <p>
     * Пример: {@code cron(0 12 15 3 ? 2026)} — 15 марта 2026 в 12:00
     *
     * @param dateTime дата и время запуска
     * @return cron выражение в формате AWS EventBridge
     */
    public static String fromLocalDateTime(LocalDateTime dateTime) {
        String minute = dateTime.format(MINUTE_FORMATTER);
        String hour = dateTime.format(HOUR_FORMATTER);
        String dayOfMonth = dateTime.format(DAY_FORMATTER);
        String month = dateTime.format(MONTH_FORMATTER);
        String year = dateTime.format(YEAR_FORMATTER);

        return String.format("cron(%s %s %s %s ? %s)", minute, hour, dayOfMonth, month, year);
    }

    /**
     * Создаёт rate выражение для интервального запуска.
     * <p>
     * Примеры:
     * <ul>
     *     <li>{@code rate(5 minutes)} — каждые 5 минут</li>
     *     <li>{@code rate(1 hour)} — каждый час</li>
     *     <li>{@code rate(1 day)} — каждый день</li>
     * </ul>
     *
     * @param value количество единиц времени
     * @param unit единица времени ("minute", "hour", "day")
     * @return rate выражение в формате AWS EventBridge
     * @throws IllegalArgumentException если unit не поддерживается
     */
    public static String rate(int value, String unit) {
        if (!isValidRateUnit(unit)) {
            throw new IllegalArgumentException(
                    "Invalid rate unit: " + unit + ". Supported: minute, hour, day");
        }

        String unitPlural = value > 1 ? unit + "s" : unit;
        return String.format("rate(%d %s)", value, unitPlural);
    }

    /**
     * Создаёт cron выражение для ежедневного запуска.
     * <p>
     * Пример: {@code cron(0 9 ? * * *)} — каждый день в 09:00
     *
     * @param hourOfDay час запуска (0-23)
     * @param minuteOfHour минута запуска (0-59)
     * @return cron выражение для ежедневного запуска
     */
    public static String daily(int hourOfDay, int minuteOfHour) {
        return String.format("cron(%d %d ? * * *)", minuteOfHour, hourOfDay);
    }

    /**
     * Создаёт cron выражение для еженедельного запуска.
     * <p>
     * Пример: {@code cron(0 12 ? * MON *)} — каждый понедельник в 12:00
     *
     * @param hourOfDay час запуска (0-23)
     * @param minuteOfHour минута запуска (0-59)
     * @param dayOfWeek день недели (MON, TUE, WED, THU, FRI, SAT, SUN)
     * @return cron выражение для еженедельного запуска
     * @throws IllegalArgumentException если dayOfWeek не поддерживается
     */
    public static String weekly(int hourOfDay, int minuteOfHour, String dayOfWeek) {
        if (!isValidDayOfWeek(dayOfWeek)) {
            throw new IllegalArgumentException(
                    "Invalid day of week: " + dayOfWeek + ". Supported: MON, TUE, WED, THU, FRI, SAT, SUN");
        }

        return String.format("cron(%d %d ? * %s *)", minuteOfHour, hourOfDay, dayOfWeek);
    }

    /**
     * Проверяет, является ли единица времени допустимой для rate выражений.
     *
     * @param unit единица времени
     * @return true если единица допустима
     */
    private static boolean isValidRateUnit(String unit) {
        return "minute".equalsIgnoreCase(unit) ||
                "hour".equalsIgnoreCase(unit) ||
                "day".equalsIgnoreCase(unit);
    }

    /**
     * Проверяет, является ли день недели допустимым.
     *
     * @param dayOfWeek день недели
     * @return true если день допустим
     */
    private static boolean isValidDayOfWeek(String dayOfWeek) {
        return dayOfWeek != null && switch (dayOfWeek.toUpperCase()) {
            case "MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN" -> true;
            default -> false;
        };
    }

    // Запрещаем создание экземпляров
    private CronExpressionBuilder() {
        throw new UnsupportedOperationException("Utility class");
    }
}
