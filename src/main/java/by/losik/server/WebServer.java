package by.losik.server;

import by.losik.filter.CorsFilter;
import by.losik.resource.ReminderResource;
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

@Singleton
public class WebServer {
    private static final Logger log = LoggerFactory.getLogger(WebServer.class);
    private Server server;
    private final int port;
    private final ReminderResource reminderResource;

    @Inject
    public WebServer(int port, ReminderResource reminderResource) {
        this.port = port;
        this.reminderResource = reminderResource;
    }

    public void start() throws Exception {
        server = new Server(port);

        Path webDir = Paths.get("./web");
        if (!Files.exists(webDir)) {
            log.warn("Static directory 'web' not found at: {}", webDir.toAbsolutePath());
            Files.createDirectories(webDir);
            log.info("Created static directory at: {}", webDir.toAbsolutePath());
        }

        ServletContextHandler apiContext = new ServletContextHandler(ServletContextHandler.SESSIONS);
        apiContext.setContextPath("/api");

        FilterHolder corsFilter = new FilterHolder(new CorsFilter());
        apiContext.addFilter(corsFilter, "/*", EnumSet.of(DispatcherType.REQUEST));

        ResourceConfig apiConfig = new ResourceConfig();

        apiConfig.register(reminderResource);
        apiConfig.register(JacksonFeature.class);
        apiConfig.register(MultiPartFeature.class);

        ServletContainer apiContainer = new ServletContainer(apiConfig);
        ServletHolder apiHolder = new ServletHolder("api", apiContainer);
        apiContext.addServlet(apiHolder, "/*");

        ServletContextHandler staticContext = new ServletContextHandler(ServletContextHandler.SESSIONS);
        staticContext.setContextPath("/");

        FilterHolder staticCorsFilter = new FilterHolder(new CorsFilter());
        staticContext.addFilter(staticCorsFilter, "/*", EnumSet.of(DispatcherType.REQUEST));

        ServletHolder staticHolder = new ServletHolder("static", DefaultServlet.class);
        staticHolder.setInitParameter("resourceBase", webDir.toAbsolutePath().toString());
        staticHolder.setInitParameter("dirAllowed", "true");
        staticHolder.setInitParameter("welcomeFiles", "index.html");
        staticHolder.setInitParameter("pathInfoOnly", "true");

        staticContext.addServlet(staticHolder, "/*");

        org.eclipse.jetty.server.handler.ContextHandlerCollection contexts =
                new org.eclipse.jetty.server.handler.ContextHandlerCollection();
        contexts.setHandlers(new org.eclipse.jetty.server.Handler[] {
                apiContext,
                staticContext
        });

        server.setHandler(contexts);
        server.start();

        log.info("Server started on port {}", port);
        log.info("Static files directory: {}", webDir.toAbsolutePath());
        log.info("Static files: http://localhost:{}", port);
        log.info("API base: http://localhost:{}/api/test", port);
    }

    public void stop() throws Exception {
        if (server != null) {
            server.stop();
            log.info("Web server stopped");
        }
    }
}