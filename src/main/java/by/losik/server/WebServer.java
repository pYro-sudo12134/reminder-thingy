package by.losik.server;

import by.losik.config.CorsConfig;
import by.losik.filter.CorsFilter;
import by.losik.filter.RateLimiterFilter;
import by.losik.filter.SessionAuthFilter;
import by.losik.resource.ExternalResource;
import by.losik.resource.MetricsResource;
import by.losik.resource.PasswordResetResource;
import by.losik.resource.ReminderResource;
import by.losik.resource.AuthResource;
import by.losik.resource.TelegramResource;
import by.losik.resource.UserResource;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import jakarta.servlet.DispatcherType;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumSet;
import java.util.Optional;

/**
 * Веб-сервер Jetty с Jersey JAX-RS.
 * <p>
 * Настраивает два контекста:
 * <ul>
 *     <li>/api — REST API с Jersey (аутентификация, rate limiting, CORS)</li>
 *     <li>/ — Статические файлы (CORS, rate limiting)</li>
 * </ul>
 * <p>
 * Фильтры применяются в порядке:
 * <ol>
 *     <li>CorsFilter — добавляет CORS заголовки</li>
 *     <li>RateLimiterFilter — ограничивает частоту запросов</li>
 *     <li>SessionAuthFilter — проверяет аутентификацию (только /api)</li>
 * </ol>
 */
@Singleton
public class WebServer implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(WebServer.class);
    private Server server;
    private final int port;
    private final ReminderResource reminderResource;
    private final RateLimiterFilter rateLimiterFilter;
    private final SessionAuthFilter sessionAuthFilter;
    private final CorsConfig corsConfig;
    private final AuthResource authResource;
    private final MetricsResource metricsResource;
    private final TelegramResource telegramResource;
    private final PasswordResetResource passwordResetResource;
    private final UserResource userResource;
    private final ExternalResource externalResource;

    /**
     * Создаёт веб-сервер с конфигурацией.
     *
     * @param port порт сервера
     * @param reminderResource ресурс для управления напоминаниями
     * @param authResource ресурс для аутентификации
     * @param metricsResource ресурс для метрик Prometheus
     * @param passwordResetResource ресурс для сброса пароля
     * @param rateLimiterFilter фильтр rate limiting
     * @param sessionAuthFilter фильтр аутентификации
     * @param corsConfig конфигурация CORS
     */
    @Inject
    public WebServer(int port,
                     ReminderResource reminderResource,
                     AuthResource authResource,
                     MetricsResource metricsResource,
                     PasswordResetResource passwordResetResource,
                     UserResource userResource,
                     TelegramResource telegramResource,
                     RateLimiterFilter rateLimiterFilter,
                     SessionAuthFilter sessionAuthFilter,
                     CorsConfig corsConfig,
                     ExternalResource externalResource) {
        this.port = port;
        this.metricsResource = metricsResource;
        this.reminderResource = reminderResource;
        this.rateLimiterFilter = rateLimiterFilter;
        this.sessionAuthFilter = sessionAuthFilter;
        this.telegramResource = telegramResource;
        this.corsConfig = corsConfig;
        this.externalResource = externalResource;
        this.passwordResetResource = passwordResetResource;
        this.authResource = authResource;
        this.userResource = userResource;
    }

    public void start() throws Exception {
        server = new Server(port);

        Path webDir = createStaticDirectory();

        ServletContextHandler apiContext = createApiContext();
        ServletContextHandler staticContext = createStaticContext(webDir);

        org.eclipse.jetty.server.handler.ContextHandlerCollection contexts =
                new org.eclipse.jetty.server.handler.ContextHandlerCollection();
        contexts.setHandlers(new org.eclipse.jetty.server.Handler[] {
                apiContext,
                staticContext
        });

        server.setHandler(contexts);
        server.start();

        logServerInfo();
    }

    private Path createStaticDirectory() {
        String staticPath = Optional.ofNullable(System.getenv("STATIC_FILES_PATH"))
                .or(() -> Optional.ofNullable(System.getProperty("static.files.path")))
                .orElse("./web");

        Path webDir = Paths.get(staticPath);

        if (!Files.exists(webDir)) {
            log.warn("Static directory not found at: {}", webDir.toAbsolutePath());
            try {
                Files.createDirectories(webDir);
                log.info("Created static directory at: {}", webDir.toAbsolutePath());
            } catch (Exception e) {
                log.error("Failed to create static directory", e);
            }
        }

        return webDir;
    }

    private ServletContextHandler createApiContext() {
        ServletContextHandler apiContext = new ServletContextHandler(ServletContextHandler.SESSIONS);
        apiContext.setContextPath("/api");

        // CORS фильтр
        FilterHolder corsFilter = new FilterHolder(new CorsFilter(corsConfig));
        apiContext.addFilter(corsFilter, "/*", EnumSet.of(DispatcherType.REQUEST));

        // Rate limiting фильтр
        FilterHolder rateLimitFilter = new FilterHolder(rateLimiterFilter);
        apiContext.addFilter(rateLimitFilter, "/*", EnumSet.of(DispatcherType.REQUEST));

        // Jersey конфигурация
        ResourceConfig apiConfig = new ResourceConfig();
        apiConfig.register(reminderResource);
        apiConfig.register(authResource);
        apiConfig.register(metricsResource);
        apiConfig.register(passwordResetResource);
        apiConfig.register(telegramResource);
        apiConfig.register(userResource);
        apiConfig.register(sessionAuthFilter);
        apiConfig.register(externalResource);
        apiConfig.register(JacksonFeature.class);
        apiConfig.register(MultiPartFeature.class);

        ServletContainer apiContainer = new ServletContainer(apiConfig);
        ServletHolder apiHolder = new ServletHolder("api", apiContainer);
        apiContext.addServlet(apiHolder, "/*");

        return apiContext;
    }

    private ServletContextHandler createStaticContext(Path webDir) {
        ServletContextHandler staticContext = new ServletContextHandler(ServletContextHandler.SESSIONS);
        staticContext.setContextPath("/");

        // CORS фильтр
        FilterHolder staticCorsFilter = new FilterHolder(new CorsFilter(corsConfig));
        staticContext.addFilter(staticCorsFilter, "/*", EnumSet.of(DispatcherType.REQUEST));

        // Rate limiting фильтр
        FilterHolder staticRateLimitFilter = new FilterHolder(rateLimiterFilter);
        staticContext.addFilter(staticRateLimitFilter, "/*", EnumSet.of(DispatcherType.REQUEST));

        ServletHolder staticHolder = new ServletHolder("static", DefaultServlet.class);
        staticHolder.setInitParameter("resourceBase", webDir.toAbsolutePath().toString());
        staticHolder.setInitParameter("dirAllowed", "true");
        staticHolder.setInitParameter("welcomeFiles", "index.html");
        staticHolder.setInitParameter("pathInfoOnly", "true");

        staticContext.addServlet(staticHolder, "/*");

        return staticContext;
    }

    private void logServerInfo() {
        log.info("Server started on port: {}", port);
        log.info("Static files: http://localhost:{}/", port);
        log.info("API base: http://localhost:{}/api", port);
    }

    public void stop() throws Exception {
        if (server != null && server.isRunning()) {
            server.stop();
            log.info("Web server stopped");
        }
    }

    @Override
    public void close() throws Exception {
        stop();
    }
}