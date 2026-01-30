package by.losik.filter;

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
import java.util.HashSet;
import java.util.Set;

@Provider
@Priority(Priorities.AUTHENTICATION)
public class SessionAuthFilter implements ContainerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(SessionAuthFilter.class);

    @Context
    private HttpServletRequest httpRequest;
    private static final Set<String> PUBLIC_PATHS = new HashSet<>();

    static {
        PUBLIC_PATHS.add("test");
        PUBLIC_PATHS.add("auth/login");
        PUBLIC_PATHS.add("auth/logout");
        PUBLIC_PATHS.add("auth/me");
        PUBLIC_PATHS.add("health");
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

        if (isPublicPath(path)) {
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

    private boolean isPublicPath(String path) {
        if (path == null || path.isEmpty() || path.equals("/")) {
            return true;
        }

        if (PUBLIC_PATHS.contains(path)) {
            return true;
        }

        for (String publicPath : PUBLIC_PATHS) {
            if (path.startsWith(publicPath + "/") || path.equals(publicPath)) {
                return true;
            }
        }

        return false;
    }

    private void abortWithUnauthorized(ContainerRequestContext ctx, String message) {
        ctx.abortWith(Response.status(Response.Status.UNAUTHORIZED)
                .entity("{\"error\": \"" + message + "\", \"code\": \"UNAUTHORIZED\"}")
                .build());
    }
}