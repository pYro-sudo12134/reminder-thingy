package by.losik.service;

import by.losik.dto.DLQMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Singleton
public class DLQProcessorService {
    private static final Logger log = LoggerFactory.getLogger(DLQProcessorService.class);

    private final int maxBatchSize;
    private final int visibilityTimeout;
    private final int notifyAfterAttempts;
    private final SqsAsyncClient sqsAsyncClient;
    private final EmailSenderService emailService;
    private final MetricsService metricsService;
    private final ObjectMapper objectMapper;
    private final String queueUrl;
    private final String notificationEmail;
    private final ExecutorService executor;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private CompletableFuture<Void> pollLoop;

    @Inject
    public DLQProcessorService(
            SqsAsyncClient sqsAsyncClient,
            EmailSenderService emailService,
            MetricsService metricsService,
            ObjectMapper objectMapper,
            @Named("dlq.url") String queueUrl,
            @Named("notification.email") String notificationEmail,
            @Named("max.batch.size") int maxBatchSize,
            @Named("notify.after.attempts") int notifyAfterAttempts,
            @Named("visibility.timeout") int visibilityTimeout) {

        this.sqsAsyncClient = sqsAsyncClient;
        this.emailService = emailService;
        this.metricsService = metricsService;
        this.objectMapper = objectMapper;
        this.queueUrl = queueUrl;
        this.notificationEmail = notificationEmail;
        this.maxBatchSize = maxBatchSize;
        this.notifyAfterAttempts = notifyAfterAttempts;
        this.visibilityTimeout = visibilityTimeout;
        this.executor = Executors.newFixedThreadPool(5);
    }

    public void start() {
        log.info("Starting DLQ processor for: {}", queueUrl);
        pollLoop = pollContinuously();
    }

