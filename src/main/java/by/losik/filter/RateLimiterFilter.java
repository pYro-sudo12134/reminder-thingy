package by.losik.filter;

import by.losik.config.RateLimitConfig;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Singleton
public class RateLimiterFilter implements Filter {
    private static final Logger log = LoggerFactory.getLogger(RateLimiterFilter.class);

    private final RateLimitConfig rateLimitConfig;
    private final Map<String, AtomicRequestLimiter> requestLimiters = new ConcurrentHashMap<>();
    private final String[] whitelistedIps;

    @Inject
    public RateLimiterFilter(RateLimitConfig rateLimitConfig) {
        this.rateLimitConfig = rateLimitConfig;
        this.whitelistedIps = rateLimitConfig.getWhitelistedIps();
        log.info("RateLimiterFilter initialized. Whitelisted IPs: {}",
                Arrays.toString(whitelistedIps));
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        if (!rateLimitConfig.isEnabled()) {
            chain.doFilter(request, response);
            return;
        }

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String clientId = getClientIdentifier(httpRequest);
        String clientIp = httpRequest.getRemoteAddr();

        if (isWhitelisted(clientIp)) {
            log.debug("Client {} is whitelisted, skipping rate limit", clientId);
            chain.doFilter(request, response);
            return;
        }

        String endpoint = httpRequest.getRequestURI();
        AtomicRequestLimiter limiter = getLimiterForEndpoint(clientId, endpoint);

        RateLimitResult result = limiter.tryAcquire();

        if (!result.isAllowed()) {
            log.warn("Rate limit exceeded - Client: {}, IP: {}, Endpoint: {}, Reason: {}",
                    clientId, clientIp, endpoint, result.getReason());

            sendRateLimitResponse(httpResponse, limiter, result);
            return;
        }

        addRateLimitHeaders(httpResponse, limiter);
        chain.doFilter(request, response);
    }

    private String getClientIdentifier(HttpServletRequest request) {
        String apiKey = request.getHeader("X-API-Key");
        if (apiKey != null && !apiKey.isBlank()) {
            return "apikey:" + apiKey;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            if (!token.isBlank()) {
                return "token:" + Integer.toHexString(token.hashCode());
            }
        }

        String path = request.getRequestURI();
        if (path.contains("/user/")) {
            String[] parts = path.split("/");
            for (int i = 0; i < parts.length; i++) {
                if ("user".equals(parts[i]) && i + 1 < parts.length) {
                    String userId = parts[i + 1];
                    if (!userId.isEmpty() && !"reminders".equals(userId) &&
                            !"stats".equals(userId) && !"rate-limit".equals(userId)) {
                        return "user:" + userId;
                    }
                }
            }
        }

        String ip = getClientIpAddress(request);

        return "ip:" + ip;
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String ip = request.getRemoteAddr();

        String[] headerNames = {
                "X-Forwarded-For",
                "X-Real-IP",
                "Proxy-Client-IP",
                "WL-Proxy-Client-IP",
                "HTTP_X_FORWARDED_FOR",
                "HTTP_X_FORWARDED",
                "HTTP_X_CLUSTER_CLIENT_IP",
                "HTTP_CLIENT_IP",
                "HTTP_FORWARDED_FOR",
                "HTTP_FORWARDED",
                "HTTP_VIA"
        };

        for (String header : headerNames) {
            String headerValue = request.getHeader(header);
            if (headerValue != null && !headerValue.isEmpty() && !"unknown".equalsIgnoreCase(headerValue)) {
                ip = headerValue.split(",")[0].trim();
                break;
            }
        }

        if (ip != null && ip.contains(":")) {
            ip = ip.replace("[", "").replace("]", "");
        }

        return ip;
    }

    private AtomicRequestLimiter getLimiterForEndpoint(String clientId, String endpoint) {
        String key = clientId + ":" + getEndpointCategory(endpoint);
        return requestLimiters.computeIfAbsent(key, k -> {
            EndpointLimit endpointLimit = getLimitForEndpointCategory(endpoint);
            return new AtomicRequestLimiter(
                    endpointLimit.getMaxRequestsPerMinute(),
                    endpointLimit.getMaxRequestsPerHour(),
                    endpointLimit.getMaxRequestsPerDay()
            );
        });
    }

