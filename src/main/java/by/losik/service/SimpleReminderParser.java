package by.losik.service;

import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Singleton
public class SimpleReminderParser {

    private static final Logger log = LoggerFactory.getLogger(SimpleReminderParser.class);

    public record ParsedResult(
            LocalDateTime scheduledTime,
            String action,
            double confidence
    ) {}

    public ParsedResult parse(String text) {
        text = text.toLowerCase().trim();

        if (text.isEmpty()) {
            LocalDateTime defaultTime = LocalDateTime.now().plus(1, ChronoUnit.HOURS);
            return new ParsedResult(defaultTime, "напоминание", 0.1);
        }

        ParsedResult relativeResult = parseRelativeTime(text);
        if (relativeResult != null) {
            String action = extractAction(text);
            return new ParsedResult(relativeResult.scheduledTime, action, relativeResult.confidence);
        }

        ParsedResult absoluteResult = parseAbsoluteTime(text);
        if (absoluteResult != null) {
            String action = extractAction(text);
            return new ParsedResult(absoluteResult.scheduledTime, action, absoluteResult.confidence);
        }

        LocalDateTime defaultTime = LocalDateTime.now().plus(1, ChronoUnit.HOURS);
        String action = extractAction(text);
        return new ParsedResult(defaultTime, action, 0.3);
    }

    private ParsedResult parseRelativeTime(String text) {
        Pattern pattern = Pattern.compile("через\\s+(\\d+)\\s+(минут[а-я]*|час[а-я]*|дн[а-я]*)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(text);

        if (matcher.find()) {
            try {
                int amount = Integer.parseInt(matcher.group(1));
                String unit = matcher.group(2).toLowerCase();

                LocalDateTime now = LocalDateTime.now();
                LocalDateTime futureTime;

                if (unit.startsWith("минут")) {
                    futureTime = now.plusMinutes(amount);
                    return new ParsedResult(futureTime, null, 0.8);
                } else if (unit.startsWith("час")) {
                    futureTime = now.plusHours(amount);
                    return new ParsedResult(futureTime, null, 0.8);
                } else if (unit.startsWith("дн")) {
                    futureTime = now.plusDays(amount);
                    return new ParsedResult(futureTime, null, 0.8);
                }
            } catch (Exception e) {
                log.debug("Failed to parse relative time: {}", e.getMessage());
            }
        }

        return null;
    }

    private ParsedResult parseAbsoluteTime(String text) {
        Pattern timeWithMinutesPattern = Pattern.compile("(?:в|на|к)\\s+(\\d{1,2})[:.](\\d{2})");
        Matcher matcher = timeWithMinutesPattern.matcher(text);

        if (matcher.find()) {
            try {
                int hour = Integer.parseInt(matcher.group(1));
                int minute = Integer.parseInt(matcher.group(2));
                LocalDateTime time = createDateTime(hour, minute);
                return new ParsedResult(time, null, 0.9);
            } catch (Exception e) {
                log.debug("Failed to parse time with minutes");
            }
        }

        Pattern timeWithPeriodPattern = Pattern.compile("(?:в|на|к)\\s+(\\d{1,2})\\s*(утра|вечера|дня|ночи)");
        matcher = timeWithPeriodPattern.matcher(text);

        if (matcher.find()) {
            try {
                int hour = Integer.parseInt(matcher.group(1));
                String period = matcher.group(2);
                hour = adjustHourForPeriod(hour, period);
                LocalDateTime time = createDateTime(hour, 0);
                return new ParsedResult(time, null, 0.9);
            } catch (Exception e) {
                log.debug("Failed to parse time with period");
            }
        }

        Pattern simpleTimePattern = Pattern.compile("\\b(?:в|на|к)\\s+(\\d{1,2})\\b");
        matcher = simpleTimePattern.matcher(text);

        if (matcher.find()) {
            try {
                int hour = Integer.parseInt(matcher.group(1));
                hour = adjustHourForSimpleTime(hour);
                LocalDateTime time = createDateTime(hour, 0);
                return new ParsedResult(time, null, 0.8);
            } catch (Exception e) {
                log.debug("Failed to parse simple time");
            }
        }

        return null;
    }

    private int adjustHourForPeriod(int hour, String period) {
        period = period.toLowerCase();

        if (period.contains("вечер") || period.contains("дня") || period.contains("ночи")) {
            if (hour >= 1 && hour <= 11) {
                return hour + 12;
            } else if (hour == 12 && period.contains("ночи")) {
                return 0;
            }
        } else if (period.contains("утр")) {
            if (hour == 12) {
                return 0;
            }
        }

        return hour;
    }

    private int adjustHourForSimpleTime(int hour) {
        LocalDateTime now = LocalDateTime.now();

        if (hour <= 12) {
            if (now.getHour() >= 12 && hour < 12) {
                LocalDateTime morningTime = LocalDateTime.of(now.toLocalDate(), LocalTime.of(hour, 0));
                if (morningTime.isBefore(now)) {
                    return hour + 12;
                }
            }
        }

        return hour;
    }

    private LocalDateTime createDateTime(int hour, int minute) {
        LocalDateTime now = LocalDateTime.now();
        LocalDate date = now.toLocalDate();

        hour = hour % 24;

        LocalDateTime result = LocalDateTime.of(date, LocalTime.of(hour, minute));

        if (result.isBefore(now)) {
            result = result.plusDays(1);
        }

        return result;
    }

    private String extractAction(String text) {
        String cleaned = text
                .replaceAll("(?:в|на|к)\\s+\\d{1,2}[:.]\\d{2}", "")
                .replaceAll("(?:в|на|к)\\s+\\d{1,2}\\s*(?:час[аов]?|ч)(?:\\s+(?:утра|вечера|дня|ночи))?", "")
                .replaceAll("(?:в|на|к)\\s+\\d{1,2}\\s*(?:утра|вечера|дня|ночи)", "")
                .replaceAll("\\b(?:в|на|к)\\s+\\d{1,2}\\b", "")
                .replaceAll("через\\s+\\d+\\s*(?:минут[уы]?|час[аов]?|день|дня|дней)", "")
                .replaceAll("\\s+", " ")
                .trim();

        cleaned = cleaned.replaceAll("^[.,!?\\s]+|[.,!?\\s]+$", "");

        if (cleaned.length() < 2 || cleaned.matches(".*\\d.*")) {
            String[] parts = text.split("\\b(?:в|на|к|через)\\s+\\d");
            if (parts.length > 0) {
                cleaned = parts[0].trim();
            }

            if (cleaned.length() < 2) {
                return "напоминание";
            }
        }

        cleaned = cleaned.replaceAll("\\b(мне|пожалуйста|нужно|надо|сейчас|сегодня|завтра|не\\s+забудь|пожалуйста)\\b", "")
                .replaceAll("\\s+", " ")
                .trim();

        cleaned = cleaned.replaceAll("^[внак]\\s+", "").trim();

        return cleaned.isEmpty() ? "напоминание" : cleaned;
    }

    public String formatForEventBridge(LocalDateTime time) {
        return time.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
    }

    public String formatForDisplay(LocalDateTime time) {
        return time.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
    }
}