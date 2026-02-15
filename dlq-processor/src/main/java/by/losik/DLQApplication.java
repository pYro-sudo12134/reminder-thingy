package by.losik;

import by.losik.composition.root.AWSModule;
import by.losik.service.DLQProcessorService;
import by.losik.service.MetricsService;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.UUID;

public class DLQApplication {
    private static final Logger log = LoggerFactory.getLogger(DLQApplication.class);

    public static void main(String[] args) throws IOException {
        String env = System.getenv().getOrDefault("ENVIRONMENT_NAME", "dev");
        String endpoint = System.getenv().getOrDefault("AWS_ENDPOINT_URL", "http://localhost:4566");
        String queueSuffix = System.getenv().getOrDefault("QUEUE_SUFFIX", "-reminder-dlq");
        boolean localstack = Boolean.parseBoolean(System.getenv().getOrDefault("USE_LOCALSTACK", "true"));

        log.info("Starting DLQ Application - env: {}, localstack: {}", env, localstack);
        log.info("SMTP Configuration: host={}, port={}",
                System.getenv().getOrDefault("SMTP_HOST", "smtp.gmail.com"),
                System.getenv().getOrDefault("SMTP_PORT", "587"));

        Injector injector = Guice.createInjector(new AWSModule(env, endpoint, queueSuffix, localstack));
        MetricsService metricsService = injector.getInstance(MetricsService.class);
        DLQProcessorService processor = injector.getInstance(DLQProcessorService.class);

        UUID serverUUID = UUID.randomUUID();
        int port = Integer.parseInt(System.getenv().getOrDefault("MANAGEMENT_PORT", "8085"));
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/health", exchange -> {
            String response = String.format("{\"status\":\"UP\", \"id\":\"%s\"}", serverUUID);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length());
            exchange.getResponseBody().write(response.getBytes());
            exchange.close();
        });
        server.createContext("/metrics", exchange -> {
            String response = metricsService.getPrometheusMetrics();
            exchange.getResponseHeaders().set("Content-Type", "text/plain");
            exchange.sendResponseHeaders(200, response.length());
            exchange.getResponseBody().write(response.getBytes());
            exchange.close();
        });
        server.setExecutor(null);
        server.start();
        log.info("Health check server started on port {}", port);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down...");
            processor.stop();
        }));

        processor.start();

        log.info("DLQ Application started");

        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            log.info("Main thread interrupted");
            Thread.currentThread().interrupt();
        }
    }
}