    private EndpointLimit getLimitForEndpointCategory(String endpoint) {
        if (endpoint.contains("/reminder/record")) {
            return new EndpointLimit(
                    rateLimitConfig.getUploadLimitPerMinute(),
                    rateLimitConfig.getMaxRequestsPerHour() / 2,
                    rateLimitConfig.getMaxRequestsPerDay() / 2
            );
        } else if (endpoint.contains("/api/")) {
            return new EndpointLimit(
                    rateLimitConfig.getApiLimitPerMinute(),
                    rateLimitConfig.getMaxRequestsPerHour(),
                    rateLimitConfig.getMaxRequestsPerDay()
            );
        } else if (endpoint.contains("/stats") || endpoint.contains("/transcription")) {
            return new EndpointLimit(
                    rateLimitConfig.getMaxRequestsPerMinute() / 2,
                    rateLimitConfig.getMaxRequestsPerHour() / 2,
                    rateLimitConfig.getMaxRequestsPerDay()
            );
        } else {
            return new EndpointLimit(
                    rateLimitConfig.getMaxRequestsPerMinute(),
                    rateLimitConfig.getMaxRequestsPerHour(),
                    rateLimitConfig.getMaxRequestsPerDay()
            );
        }
    }

    private String getEndpointCategory(String endpoint) {
        if (endpoint.contains("/reminder/record")) {
            return "upload";
        } else if (endpoint.contains("/transcription")) {
            return "transcription";
        } else if (endpoint.contains("/stats")) {
            return "stats";
        } else if (endpoint.contains("/user/") && endpoint.contains("/reminders")) {
            return "user_reminders";
        } else if (endpoint.contains("/reminder/")) {
            return "reminder_ops";
        } else if (endpoint.contains("/test")) {
            return "test";
        } else {
            return "general";
        }
    }

    private boolean isWhitelisted(String ip) {
        if (whitelistedIps.length == 0) {
            return false;
        }
        for (String whitelistedIp : whitelistedIps) {
            if (whitelistedIp.trim().equals(ip)) {
                return true;
            }
        }
        return false;
    }

    private void sendRateLimitResponse(HttpServletResponse response,
                                       AtomicRequestLimiter limiter,
                                       RateLimitResult result) throws IOException {
        response.setStatus(429);
        response.setContentType("application/json; charset=UTF-8");
        response.setCharacterEncoding("UTF-8");

        int retryAfter = limiter.getRetryAfterSeconds();
        response.setHeader("Retry-After", String.valueOf(retryAfter));

        Map<String, Object> stats = limiter.getStats();

        try (PrintWriter writer = response.getWriter()) {
            writer.write("{\n");
            writer.write("  \"error\": \"Rate limit exceeded\",\n");
            writer.write("  \"reason\": \"" + result.getReason() + "\",\n");
            writer.write("  \"retry_after\": " + retryAfter + ",\n");
            writer.write("  \"limits\": {\n");
            writer.write("    \"minute\": {\n");
            writer.write("      \"limit\": " + stats.get("minuteLimit") + ",\n");
            writer.write("      \"remaining\": " + stats.get("minuteRemaining") + ",\n");
            writer.write("      \"reset_in\": " + stats.get("minuteResetIn") + "\n");
            writer.write("    },\n");
            writer.write("    \"hour\": {\n");
            writer.write("      \"limit\": " + stats.get("hourLimit") + ",\n");
            writer.write("      \"remaining\": " + stats.get("hourRemaining") + ",\n");
            writer.write("      \"reset_in\": " + stats.get("hourResetIn") + "\n");
            writer.write("    },\n");
            writer.write("    \"day\": {\n");
            writer.write("      \"limit\": " + stats.get("dayLimit") + ",\n");
            writer.write("      \"remaining\": " + stats.get("dayRemaining") + ",\n");
            writer.write("      \"reset_in\": " + stats.get("dayResetIn") + "\n");
            writer.write("    }\n");
            writer.write("  },\n");
            writer.write("  \"timestamp\": \"" + java.time.Instant.now().toString() + "\"\n");
            writer.write("}");
        }
    }

