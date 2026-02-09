package by.losik.filter;

import by.losik.config.RateLimitConfig;
import by.losik.config.RedisConnectionFactory;
import by.losik.config.RedisRateLimitConfig;
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
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.exceptions.JedisConnectionException;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@Singleton
public class RateLimiterFilter implements Filter {
    private static final Logger log = LoggerFactory.getLogger(RateLimiterFilter.class);

    private final RateLimitConfig rateLimitConfig;
    private final RedisRateLimitConfig redisConfig;
    private final RedisConnectionFactory redisConnectionFactory;
    private final String[] whitelistedIps;
    private JedisPool jedisPool;

    @Inject
    public RateLimiterFilter(RateLimitConfig rateLimitConfig,
                             RedisRateLimitConfig redisConfig,
                             RedisConnectionFactory redisConnectionFactory) {
        this.rateLimitConfig = rateLimitConfig;
        this.redisConfig = redisConfig;
        this.redisConnectionFactory = redisConnectionFactory;
        this.whitelistedIps = rateLimitConfig.getWhitelistedIps();
        log.info("RateLimiterFilter initialized. Whitelisted IPs: {}",
                Arrays.toString(whitelistedIps));
    }

    @Override
    public void init(FilterConfig filterConfig) {
        try {
            this.jedisPool = redisConnectionFactory.createJedisPool();
            log.info("RateLimiterFilter initialized with Redis");
            log.info("Rate limiting enabled: {}", rateLimitConfig.isEnabled());
            log.info("Redis enabled: {}", rateLimitConfig.isEnabled());
            log.info("Redis: {}:{}", redisConfig.getHost(), redisConfig.getPort());
            log.info("Default limits: {}/min, {}/hour, {}/day",
                    rateLimitConfig.getMaxRequestsPerMinute(),
                    rateLimitConfig.getMaxRequestsPerHour(),
                    rateLimitConfig.getMaxRequestsPerDay());
        } catch (Exception e) {
            log.error("Failed to initialize Redis pool in RateLimiterFilter", e);
            throw new RuntimeException("RateLimiterFilter initialization failed", e);
        }
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
        String endpointCategory = getEndpointCategory(endpoint);
        String redisKey = clientId + ":" + endpointCategory;

        EndpointLimit limit = getLimitForEndpointCategory(endpoint);

        RateLimitResult result = checkRateLimit(redisKey, limit);

        if (!result.isAllowed()) {
            log.warn("Rate limit exceeded - Client: {}, IP: {}, Endpoint: {}, Reason: {}",
                    clientId, clientIp, endpoint, result.getReason());

            sendRateLimitResponse(httpResponse, redisKey, limit, result);
            return;
        }

        addRateLimitHeaders(httpResponse, redisKey, limit);
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

    private RateLimitResult checkRateLimit(String redisKey, EndpointLimit limit) {
        if (!rateLimitConfig.isEnabled()) {
            return RateLimitResult.allowed();
        }

        try (Jedis jedis = jedisPool.getResource()) {
            String minuteKey = "rl:min:" + redisKey;
            String hourKey = "rl:hour:" + redisKey;
            String dayKey = "rl:day:" + redisKey;

            String script =
                    "local minute = redis.call('INCR', KEYS[1]) " +
                            "if minute == 1 then redis.call('EXPIRE', KEYS[1], 60) end " +
                            "local hour = redis.call('INCR', KEYS[2]) " +
                            "if hour == 1 then redis.call('EXPIRE', KEYS[2], 3600) end " +
                            "local day = redis.call('INCR', KEYS[3]) " +
                            "if day == 1 then redis.call('EXPIRE', KEYS[3], 86400) end " +
                            "local limits = {ARGV[1], ARGV[2], ARGV[3]} " +
                            "if minute > tonumber(limits[1]) then return 'minute' end " +
                            "if hour > tonumber(limits[2]) then return 'hour' end " +
                            "if day > tonumber(limits[3]) then return 'day' end " +
                            "return 'ok'";

            String result = (String) jedis.eval(script,
                    3, minuteKey, hourKey, dayKey,
                    String.valueOf(limit.getMaxRequestsPerMinute()),
                    String.valueOf(limit.getMaxRequestsPerHour()),
                    String.valueOf(limit.getMaxRequestsPerDay())
            );

            return switch (result) {
                case "minute" -> RateLimitResult.minuteLimitExceeded();
                case "hour" -> RateLimitResult.hourLimitExceeded();
                case "day" -> RateLimitResult.dayLimitExceeded();
                default -> RateLimitResult.allowed();
            };
        } catch (JedisConnectionException e) {
            log.error("Redis connection failed, allowing request: {}", e.getMessage());
            return RateLimitResult.allowed();
        } catch (Exception e) {
            log.error("Redis rate limit error: {}", e.getMessage());
            return RateLimitResult.allowed();
        }
    }

    private void sendRateLimitResponse(HttpServletResponse response,
                                       String redisKey,
                                       EndpointLimit limit,
                                       RateLimitResult result) throws IOException {
        response.setStatus(429);
        response.setContentType("application/json; charset=UTF-8");
        response.setCharacterEncoding("UTF-8");

        Map<String, Object> stats = getStatsFromRedis(redisKey, limit);
        int retryAfter = getRetryAfterSeconds(redisKey, result);

        response.setHeader("Retry-After", String.valueOf(retryAfter));

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

    private Map<String, Object> getStatsFromRedis(String redisKey, EndpointLimit limit) {
        Map<String, Object> stats = new HashMap<>();

        if (!rateLimitConfig.isEnabled()) {
            stats.put("minuteLimit", limit.getMaxRequestsPerMinute());
            stats.put("minuteRemaining", limit.getMaxRequestsPerMinute());
            stats.put("minuteResetIn", 0);
            stats.put("hourLimit", limit.getMaxRequestsPerHour());
            stats.put("hourRemaining", limit.getMaxRequestsPerHour());
            stats.put("hourResetIn", 0);
            stats.put("dayLimit", limit.getMaxRequestsPerDay());
            stats.put("dayRemaining", limit.getMaxRequestsPerDay());
            stats.put("dayResetIn", 0);
            return stats;
        }

        try (Jedis jedis = jedisPool.getResource()) {
            String minuteKey = "rl:min:" + redisKey;
            String hourKey = "rl:hour:" + redisKey;
            String dayKey = "rl:day:" + redisKey;

            long minuteCount = getCount(jedis, minuteKey);
            long hourCount = getCount(jedis, hourKey);
            long dayCount = getCount(jedis, dayKey);

            long minuteTtl = jedis.ttl(minuteKey);
            long hourTtl = jedis.ttl(hourKey);
            long dayTtl = jedis.ttl(dayKey);

            stats.put("minuteLimit", limit.getMaxRequestsPerMinute());
            stats.put("minuteUsed", minuteCount);
            stats.put("minuteRemaining", Math.max(0, limit.getMaxRequestsPerMinute() - minuteCount));
            stats.put("minuteResetIn", minuteTtl > 0 ? minuteTtl : 0);
            stats.put("hourLimit", limit.getMaxRequestsPerHour());
            stats.put("hourUsed", hourCount);
            stats.put("hourRemaining", Math.max(0, limit.getMaxRequestsPerHour() - hourCount));
            stats.put("hourResetIn", hourTtl > 0 ? hourTtl : 0);
            stats.put("dayLimit", limit.getMaxRequestsPerDay());
            stats.put("dayUsed", dayCount);
            stats.put("dayRemaining", Math.max(0, limit.getMaxRequestsPerDay() - dayCount));
            stats.put("dayResetIn", dayTtl > 0 ? dayTtl : 0);

        } catch (Exception e) {
            log.error("Failed to get stats from Redis: {}", e.getMessage());
        }

        return stats;
    }

    private long getCount(Jedis jedis, String key) {
        String value = jedis.get(key);
        return value != null ? Long.parseLong(value) : 0;
    }

    private int getRetryAfterSeconds(String redisKey, RateLimitResult result) {
        if (!rateLimitConfig.isEnabled()) {
            return 60;
        }

        try (Jedis jedis = jedisPool.getResource()) {
            String minuteKey = "rl:min:" + redisKey;
            String hourKey = "rl:hour:" + redisKey;
            String dayKey = "rl:day:" + redisKey;

            switch(result.getReason()) {
                case "minute_limit_exceeded":
                    long minuteTtl = jedis.ttl(minuteKey);
                    return Math.max(1, (int) minuteTtl);
                case "hour_limit_exceeded":
                    long hourTtl = jedis.ttl(hourKey);
                    return Math.max(1, (int) hourTtl);
                case "day_limit_exceeded":
                    long dayTtl = jedis.ttl(dayKey);
                    return Math.max(1, (int) dayTtl);
                default:
                    return 60;
            }
        } catch (Exception e) {
            log.error("Failed to get retry after: {}", e.getMessage());
            return 60;
        }
    }

    private void addRateLimitHeaders(HttpServletResponse response, String redisKey, EndpointLimit limit) {
        Map<String, Object> stats = getStatsFromRedis(redisKey, limit);

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
    public void destroy() {
        if (jedisPool != null) {
            jedisPool.close();
            log.info("Redis pool closed");
        }
        log.info("RateLimiterFilter destroyed");
    }
}