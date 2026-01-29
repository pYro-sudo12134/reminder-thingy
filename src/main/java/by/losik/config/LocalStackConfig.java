package by.losik.config;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.ssl.SSLContextBuilder;
import org.opensearch.client.Request;
import org.opensearch.client.Response;
import org.opensearch.client.RestClient;
import org.opensearch.client.RestClientBuilder;
import org.opensearch.client.RestHighLevelClient;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClient;
import software.amazon.awssdk.services.eventbridge.EventBridgeAsyncClient;
import software.amazon.awssdk.services.lambda.LambdaAsyncClient;
import software.amazon.awssdk.services.opensearch.OpenSearchAsyncClient;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.ses.SesAsyncClient;
import software.amazon.awssdk.services.transcribe.TranscribeAsyncClient;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Optional;

@Singleton
public class LocalStackConfig {
    private final String TRUSTSTORE_PATH;
    private final String TRUSTSTORE_PASSWORD;
    private final String LOCALSTACK_ENDPOINT;
    private final String REGION;
    private final String ACCESS_KEY;
    private final String SECRET_KEY;
    private final String EMAIL;
    private final String OPENSEARCH_HOST;
    private final String OPENSEARCH_PORT;
    private final EventBridgeAsyncClient eventBridgeAsyncClient;
    private final S3AsyncClient s3AsyncClient;
    private final TranscribeAsyncClient transcribeAsyncClient;
    private final LambdaAsyncClient lambdaAsyncClient;
    private final RestHighLevelClient openSearchClient;
    private final OpenSearchAsyncClient openSearchAsyncClient;
    private final SdkAsyncHttpClient asyncHttpClient;
    private final SesAsyncClient sesAsyncClient;
    private final CloudWatchAsyncClient cloudWatchAsyncClient;

    @Inject
    public LocalStackConfig(String localstackEndpoint, String region, String accessKey,
                            String secretKey, String email, String openSearchPort,
                            String openSearchHost, String truststorePath,
                            String truststorePassword,
                            SecretsManagerConfig secretsManagerConfig) {
        this.LOCALSTACK_ENDPOINT = localstackEndpoint;
        this.REGION = region;
        this.ACCESS_KEY = accessKey;
        this.SECRET_KEY = secretKey;
        this.EMAIL = email;
        this.OPENSEARCH_HOST = openSearchHost;
        this.OPENSEARCH_PORT = openSearchPort;
        this.TRUSTSTORE_PATH = truststorePath;
        this.TRUSTSTORE_PASSWORD = truststorePassword;
        this.asyncHttpClient = createAsyncHttpClient();
        this.eventBridgeAsyncClient = createEventBridgeAsyncClient();
        this.s3AsyncClient = createS3AsyncClient();
        this.transcribeAsyncClient = createTranscribeAsyncClient();
        this.lambdaAsyncClient = createLambdaAsyncClient();
        this.openSearchClient = createOpenSearchClient(secretsManagerConfig);
        this.openSearchAsyncClient = createOpenSearchAsyncClient();
        this.sesAsyncClient = createSesAsyncClient();
        this.cloudWatchAsyncClient = createCloudWatchAsyncClient();
    }

    public EventBridgeAsyncClient getEventBridgeAsyncClient() {
        return eventBridgeAsyncClient;
    }

    public S3AsyncClient getS3AsyncClient() {
        return s3AsyncClient;
    }

    public TranscribeAsyncClient getTranscribeAsyncClient() {
        return transcribeAsyncClient;
    }

    public LambdaAsyncClient getLambdaAsyncClient() {
        return lambdaAsyncClient;
    }
    public RestHighLevelClient getOpenSearchClient() {
        return openSearchClient;
    }

    public OpenSearchAsyncClient getOpenSearchAsyncClient() {
        return openSearchAsyncClient;
    }

    public SdkAsyncHttpClient getAsyncHttpClient() {
        return asyncHttpClient;
    }

    public String getLocalstackEndpoint() {
        return LOCALSTACK_ENDPOINT;
    }

    public CloudWatchAsyncClient getCloudWatchAsyncClient() {
        return this.cloudWatchAsyncClient;
    }
    public String getRegion() {
        return REGION;
    }
    public String getEmail() {
        return EMAIL;
    }

    public String getAccessKeyId() {
        return ACCESS_KEY;
    }

    private SdkAsyncHttpClient createAsyncHttpClient() {
        return NettyNioAsyncHttpClient.builder()
                .connectionTimeout(Duration.ofSeconds(10))
                .connectionMaxIdleTime(Duration.ofSeconds(5))
                .maxConcurrency(100)
                .build();
    }

