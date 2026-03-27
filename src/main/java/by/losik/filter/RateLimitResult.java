package by.losik.filter;

/**
 * Результат проверки rate limiting.
 * <p>
 * Возвращается фильтром RateLimiterFilter после проверки лимитов.
 * Содержит флаг разрешения запроса и причину отказа (если есть).
 */
public class RateLimitResult {
    private final boolean allowed;
    private final String reason;

    /**
     * Создаёт результат проверки.
     *
     * @param allowed true если запрос разрешён
     * @param reason причина отказа (или "allowed" если разрешён)
     */
    private RateLimitResult(boolean allowed, String reason) {
        this.allowed = allowed;
        this.reason = reason;
    }

    /**
     * Создаёт результат для разрешённого запроса.
     *
     * @return RateLimitResult с allowed=true
     */
    public static RateLimitResult allowed() {
        return new RateLimitResult(true, "allowed");
    }

    /**
     * Создаёт результат для превышения минутного лимита.
     *
     * @return RateLimitResult с allowed=false и reason="minute_limit_exceeded"
     */
    public static RateLimitResult minuteLimitExceeded() {
        return new RateLimitResult(false, "minute_limit_exceeded");
    }

    /**
     * Создаёт результат для превышения часового лимита.
     *
     * @return RateLimitResult с allowed=false и reason="hour_limit_exceeded"
     */
    public static RateLimitResult hourLimitExceeded() {
        return new RateLimitResult(false, "hour_limit_exceeded");
    }

    /**
     * Создаёт результат для превышения дневного лимита.
     *
     * @return RateLimitResult с allowed=false и reason="day_limit_exceeded"
     */
    public static RateLimitResult dayLimitExceeded() {
        return new RateLimitResult(false, "day_limit_exceeded");
    }

    /**
     * Проверяет, разрешён ли запрос.
     *
     * @return true если запрос разрешён
     */
    public boolean isAllowed() {
        return allowed;
    }

    /**
     * Получает причину отказа.
     *
     * @return причина отказа ("minute_limit_exceeded", "hour_limit_exceeded", "day_limit_exceeded")
     *         или "allowed" если запрос разрешён
     */
    public String getReason() {
        return reason;
    }

    /**
     * Возвращает строковое представление результата.
     *
     * @return строка в формате "RateLimitResult{allowed=X, reason='Y'}"
     */
    @Override
    public String toString() {
        return "RateLimitResult{allowed=%s, reason='%s'}"
                .formatted(allowed, reason);
    }
}