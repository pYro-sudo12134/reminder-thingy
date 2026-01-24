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

        ParsedResult timeResult = parseTime(text);

        if (timeResult == null) {
            timeResult = parseRelativeTime(text);
        }

        if (timeResult == null) {
            LocalDateTime defaultTime = LocalDateTime.now().plus(1, ChronoUnit.HOURS);
            String action = extractAction(text);
            return new ParsedResult(defaultTime, action, 0.3);
        }

        String action = extractAction(text);

        return new ParsedResult(timeResult.scheduledTime, action, timeResult.confidence);
    }

    private ParsedResult parseTime(String text) {
        Pattern[] patterns = {
                Pattern.compile("в\\s+(\\d{1,2})[.:](\\d{2})"),
                Pattern.compile("в\\s+(\\d{1,2})\\s*(час[аов]?|ч)(?:\\s+(утра|вечера|дня|ночи))?"),
                Pattern.compile("в\\s+(\\d{1,2})\\s*(утра|вечера|дня|ночи)"),
                Pattern.compile("(на|к)\\s+(\\d{1,2})[.:](\\d{2})")
        };

        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                try {
                    LocalDateTime time = extractDateTime(matcher);
                    return new ParsedResult(time, null, 0.9);
                } catch (Exception e) {
                    log.debug("Failed to parse time with pattern: {}", pattern.pattern());
                }
            }
        }

        return null;
    }

    private ParsedResult parseRelativeTime(String text) {
        Pattern pattern = Pattern.compile("через\\s+(\\d+)\\s+(минут[уы]?|час[аов]?|день|дня|дней)");
        Matcher matcher = pattern.matcher(text);

        if (matcher.find()) {
            try {
                int amount = Integer.parseInt(matcher.group(1));
                String unit = matcher.group(2);

                LocalDateTime now = LocalDateTime.now();
                LocalDateTime futureTime;

                if (unit.startsWith("минут")) {
                    futureTime = now.plusMinutes(amount);
                } else if (unit.startsWith("час")) {
                    futureTime = now.plusHours(amount);
                } else if (unit.startsWith("день")) {
                    futureTime = now.plusDays(amount);
                } else {
                    futureTime = now.plusHours(1);
                }

                return new ParsedResult(futureTime, null, 0.8);
            } catch (Exception e) {
                log.debug("Failed to parse relative time");
            }
        }

        return null;
    }

    private LocalDateTime extractDateTime(Matcher matcher) {
        LocalDateTime now = LocalDateTime.now();
        LocalDate date = now.toLocalDate();

        int hour = 12;
        int minute = 0;

        if (matcher.group(1) != null) {
            hour = Integer.parseInt(matcher.group(1));
        } else if (matcher.group(2) != null) {
            hour = Integer.parseInt(matcher.group(2));
        }

        if (matcher.groupCount() >= 2 && matcher.group(2) != null && matcher.group(2).matches("\\d{2}")) {
            minute = Integer.parseInt(matcher.group(2));
        }

        String period = "";
        if (matcher.groupCount() >= 3) {
            period = matcher.group(3) != null ? matcher.group(3) : "";
        }

        if (!period.isEmpty()) {
            if (period.contains("вечер") || period.contains("ноч") || period.contains("ночи") || period.contains("дня")) {
                if (hour < 12) hour += 12;
            }
        }

        LocalDateTime result = LocalDateTime.of(date, LocalTime.of(hour, minute));
        if (result.isBefore(now)) {
            result = result.plusDays(1);
        }

        return result;
    }

    private String extractAction(String text) {
        String cleaned = text
                .replaceAll("в\\s+\\d{1,2}[.:]\\d{2}", "")
                .replaceAll("в\\s+\\d{1,2}\\s*(?:час[аов]?|ч)(?:\\s+(?:утра|вечера|дня|ночи))?", "")
                .replaceAll("через\\s+\\d+\\s+(?:минут[уы]?|час[аов]?|день|дня|дней)", "")
                .replaceAll("\\b(в|на|к|через|мне|пожалуйста)\\b", "")
                .replaceAll("\\s+", " ")
                .trim();

        cleaned = cleaned.replaceAll("^[.,!?\\s]+|[.,!?\\s]+$", "");

        if (cleaned.length() < 3) {
            return "напоминание";
        }

        return cleaned;
    }

    public String formatForEventBridge(LocalDateTime time) {
        return time.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
    }

    public String formatForDisplay(LocalDateTime time) {
        return time.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
    }
}