    private CloudWatchAsyncClient createCloudWatchAsyncClient() {
        return CloudWatchAsyncClient.builder()
                .endpointOverride(URI.create(LOCALSTACK_ENDPOINT))
                .credentialsProvider(createCredentialsProvider())
                .region(Region.US_EAST_1)
                .build();
    }

    private EventBridgeAsyncClient createEventBridgeAsyncClient() {
        return EventBridgeAsyncClient.builder()
                .endpointOverride(URI.create(LOCALSTACK_ENDPOINT))
                .region(Region.of(REGION))
                .credentialsProvider(createCredentialsProvider())
                .httpClient(asyncHttpClient)
                .build();
    }

    private S3AsyncClient createS3AsyncClient() {
        return S3AsyncClient.builder()
                .endpointOverride(URI.create(LOCALSTACK_ENDPOINT))
                .region(Region.of(REGION))
                .credentialsProvider(createCredentialsProvider())
                .serviceConfiguration(s3 -> s3
                        .pathStyleAccessEnabled(true)
                )
                .httpClient(asyncHttpClient)
                .build();
    }

    private SesAsyncClient createSesAsyncClient() {
        return SesAsyncClient.builder()
                .endpointOverride(java.net.URI.create(LOCALSTACK_ENDPOINT))
                .region(software.amazon.awssdk.regions.Region.of(REGION))
                .credentialsProvider(software.amazon.awssdk.auth.credentials.StaticCredentialsProvider.create(
                        software.amazon.awssdk.auth.credentials.AwsBasicCredentials.create(
                                ACCESS_KEY, SECRET_KEY
                        )
                ))
                .build();
    }

    private TranscribeAsyncClient createTranscribeAsyncClient() {
        return TranscribeAsyncClient.builder()
                .endpointOverride(URI.create(LOCALSTACK_ENDPOINT))
                .region(Region.of(REGION))
                .credentialsProvider(createCredentialsProvider())
                .httpClient(asyncHttpClient)
                .build();
    }

    private LambdaAsyncClient createLambdaAsyncClient() {
        return LambdaAsyncClient.builder()
                .endpointOverride(URI.create(LOCALSTACK_ENDPOINT))
                .region(Region.of(REGION))
                .credentialsProvider(createCredentialsProvider())
                .httpClient(asyncHttpClient)
                .build();
    }

    private RestHighLevelClient createOpenSearchClient(SecretsManagerConfig secretsConfig) {
        try {
            String host = secretsConfig.getSecret("OPENSEARCH_HOST",
                    Optional.ofNullable(System.getenv("OPENSEARCH_HOST"))
                            .or(() -> Optional.ofNullable(System.getProperty("OPENSEARCH_HOST")))
                            .orElse(OPENSEARCH_HOST));

            String httpsPort = secretsConfig.getSecret("OPENSEARCH_HTTPS_PORT",
                    Optional.ofNullable(System.getenv("OPENSEARCH_HTTPS_PORT"))
                            .or(() -> Optional.ofNullable(System.getProperty("OPENSEARCH_HTTPS_PORT")))
                            .orElse(OPENSEARCH_PORT));

            String username = secretsConfig.getSecret("OPENSEARCH_USER",
                    Optional.ofNullable(System.getenv("OPENSEARCH_USER"))
                            .or(() -> Optional.ofNullable(System.getProperty("OPENSEARCH_USER")))
                            .orElse("admin"));

            String password = secretsConfig.getSecret("OPENSEARCH_PASSWORD",
                    Optional.ofNullable(System.getenv("OPENSEARCH_ADMIN_PASSWORD"))
                            .or(() -> Optional.ofNullable(System.getProperty("OPENSEARCH_PASSWORD")))
                            .orElse("admin123"));

            boolean useSsl = Boolean.parseBoolean(secretsConfig.getSecret("OPENSEARCH_USE_SSL",
                    Optional.ofNullable(System.getenv("OPENSEARCH_USE_SSL"))
                            .or(() -> Optional.ofNullable(System.getProperty("OPENSEARCH_USE_SSL")))
                            .orElse("false")));

            boolean disableSslVerification = Boolean.parseBoolean(
                    secretsConfig.getSecret("OPENSEARCH_DISABLE_SSL_VERIFICATION",
                            Optional.ofNullable(System.getenv("OPENSEARCH_DISABLE_SSL_VERIFICATION"))
                                    .or(() -> Optional.ofNullable(System.getProperty("OPENSEARCH_DISABLE_SSL_VERIFICATION")))
                                    .orElse("true")));

            String protocol = useSsl ? "https" : "http";
            String endpoint = protocol + "://" + host + ":" + httpsPort;

            System.out.println("Creating OpenSearch client for endpoint: " + endpoint);
            System.out.println("Using truststore: " + TRUSTSTORE_PATH);

            CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(
                    AuthScope.ANY,
                    new UsernamePasswordCredentials(username, password)
            );

            RestClientBuilder builder = RestClient.builder(
                    HttpHost.create(endpoint)
            );

            if (useSsl) {
                SSLContext sslContext;
                if (disableSslVerification) {
                    sslContext = SSLContextBuilder
                            .create()
                            .loadTrustMaterial(null, (chain, authType) -> true)
                            .build();

                    builder.setHttpClientConfigCallback(httpClientBuilder ->
                            httpClientBuilder
                                    .setSSLContext(sslContext)
                                    .setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE)
                                    .setDefaultCredentialsProvider(credentialsProvider)
                    );
                } else {
                    Path actualTruststorePath = Paths.get(TRUSTSTORE_PATH);
                    if (Files.exists(actualTruststorePath)) {
                        sslContext = SSLContextBuilder
                                .create()
                                .loadTrustMaterial(
                                        actualTruststorePath.toFile(),
                                        TRUSTSTORE_PASSWORD.toCharArray()
                                )
                                .build();

                        builder.setHttpClientConfigCallback(httpClientBuilder ->
                                httpClientBuilder
                                        .setSSLContext(sslContext)
                                        .setDefaultCredentialsProvider(credentialsProvider)
                        );
                    } else {
                        System.err.println("WARNING: Truststore not found at: " + TRUSTSTORE_PATH);
                        sslContext = SSLContext.getDefault();
                        builder.setHttpClientConfigCallback(httpClientBuilder ->
                                httpClientBuilder
                                        .setSSLContext(sslContext)
                                        .setDefaultCredentialsProvider(credentialsProvider)
                        );
                    }
                }
            } else {
                builder.setHttpClientConfigCallback(httpClientBuilder ->
                        httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider)
                );
            }