    private void addRateLimitHeaders(HttpServletResponse response, AtomicRequestLimiter limiter) {
        Map<String, Object> stats = limiter.getStats();

        response.setHeader("X-RateLimit-Limit-Minute",
                String.valueOf(stats.get("minuteLimit")));
        response.setHeader("X-RateLimit-Remaining-Minute",
                String.valueOf(stats.get("minuteRemaining")));
        response.setHeader("X-RateLimit-Reset-Minute",
                String.valueOf(stats.get("minuteResetIn")));

        response.setHeader("X-RateLimit-Limit-Hour",
                String.valueOf(stats.get("hourLimit")));
        response.setHeader("X-RateLimit-Remaining-Hour",
                String.valueOf(stats.get("hourRemaining")));
        response.setHeader("X-RateLimit-Reset-Hour",
                String.valueOf(stats.get("hourResetIn")));

        response.setHeader("X-RateLimit-Limit-Day",
                String.valueOf(stats.get("dayLimit")));
        response.setHeader("X-RateLimit-Remaining-Day",
                String.valueOf(stats.get("dayRemaining")));
        response.setHeader("X-RateLimit-Reset-Day",
                String.valueOf(stats.get("dayResetIn")));
    }

    @Override
    public void init(FilterConfig filterConfig) {
        log.info("RateLimiterFilter initialized with configuration");
        log.info("Rate limiting enabled: {}", rateLimitConfig.isEnabled());
        log.info("Default limits: {}/min, {}/hour, {}/day",
                rateLimitConfig.getMaxRequestsPerMinute(),
                rateLimitConfig.getMaxRequestsPerHour(),
                rateLimitConfig.getMaxRequestsPerDay());
    }

    @Override
    public void destroy() {
        requestLimiters.clear();
        log.info("RateLimiterFilter destroyed");
    }

    private static class RateLimitResult {
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

    private static class AtomicRequestLimiter {
        private final int maxPerMinute;
        private final int maxPerHour;
        private final int maxPerDay;
        private final AtomicInteger minuteCount = new AtomicInteger(0);
        private final AtomicInteger hourCount = new AtomicInteger(0);
        private final AtomicInteger dayCount = new AtomicInteger(0);
        private final AtomicLong minuteWindowStart = new AtomicLong(System.currentTimeMillis());
        private final AtomicLong hourWindowStart = new AtomicLong(System.currentTimeMillis());
        private final AtomicLong dayWindowStart = new AtomicLong(System.currentTimeMillis());

        public AtomicRequestLimiter(int maxPerMinute, int maxPerHour, int maxPerDay) {
            this.maxPerMinute = maxPerMinute;
            this.maxPerHour = maxPerHour;
            this.maxPerDay = maxPerDay;
        }

        public RateLimitResult tryAcquire() {
            long now = System.currentTimeMillis();

            resetIfNeeded(now);

            int currentMinute = minuteCount.get();
            int currentHour = hourCount.get();
            int currentDay = dayCount.get();

            if (currentMinute >= maxPerMinute) {
                return RateLimitResult.minuteLimitExceeded();
            }

            if (currentHour >= maxPerHour) {
                return RateLimitResult.hourLimitExceeded();
            }

            if (currentDay >= maxPerDay) {
                return RateLimitResult.dayLimitExceeded();
            }

            boolean minuteUpdated = false;
            boolean hourUpdated = false;
            boolean dayUpdated = false;

            while (!minuteUpdated) {
                int current = minuteCount.get();
                if (current >= maxPerMinute) {
                    return RateLimitResult.minuteLimitExceeded();
                }
                minuteUpdated = minuteCount.compareAndSet(current, current + 1);
            }

            while (!hourUpdated) {
                int current = hourCount.get();
                if (current >= maxPerHour) {
                    minuteCount.decrementAndGet();
                    return RateLimitResult.hourLimitExceeded();
                }
                hourUpdated = hourCount.compareAndSet(current, current + 1);
            }

            while (!dayUpdated) {
                int current = dayCount.get();
                if (current >= maxPerDay) {
                    minuteCount.decrementAndGet();
                    hourCount.decrementAndGet();
                    return RateLimitResult.dayLimitExceeded();
                }
                dayUpdated = dayCount.compareAndSet(current, current + 1);
            }

            return RateLimitResult.allowed();
        }

