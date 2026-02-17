package by.losik.composition.root;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import jakarta.mail.Authenticator;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClient;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

import java.net.URI;
import java.time.Duration;
import java.util.Properties;

public class AWSModule extends AbstractModule {

    private final String environment;
    private final String awsEndpoint;
    private final boolean useLocalstack;
    private final String queueSuffix;

    public AWSModule(String environment, String awsEndpoint, String queueSuffix, boolean useLocalstack) {
        this.environment = environment;
        this.awsEndpoint = awsEndpoint;
        this.queueSuffix = queueSuffix;
        this.useLocalstack = useLocalstack;
    }

    @Override
    protected void configure() {
        bind(String.class).annotatedWith(Names.named("environment")).toInstance(environment);
        bind(String.class).annotatedWith(Names.named("queue.suffix")).toInstance(queueSuffix);
        bind(String.class).annotatedWith(Names.named("from.email"))
                .toInstance(System.getenv().getOrDefault("FROM_EMAIL", "losik2006@gmail.com"));
        bind(String.class).annotatedWith(Names.named("dlq.url"))
                .toInstance(System.getenv().getOrDefault("DLQ_URL",
                        awsEndpoint + "/" +
                                System.getenv().getOrDefault("AWS_ACCOUNT_ID", "000000000000") +
                                "/" + environment + queueSuffix));
        bind(String.class).annotatedWith(Names.named("notification.email"))
                .toInstance(System.getenv().getOrDefault("NOTIFICATION_EMAIL", "losik2006@gmail.com"));

        bind(String.class).annotatedWith(Names.named("smtp.host"))
                .toInstance(System.getenv().getOrDefault("SMTP_HOST", "smtp.gmail.com"));
        bind(String.class).annotatedWith(Names.named("smtp.port"))
                .toInstance(System.getenv().getOrDefault("SMTP_PORT", "587"));
        bind(String.class).annotatedWith(Names.named("smtp.username"))
                .toInstance(System.getenv().getOrDefault("SMTP_USERNAME", "losik2006@gmail.com"));
        bind(String.class).annotatedWith(Names.named("smtp.password"))
                .toInstance(System.getenv().getOrDefault("SMTP_PASSWORD", "pvtorvfjghhixyxy"));
        bind(Boolean.class).annotatedWith(Names.named("smtp.auth"))
                .toInstance(Boolean.parseBoolean(System.getenv().getOrDefault("SMTP_AUTH", "true")));
        bind(Boolean.class).annotatedWith(Names.named("smtp.starttls"))
                .toInstance(Boolean.parseBoolean(System.getenv().getOrDefault("SMTP_STARTTLS", "true")));
        bind(Integer.class).annotatedWith(Names.named("max.batch.size"))
                .toInstance(Integer.parseInt(System.getenv().getOrDefault("MAX_BATCH_SIZE", "10")));
        bind(Integer.class).annotatedWith(Names.named("notify.after.attempts"))
                .toInstance(Integer.parseInt(System.getenv().getOrDefault("NOTIFY_AFTER_ATTEMPTS", "3")));
        bind(Integer.class).annotatedWith(Names.named("visibility.timeout"))
                .toInstance(Integer.parseInt(System.getenv().getOrDefault("VISIBILITY_TIMEOUT", "60")));
    }

    @Provides
    @Singleton
    public AwsCredentialsProvider provideCredentials() {
        if (useLocalstack) {
            return StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(
                            System.getenv().getOrDefault("AWS_ACCESS_KEY_ID", "test"),
                            System.getenv().getOrDefault("AWS_SECRET_ACCESS_KEY", "test"))
            );
        }

        return DefaultCredentialsProvider.create();
    }

    @Provides
    @Singleton
    public SqsAsyncClient provideSqsAsync(AwsCredentialsProvider credentials) {
        var builder = SqsAsyncClient.builder()
                .region(Region.US_EAST_1)
                .credentialsProvider(credentials)
                .httpClientBuilder(NettyNioAsyncHttpClient.builder()
                        .connectionTimeout(Duration.ofSeconds(5))
                        .maxConcurrency(50));

        if (useLocalstack) {
            builder.endpointOverride(URI.create(awsEndpoint));
        }

        return builder.build();
    }

    @Provides
    @Singleton
    public Session provideMailSession(
            @Named("smtp.host") String host,
            @Named("smtp.port") String port,
            @Named("smtp.username") String username,
            @Named("smtp.password") String password,
            @Named("smtp.auth") boolean auth,
            @Named("smtp.starttls") boolean starttls) {

        Properties props = new Properties();
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", port);
        props.put("mail.smtp.auth", String.valueOf(auth));
        props.put("mail.smtp.starttls.enable", String.valueOf(starttls));
        props.put("mail.smtp.connectiontimeout", "5000");
        props.put("mail.smtp.timeout", "5000");
        props.put("mail.smtp.writetimeout", "5000");
        props.put("mail.smtp.ssl.protocols", "TLSv1.2");
        props.put("mail.smtp.ssl.trust", host);

        if (auth && !username.isEmpty() && !password.isEmpty()) {
            return Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(username, password);
                }
            });
        } else {
            return Session.getInstance(props);
        }
    }

    @Provides
    @Singleton
    public ObjectMapper provideObjectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Provides
    @Singleton
    public CloudWatchAsyncClient provideCloudWatchAsync(AwsCredentialsProvider credentials) {
        var builder = CloudWatchAsyncClient.builder()
                .region(Region.US_EAST_1)
                .credentialsProvider(credentials);

        if (useLocalstack) {
            builder.endpointOverride(URI.create(awsEndpoint));
        }

        return builder.build();
    }

    @Provides
    @Named("metrics.enabled")
    public Boolean provideMetricsEnabled() {
        return Boolean.parseBoolean(System.getenv().getOrDefault("METRICS_ENABLED", "true"));
    }
}