package by.losik.util;

import java.time.LocalDateTime;

/**
 * Утилитный класс для создания cron выражений AWS EventBridge.
 * <p>
 * Формат cron AWS EventBridge:
 * <pre>{@code cron(Minutes Hours Day-of-month Month Day-of-week Year)}</pre>
 * <p>
 * Важные правила:
 * <ul>
 *     <li>Если указан Day-of-month, Day-of-week должен быть ?</li>
 *     <li>Если указан Day-of-week, Day-of-month должен быть ?</li>
 *     <li>Нельзя одновременно указывать конкретные значения для обоих полей</li>
 *     <li>Поля не должны содержать ведущие нули</li>
 * </ul>
 */
public final class CronExpressionBuilder {

    /**
     * Создаёт cron выражение для абсолютного времени.
     * <p>
     * Пример: {@code cron(0 12 15 3 ? 2026)} — 15 марта 2026 в 12:00
     *
     * @param dateTime дата и время запуска
     * @return cron выражение в формате AWS EventBridge
     */
    public static String fromLocalDateTime(LocalDateTime dateTime) {
        int minute = dateTime.getMinute();
        int hour = dateTime.getHour();
        int dayOfMonth = dateTime.getDayOfMonth();
        int month = dateTime.getMonthValue();
        int year = dateTime.getYear();

        return String.format("cron(%d %d %d %d ? %d)", minute, hour, dayOfMonth, month, year);
    }

    /**
     * Создаёт rate выражение для интервального запуска.
     *
     * @param value количество единиц времени
     * @param unit единица времени ("minute", "hour", "day")
     * @return rate выражение
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
     * Пример: {@code cron(0 9 ? * ? *)} — каждый день в 09:00
     *
     * @param hourOfDay час запуска (0-23)
     * @param minuteOfHour минута запуска (0-59)
     * @return cron выражение для ежедневного запуска
     */
    public static String daily(int hourOfDay, int minuteOfHour) {
        return String.format("cron(%d %d ? * ? *)", minuteOfHour, hourOfDay);
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
     */
    public static String weekly(int hourOfDay, int minuteOfHour, String dayOfWeek) {
        if (!isValidDayOfWeek(dayOfWeek)) {
            throw new IllegalArgumentException(
                    "Invalid day of week: " + dayOfWeek);
        }
        return String.format("cron(%d %d ? * %s *)", minuteOfHour, hourOfDay, dayOfWeek.toUpperCase());
    }

    /**
     * Создаёт cron выражение для запуска по дням месяца.
     *
     * @param hourOfDay час запуска
     * @param minuteOfHour минута запуска
     * @param daysOfMonth дни месяца (например, "1,15" или "1-10")
     * @return cron выражение
     */
    public static String monthly(int hourOfDay, int minuteOfHour, String daysOfMonth) {
        return String.format("cron(%d %d %s * ? *)", minuteOfHour, hourOfDay, daysOfMonth);
    }

    private static boolean isValidRateUnit(String unit) {
        return "minute".equalsIgnoreCase(unit) ||
                "hour".equalsIgnoreCase(unit) ||
                "day".equalsIgnoreCase(unit);
    }

    private static boolean isValidDayOfWeek(String dayOfWeek) {
        if (dayOfWeek == null) return false;
        return switch (dayOfWeek.toUpperCase()) {
            case "MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN" -> true;
            default -> false;
        };
    }

    private CronExpressionBuilder() {
        throw new UnsupportedOperationException("Utility class");
    }
}