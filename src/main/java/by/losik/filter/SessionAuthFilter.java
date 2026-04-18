package by.losik.filter;

import by.losik.config.AuthFilterConfig;
import com.google.inject.Inject;
import jakarta.annotation.Priority;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Фильтр аутентификации через сессии.
 * <p>
 * Проверяет наличие активной сессии (JSESSIONID) для защищённых путей.
 * Публичные пути (login, register, health, metrics) доступны без аутентификации.
 * <p>
 * Приоритет: AUTHENTICATION (выполняется одним из первых).
 *
 * @see by.losik.config.AuthFilterConfig
 */
@Provider
@Priority(Priorities.AUTHENTICATION)
public class SessionAuthFilter implements ContainerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(SessionAuthFilter.class);

    private final AuthFilterConfig config;

    @Context
    private HttpServletRequest httpRequest;

    /**
     * Создаёт фильтр аутентификации с конфигурацией.
     *
     * @param config конфигурация public paths
     */
    @Inject
    public SessionAuthFilter(AuthFilterConfig config) {
        this.config = config;
    }

    @Override
    public void filter(ContainerRequestContext ctx) throws IOException {
        String path = ctx.getUriInfo().getPath();
        String method = ctx.getMethod();

        log.debug("Auth filter: {} {}", method, path);

        if ("OPTIONS".equals(method)) {
            log.debug("Skipping auth for OPTIONS request");
            return;
        }

        if (config.isPublicPath(path)) {
            log.debug("Skipping auth for public path: {}", path);
            return;
        }

        if (httpRequest == null) {
            log.error("HttpServletRequest is null in filter");
            abortWithUnauthorized(ctx, "Server configuration error");
            return;
        }

        HttpSession session = httpRequest.getSession(false);
        if (session == null) {
            log.warn("No session for protected path: {}", path);
            abortWithUnauthorized(ctx, "No active session");
            return;
        }

        String username = (String) session.getAttribute("username");
        if (username == null) {
            log.warn("Session exists but no username for path: {}", path);
            abortWithUnauthorized(ctx, "Session expired");
            return;
        }

        ctx.setProperty("username", username);
        log.debug("Authenticated user '{}' for path: {}", username, path);
    }

    private void abortWithUnauthorized(ContainerRequestContext ctx, String message) {
        ctx.abortWith(Response.status(Response.Status.UNAUTHORIZED)
                .entity("{\"error\": \"" + message + "\", \"code\": \"UNAUTHORIZED\"}")
                .build());
    }
}