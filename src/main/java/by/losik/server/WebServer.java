package by.losik.server;

import by.losik.filter.CorsFilter;
import by.losik.filter.RateLimiterFilter;
import by.losik.filter.SessionAuthFilter;
import by.losik.resource.MetricsResource;
import by.losik.resource.PasswordResetResource;
import by.losik.resource.ReminderResource;
import by.losik.resource.AuthResource;
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

@Singleton
public class WebServer implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(WebServer.class);
    private Server server;
    private final int port;
    private final ReminderResource reminderResource;
    private final RateLimiterFilter rateLimiterFilter;
    private final AuthResource authResource;
    private final MetricsResource metricsResource;
    private final PasswordResetResource passwordResetResource;

    @Inject
    public WebServer(int port,
                     ReminderResource reminderResource,
                     AuthResource authResource,
                     MetricsResource metricsResource,
                     PasswordResetResource passwordResetResource,
                     RateLimiterFilter rateLimiterFilter) {
        this.port = port;
        this.metricsResource = metricsResource;
        this.reminderResource = reminderResource;
        this.rateLimiterFilter = rateLimiterFilter;
        this.passwordResetResource = passwordResetResource;
        this.authResource = authResource;
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

        FilterHolder corsFilter = new FilterHolder(new CorsFilter());
        apiContext.addFilter(corsFilter, "/*", EnumSet.of(DispatcherType.REQUEST));

        FilterHolder rateLimitFilter = new FilterHolder(rateLimiterFilter);
        apiContext.addFilter(rateLimitFilter, "/*", EnumSet.of(DispatcherType.REQUEST));

        ResourceConfig apiConfig = new ResourceConfig();
        apiConfig.register(reminderResource);
        apiConfig.register(authResource);
        apiConfig.register(metricsResource);
        apiConfig.register(passwordResetResource);
        apiConfig.register(SessionAuthFilter.class);
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

        FilterHolder staticCorsFilter = new FilterHolder(new CorsFilter());
        staticContext.addFilter(staticCorsFilter, "/*", EnumSet.of(DispatcherType.REQUEST));

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