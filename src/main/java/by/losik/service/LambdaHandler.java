package by.losik.service;

import by.losik.config.LocalStackConfig;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.lambda.LambdaAsyncClient;
import software.amazon.awssdk.services.lambda.model.CreateFunctionRequest;
import software.amazon.awssdk.services.lambda.model.CreateFunctionResponse;
import software.amazon.awssdk.services.lambda.model.FunctionCode;
import software.amazon.awssdk.services.lambda.model.Runtime;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;

@Singleton
public class LambdaHandler {
    private final LambdaAsyncClient lambdaClient;

    @Inject
    public LambdaHandler(LocalStackConfig config) {
        this.lambdaClient = config.getLambdaAsyncClient();
    }

    public CompletableFuture<String> deployLambdaFunction() {
        try {
            byte[] lambdaCode = Files.readAllBytes(Paths.get("build/libs/send-reminder-lambda.jar"));

            CreateFunctionRequest request = CreateFunctionRequest.builder()
                    .functionName("send-reminder")
                    .runtime(Runtime.JAVA17)
                    .role("arn:aws:iam::000000000000:role/lambda-role")
                    .handler("by.losik.lambda.SendReminderLambda::handleRequest")
                    .code(FunctionCode.builder()
                            .zipFile(SdkBytes.fromByteArray(lambdaCode))
                            .build())
                    .timeout(30)
                    .memorySize(512)
                    .build();

            return lambdaClient.createFunction(request)
                    .thenApply(CreateFunctionResponse::functionArn)
                    .exceptionally(ex -> {
                        System.err.println("Failed to deploy lambda: " + ex.getMessage());
                        return null;
                    });

        } catch (Exception e) {
            CompletableFuture<String> future = new CompletableFuture<>();
            future.completeExceptionally(e);
            return future;
        }
    }
}