    private CompletableFuture<Void> pollContinuously() {
        if (!running.get()) {
            return CompletableFuture.completedFuture(null);
        }

        return pollBatch()
                .thenCompose(count -> {
                    if (!running.get()) {
                        return CompletableFuture.completedFuture(null);
                    }
                    CompletableFuture<Void> delay = new CompletableFuture<>();
                    executor.execute(() -> {
                        try {
                            Thread.sleep(5000);
                            delay.complete(null);
                        } catch (InterruptedException e) {
                            delay.completeExceptionally(e);
                            Thread.currentThread().interrupt();
                        }
                    });
                    return delay.thenCompose(v -> pollContinuously());
                })
                .exceptionally(throwable -> {
                    log.error("Polling error: {}", throwable.getMessage());
                    if (running.get()) {
                        try {
                            Thread.sleep(10000);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        return pollContinuously().join();
                    }
                    return null;
                });
    }

    private CompletableFuture<Integer> pollBatch() {
        ReceiveMessageRequest request = ReceiveMessageRequest.builder()
                .queueUrl(queueUrl)
                .maxNumberOfMessages(maxBatchSize)
                .waitTimeSeconds(10)
                .visibilityTimeout(visibilityTimeout)
                .attributeNamesWithStrings("ApproximateReceiveCount", "SentTimestamp")
                .build();

        return sqsAsyncClient.receiveMessage(request)
                .thenCompose(response -> {
                    List<Message> messages = response.messages();
                    metricsService.recordQueueSize(messages.size());
                    if (messages.isEmpty()) {
                        return CompletableFuture.completedFuture(0);
                    }

                    log.info("Received {} messages from DLQ", messages.size());
                    metricsService.incrementMessagesReceived();

                    List<CompletableFuture<Void>> processors = messages.stream()
                            .map(this::processMessage)
                            .toList();

                    return CompletableFuture.allOf(
                            processors.toArray(new CompletableFuture[0])
                    ).thenApply(v -> messages.size());
                });
    }

    private CompletableFuture<Void> processMessage(Message sqsMessage) {
        String messageId = sqsMessage.messageId();
        Timer.Sample timer = metricsService.startTimer();

        return CompletableFuture.supplyAsync(() -> {
                    try {
                        int receiveCount = 1;
                        String countAttr = sqsMessage.attributes().get(MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT);
                        if (countAttr != null) {
                            receiveCount = Integer.parseInt(countAttr);
                        }

                        log.info("Processing message {} (attempt #{})", messageId, receiveCount);

                        DLQMessage message = parseMessage(sqsMessage, receiveCount);

                        metricsService.incrementMessagesProcessed(
                                message.getSource(),
                                receiveCount >= notifyAfterAttempts ? "alerted" : "processed"
                        );

                        if (receiveCount >= notifyAfterAttempts) {
                            log.info("Sending notification for message {} (attempt {})", messageId, receiveCount);

                            String msgId = emailService.sendDLQAlert(message, notificationEmail).join();

                            if (msgId != null) {
                                log.info("Notification sent successfully for message {}", messageId);
                                metricsService.incrementEmailsSent(message.getSource());
                            }
                        }

                        metricsService.stopTimer(timer, "process", message.getSource());

                        return sqsMessage;

                    } catch (Exception e) {
                        log.error("Error processing message {}: {}", messageId, e.getMessage());
                        metricsService.incrementErrors("processing", "unknown");
                        metricsService.stopTimer(timer, "process", "failed");

                        throw new RuntimeException(e);
                    }
                }, executor)
                .thenCompose(msg -> {
                    try {
                        int receiveCount = 1;
                        String countAttr = msg.attributes().get(MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT);
                        if (countAttr != null) {
                            receiveCount = Integer.parseInt(countAttr);
                        }

                        if (receiveCount >= notifyAfterAttempts) {
                            return deleteMessage(msg);
                        } else {
                            log.debug("Message {} (attempt {}) will be retried later", msg.messageId(), receiveCount);
                            return CompletableFuture.completedFuture(null);
                        }
                    } catch (Exception e) {
                        log.error("Error checking receive count for deletion: {}", e.getMessage());
                        return CompletableFuture.completedFuture(null);
                    }
                })
                .exceptionally(throwable -> {
                    log.debug("Message {} will be retried later (current attempt count will increase)", messageId);
                    return null;
                });
    }

    private DLQMessage parseMessage(Message sqsMessage, int receiveCount) {
        DLQMessage message = new DLQMessage();
        message.setMessageId(sqsMessage.messageId());
        message.setReceiveCount(receiveCount);
        message.setQueueName(queueUrl.substring(queueUrl.lastIndexOf('/') + 1));
        message.setTimestamp(Instant.now());
        message.setRawBody(sqsMessage.body());

        try {
            Map<String, Object> body = objectMapper.readValue(
                    sqsMessage.body(),
                    objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class)
            );

            message.setSource((String) body.getOrDefault("source", "unknown"));
            message.setDetailType((String) body.getOrDefault("detail-type", "unknown"));

            Object detail = body.get("detail");
            if (detail instanceof Map) {
                message.setDetail((Map<String, Object>) detail);

                Map<String, Object> detailMap = (Map<String, Object>) detail;
                if (detailMap.containsKey("ErrorMessage")) {
                    message.setErrorMessage((String) detailMap.get("ErrorMessage"));
                } else if (detailMap.containsKey("errorMessage")) {
                    message.setErrorMessage((String) detailMap.get("errorMessage"));
                }
            } else if (detail != null) {
                Map<String, Object> wrapped = new HashMap<>();
                wrapped.put("value", detail);
                message.setDetail(wrapped);
            }

            if (body.containsKey("time")) {
                message.setTimestamp(Instant.parse((String) body.get("time")));
            } else if (body.containsKey("timestamp")) {
                message.setTimestamp(Instant.parse((String) body.get("timestamp")));
            }

        } catch (Exception e) {
            log.warn("Failed to parse message as JSON: {}", e.getMessage());
            message.setSource("parse-error");
            message.setDetailType("unknown");
            message.setErrorMessage("Failed to parse: " + e.getMessage());

            Map<String, Object> detail = new HashMap<>();
            detail.put("rawBody", sqsMessage.body());
            detail.put("parseError", e.getMessage());
            message.setDetail(detail);
        }

        return message;
    }

    private CompletableFuture<Void> deleteMessage(Message message) {
        DeleteMessageRequest request = DeleteMessageRequest.builder()
                .queueUrl(queueUrl)
                .receiptHandle(message.receiptHandle())
                .build();

        return sqsAsyncClient.deleteMessage(request)
                .thenAccept(response ->
                        log.info("Deleted message: {}", message.messageId()))
                .exceptionally(throwable -> {
                    log.error("Failed to delete message {}: {}",
                            message.messageId(), throwable.getMessage());
                    return null;
                });
    }

    @Inject
    public void startStatsLogging() {
        Executors.newSingleThreadScheduledExecutor()
                .scheduleAtFixedRate(() -> {
                    if (running.get()) {
                        metricsService.logStats();
                    }
                }, 5, 5, TimeUnit.MINUTES);
    }

    public void stop() {
        log.info("Stopping DLQ processor...");
        running.set(false);

        if (pollLoop != null) {
            pollLoop.cancel(true);
        }

        executor.shutdown();
        emailService.shutdown();

        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        log.info("DLQ processor stopped");
    }
}