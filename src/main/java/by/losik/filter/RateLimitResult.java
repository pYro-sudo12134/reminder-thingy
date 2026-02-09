package by.losik.filter;

public class RateLimitResult {
    private final boolean allowed;
    private final String reason;

    private RateLimitResult(boolean allowed, String reason) {
        this.allowed = allowed;
        this.reason = reason;
    }

    public static RateLimitResult allowed() {
        return new RateLimitResult(true, "allowed");
    }

    public static RateLimitResult minuteLimitExceeded() {
        return new RateLimitResult(false, "minute_limit_exceeded");
    }

    public static RateLimitResult hourLimitExceeded() {
        return new RateLimitResult(false, "hour_limit_exceeded");
    }

    public static RateLimitResult dayLimitExceeded() {
        return new RateLimitResult(false, "day_limit_exceeded");
    }

    public boolean isAllowed() { return allowed; }
    public String getReason() { return reason; }
}