        private void resetIfNeeded(long now) {
            long minuteStart = minuteWindowStart.get();
            if (now - minuteStart > TimeUnit.MINUTES.toMillis(1)) {
                if (minuteWindowStart.compareAndSet(minuteStart, now)) {
                    minuteCount.set(0);
                }
            }

            long hourStart = hourWindowStart.get();
            if (now - hourStart > TimeUnit.HOURS.toMillis(1)) {
                if (hourWindowStart.compareAndSet(hourStart, now)) {
                    hourCount.set(0);
                }
            }

            long dayStart = dayWindowStart.get();
            if (now - dayStart > TimeUnit.DAYS.toMillis(1)) {
                if (dayWindowStart.compareAndSet(dayStart, now)) {
                    dayCount.set(0);
                }
            }
        }

        public int getRetryAfterSeconds() {
            long now = System.currentTimeMillis();

            int currentMinute = minuteCount.get();
            int currentHour = hourCount.get();
            int currentDay = dayCount.get();

            if (currentMinute >= maxPerMinute) {
                long minuteStart = minuteWindowStart.get();
                long minuteRemaining = TimeUnit.MINUTES.toMillis(1) - (now - minuteStart);
                return (int) Math.max(1, TimeUnit.MILLISECONDS.toSeconds(minuteRemaining));
            }

            if (currentHour >= maxPerHour) {
                long hourStart = hourWindowStart.get();
                long hourRemaining = TimeUnit.HOURS.toMillis(1) - (now - hourStart);
                return (int) Math.max(1, TimeUnit.MILLISECONDS.toSeconds(hourRemaining));
            }

            if (currentDay >= maxPerDay) {
                long dayStart = dayWindowStart.get();
                long dayRemaining = TimeUnit.DAYS.toMillis(1) - (now - dayStart);
                return (int) Math.max(1, TimeUnit.MILLISECONDS.toSeconds(dayRemaining));
            }

            return 60;
        }

        public Map<String, Object> getStats() {
            long now = System.currentTimeMillis();

            long minuteStart = minuteWindowStart.get();
            long hourStart = hourWindowStart.get();
            long dayStart = dayWindowStart.get();

            long minuteResetIn = Math.max(0, TimeUnit.MINUTES.toMillis(1) - (now - minuteStart));
            long hourResetIn = Math.max(0, TimeUnit.HOURS.toMillis(1) - (now - hourStart));
            long dayResetIn = Math.max(0, TimeUnit.DAYS.toMillis(1) - (now - dayStart));

            int currentMinute = minuteCount.get();
            int currentHour = hourCount.get();
            int currentDay = dayCount.get();

            Map<String, Object> stats = new ConcurrentHashMap<>();
            stats.put("minuteLimit", maxPerMinute);
            stats.put("minuteUsed", currentMinute);
            stats.put("minuteRemaining", Math.max(0, maxPerMinute - currentMinute));
            stats.put("minuteResetIn", TimeUnit.MILLISECONDS.toSeconds(minuteResetIn));
            stats.put("hourLimit", maxPerHour);
            stats.put("hourUsed", currentHour);
            stats.put("hourRemaining", Math.max(0, maxPerHour - currentHour));
            stats.put("hourResetIn", TimeUnit.MILLISECONDS.toSeconds(hourResetIn));
            stats.put("dayLimit", maxPerDay);
            stats.put("dayUsed", currentDay);
            stats.put("dayRemaining", Math.max(0, maxPerDay - currentDay));
            stats.put("dayResetIn", TimeUnit.MILLISECONDS.toSeconds(dayResetIn));

            return stats;
        }

        public void reset() {
            long now = System.currentTimeMillis();
            minuteWindowStart.set(now);
            hourWindowStart.set(now);
            dayWindowStart.set(now);
            minuteCount.set(0);
            hourCount.set(0);
            dayCount.set(0);
        }
    }

    private static class EndpointLimit {
        private final int maxRequestsPerMinute;
        private final int maxRequestsPerHour;
        private final int maxRequestsPerDay;

        public EndpointLimit(int perMinute, int perHour, int perDay) {
            this.maxRequestsPerMinute = perMinute;
            this.maxRequestsPerHour = perHour;
            this.maxRequestsPerDay = perDay;
        }

        public int getMaxRequestsPerMinute() { return maxRequestsPerMinute; }
        public int getMaxRequestsPerHour() { return maxRequestsPerHour; }
        public int getMaxRequestsPerDay() { return maxRequestsPerDay; }
    }
}