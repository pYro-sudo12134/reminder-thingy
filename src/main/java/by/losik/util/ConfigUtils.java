package by.losik.util;

import java.util.Optional;

/**
 * Утилитный класс для работы с переменными окружения и системными свойствами.
 */
public final class ConfigUtils {

    private ConfigUtils() {
    }

    /**
     * Получает значение переменной окружения или системного свойства.
     * Системные свойства имеют приоритет над переменными окружения.
     *
     * @param key          ключ переменной/свойства
     * @param defaultValue значение по умолчанию
     * @return значение переменной окружения или свойства, или defaultValue
     */
    public static String getEnvOrDefault(String key, String defaultValue) {
        return Optional.ofNullable(System.getenv(key))
                .or(() -> Optional.ofNullable(System.getProperty(key)))
                .orElse(defaultValue);
    }

    /**
     * Получает значение переменной окружения или системного свойства.
     * Системные свойства имеют приоритет над переменными окружения.
     *
     * @param key ключ переменной/свойства
     * @return значение переменной окружения или свойства, или null
     */
    public static String getEnvOrNull(String key) {
        return Optional.ofNullable(System.getenv(key))
                .or(() -> Optional.ofNullable(System.getProperty(key)))
                .orElse(null);
    }

    /**
     * Получает значение переменной окружения как Integer.
     *
     * @param key          ключ переменной/свойства
     * @param defaultValue значение по умолчанию
     * @return значение как Integer
     */
    public static Integer getIntEnvOrDefault(String key, Integer defaultValue) {
        String value = getEnvOrDefault(key, null);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Получает значение переменной окружения как Long.
     *
     * @param key          ключ переменной/свойства
     * @param defaultValue значение по умолчанию
     * @return значение как Long
     */
    public static Long getLongEnvOrDefault(String key, Long defaultValue) {
        String value = getEnvOrDefault(key, null);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Получает значение переменной окружения как Boolean.
     *
     * @param key          ключ переменной/свойства
     * @param defaultValue значение по умолчанию
     * @return значение как Boolean
     */
    public static Boolean getBooleanEnvOrDefault(String key, Boolean defaultValue) {
        String value = getEnvOrDefault(key, null);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value.trim());
    }
}