            RestHighLevelClient client = new RestHighLevelClient(builder);

            try {
                Request request = new Request("GET", "/");
                Response response = client.getLowLevelClient().performRequest(request);
                System.out.println("OpenSearch connection successful, endpoint: " + endpoint);
                System.out.println("Response: " + response.getStatusLine());
            } catch (Exception e) {
                System.err.println("OpenSearch connection test failed: " + e.getMessage());
                System.err.println("But continuing with client creation anyway");
            }

            return client;

        } catch (Exception e) {
            System.err.println("Failed to create OpenSearch client: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("OpenSearch client creation failed", e);
        }
    }

    private OpenSearchAsyncClient createOpenSearchAsyncClient() {
        return OpenSearchAsyncClient.builder()
                .endpointOverride(URI.create(LOCALSTACK_ENDPOINT))
                .region(Region.of(REGION))
                .credentialsProvider(createCredentialsProvider())
                .httpClient(asyncHttpClient)
                .build();
    }

    private StaticCredentialsProvider createCredentialsProvider() {
        return StaticCredentialsProvider.create(
                AwsBasicCredentials.create(ACCESS_KEY, SECRET_KEY)
        );
    }


    public String getS3Endpoint() {
        return LOCALSTACK_ENDPOINT;
    }

    public String getTranscribeEndpoint() {
        return LOCALSTACK_ENDPOINT;
    }

    public String getOpenSearchEndpoint() {
        String host = Optional.ofNullable(OPENSEARCH_HOST)
                .filter(h -> !h.isEmpty())
                .orElse("localstack");

        String port = Optional.ofNullable(OPENSEARCH_PORT)
                .filter(p -> !p.isEmpty())
                .orElse("4510");

        return "http://" + host + ":" + port;
    }

    public void shutdown() {
        if (eventBridgeAsyncClient != null) {
            eventBridgeAsyncClient.close();
        }
        if (s3AsyncClient != null) {
            s3AsyncClient.close();
        }
        if (transcribeAsyncClient != null) {
            transcribeAsyncClient.close();
        }
        if (lambdaAsyncClient != null) {
            lambdaAsyncClient.close();
        }
        if (openSearchClient != null) {
            try {
                openSearchClient.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (openSearchAsyncClient != null) {
            openSearchAsyncClient.close();
        }
    }

    public SesAsyncClient getSesAsyncClient() {
        return sesAsyncClient;
    }

    public String getSecretKey() {
        return SECRET_KEY;
    }
}