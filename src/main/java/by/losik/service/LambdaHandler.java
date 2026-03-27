package by.losik.service;

import by.losik.config.LambdaConfig;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.lambda.LambdaAsyncClient;
import software.amazon.awssdk.services.lambda.model.CreateFunctionRequest;
import software.amazon.awssdk.services.lambda.model.CreateFunctionResponse;
import software.amazon.awssdk.services.lambda.model.FunctionCode;
import software.amazon.awssdk.services.lambda.model.ResourceConflictException;
import software.amazon.awssdk.services.lambda.model.Runtime;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;

/**
 * Обработчик для деплоя Lambda функций.
 * <p>
 * Отвечает за загрузку и развёртывание кода Lambda функции в AWS/LocalStack.
 * Используется для деплоя функции отправки напоминаний (send-reminder).
 * <p>
 * Деплой выполняется только если:
 * <ul>
 *     <li>Включено через {@code LAMBDA_DEPLOY_ENABLED=true}</li>
 *     <li>JAR файл существует по пути {@code LAMBDA_JAR_PATH}</li>
 * </ul>
 *
 * @see LambdaConfig
 * @see by.losik.lambda.SendReminderLambda
 */
@Singleton
public class LambdaHandler {

    private static final Logger log = LoggerFactory.getLogger(LambdaHandler.class);

    private final LambdaAsyncClient lambdaClient;
    private final LambdaConfig config;

    /**
     * Создаёт обработчик Lambda с внедрёнными зависимостями.
     *
     * @param lambdaClient клиент AWS Lambda
     * @param config конфигурация Lambda
     */
    @Inject
    public LambdaHandler(LambdaAsyncClient lambdaClient, LambdaConfig config) {
        this.lambdaClient = lambdaClient;
        this.config = config;
    }

    /**
     * Развёртывает Lambda функцию если деплой включён и функция ещё не существует.
     *
     * @return CompletableFuture с ARN функции или null если деплой пропущен
     */
    public CompletableFuture<String> deployLambdaFunctionIfNotExists() {
        if (!config.isDeployEnabled()) {
            log.info("Lambda deploy is disabled (LAMBDA_DEPLOY_ENABLED=false)");
            return CompletableFuture.completedFuture(null);
        }

        return deployLambdaFunction();
    }

    /**
     * Развёртывает Lambda функцию.
     * <p>
     * Если функция уже существует, возвращает её ARN без ошибки.
     *
     * @return CompletableFuture с ARN функции
     */
    public CompletableFuture<String> deployLambdaFunction() {
        String functionName = config.getFunctionName();
        String jarPath = config.getJarPath();

        log.info("Deploying Lambda function: {} from {}", functionName, jarPath);

        if (!Files.exists(Paths.get(jarPath))) {
            log.warn("Lambda JAR file not found at: {}. Skipping deploy.", jarPath);
            return CompletableFuture.completedFuture(null);
        }

        try {
            byte[] lambdaCode = Files.readAllBytes(Paths.get(jarPath));
            log.debug("Loaded Lambda code: {} bytes", lambdaCode.length);

            CreateFunctionRequest request = CreateFunctionRequest.builder()
                    .functionName(functionName)
                    .runtime(Runtime.JAVA17)
                    .role(config.getRoleArn())
                    .handler(config.getHandlerClass())
                    .code(FunctionCode.builder()
                            .zipFile(SdkBytes.fromByteArray(lambdaCode))
                            .build())
                    .timeout(config.getTimeoutSec())
                    .memorySize(config.getMemorySize())
                    .build();

            return lambdaClient.createFunction(request)
                    .thenApply(CreateFunctionResponse::functionArn)
                    .thenApply(arn -> {
                        log.info("Lambda function deployed successfully: {}", arn);
                        return arn;
                    })
                    .exceptionally(ex -> {
                        if (ex.getCause() instanceof ResourceConflictException) {
                            log.info("Lambda function already exists: {}", functionName);
                            return null;
                        }
                        log.error("Failed to deploy Lambda function: {}", ex.getMessage(), ex);
                        return null;
                    });

        } catch (Exception e) {
            log.error("Failed to read Lambda JAR file: {}", e.getMessage(), e);
            CompletableFuture<String> future = new CompletableFuture<>();
            future.completeExceptionally(e);
            return future;
        }
    }
}
