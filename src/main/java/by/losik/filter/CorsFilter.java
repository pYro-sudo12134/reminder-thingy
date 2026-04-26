package by.losik.filter;

import by.losik.config.CorsConfig;
import com.google.inject.Inject;
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

/**
 * Фильтр для CORS (Cross-Origin Resource Sharing) заголовков.
 * <p>
 * Добавляет CORS заголовки ко всем ответам:
 * <ul>
 *     <li>Access-Control-Allow-Origin</li>
 *     <li>Access-Control-Allow-Methods</li>
 *     <li>Access-Control-Allow-Headers</li>
 *     <li>Access-Control-Allow-Credentials</li>
 *     <li>Access-Control-Max-Age</li>
 * </ul>
 * <p>
 * Также добавляет security заголовки:
 * <ul>
 *     <li>X-Content-Type-Options: nosniff</li>
 *     <li>X-Frame-Options: DENY</li>
 *     <li>X-XSS-Protection: 1; mode=block</li>
 * </ul>
 *
 * @see by.losik.config.CorsConfig
 */
public class CorsFilter implements Filter {
    private static final Logger log = LoggerFactory.getLogger(CorsFilter.class);

    private final CorsConfig config;

    /**
     * Создаёт CORS фильтр с конфигурацией.
     *
     * @param config конфигурация CORS настроек
     */
    @Inject
    public CorsFilter(CorsConfig config) {
        this.config = config;
    }

    @Override
    public void init(FilterConfig filterConfig) {
        log.info("CORS Filter initialized with allowed origins: {}", config.getAllowedOrigins());
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        httpResponse.setHeader("Access-Control-Allow-Origin",
                config.getAllowedOrigins());
        httpResponse.setHeader("Access-Control-Allow-Methods", config.getAllowedMethods());
        httpResponse.setHeader("Access-Control-Allow-Headers", config.getAllowedHeaders());
        httpResponse.setHeader("Access-Control-Allow-Credentials", String.valueOf(config.isAllowCredentials()));
        httpResponse.setHeader("Access-Control-Max-Age", config.getMaxAge());

        httpResponse.setHeader("X-Content-Type-Options", "nosniff");
        httpResponse.setHeader("X-Frame-Options", "DENY");
        httpResponse.setHeader("X-XSS-Protection", "1; mode=block");

        if ("OPTIONS".equalsIgnoreCase(httpRequest.getMethod())) {
            httpResponse.setStatus(HttpServletResponse.SC_OK);
            return;
        }

        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
        log.info("CORS Filter destroyed");
    }
}