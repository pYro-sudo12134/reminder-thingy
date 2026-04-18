package by.losik.service;

import by.losik.config.LocalStackConfig;
import by.losik.config.TranscribeConfig;
import by.losik.dto.TranscriptionJobResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.transcribe.TranscribeAsyncClient;
import software.amazon.awssdk.services.transcribe.model.GetTranscriptionJobRequest;
import software.amazon.awssdk.services.transcribe.model.GetTranscriptionJobResponse;
import software.amazon.awssdk.services.transcribe.model.Media;
import software.amazon.awssdk.services.transcribe.model.StartTranscriptionJobRequest;
import software.amazon.awssdk.services.transcribe.model.TranscriptionJob;
import software.amazon.awssdk.services.transcribe.model.TranscriptionJobStatus;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Сервис для транскрибации аудио через AWS Transcribe.
 * <p>
 * Предоставляет методы для:
 * <ul>
 *     <li>Запуска задачи транскрибации</li>
 *     <li>Polling статуса выполнения</li>
 *     <li>Скачивания и парсинга результата</li>
 * </ul>
 * <p>
 * Использует асинхронный TranscribeAsyncClient и ScheduledExecutorService для polling.
 *
 * @see TranscribeConfig
 */
@Singleton
public class TranscribeService implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(TranscribeService.class);

    private final TranscribeAsyncClient transcribeAsyncClient;
    private final S3Service s3Service;
    private final ObjectMapper objectMapper;
    private final ScheduledExecutorService scheduler;
    private final TranscribeConfig config;

    /**
     * Создаёт транскрибация сервис с конфигурацией.
     *
     * @param localStackConfig конфигурация LocalStack для клиента
     * @param s3Service сервис для работы с S3
     * @param config конфигурация транскрибации
     */
    @Inject
    public TranscribeService(LocalStackConfig localStackConfig, S3Service s3Service, TranscribeConfig config) {
        this.transcribeAsyncClient = localStackConfig.getTranscribeAsyncClient();
        this.s3Service = s3Service;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.findAndRegisterModules();
        this.scheduler = Executors.newScheduledThreadPool(2);
        this.config = config;
    }

    /**
     * Запускает задачу транскрибации аудиофайла.
     *
     * @param audioKey ключ аудиофайла в S3
     * @param bucketName имя бакета S3
     * @return информация о запущенной задаче транскрибации
     */
    public CompletableFuture<TranscriptionJobResponse> startTranscriptionAsync(
            String audioKey, String bucketName) {

        String jobName = "transcribe-job-" + System.currentTimeMillis();

        Media media = Media.builder()
                .mediaFileUri(String.format("s3://%s/%s", bucketName, audioKey))
                .build();

        StartTranscriptionJobRequest request = StartTranscriptionJobRequest.builder()
                .transcriptionJobName(jobName)
                .languageCode(config.getLanguageCode())
                .mediaFormat(config.getMediaFormat())
                .media(media)
                .build();

        return transcribeAsyncClient.startTranscriptionJob(request)
                .thenApply(response -> {
                    log.info("Transcription job started: {}", jobName);
                    return new TranscriptionJobResponse(
                            jobName,
                            response.transcriptionJob().transcriptionJobStatusAsString(),
                            null,
                            null,
                            null,
                            LocalDateTime.now(),
                            null
                    );
                })
                .exceptionally(ex -> {
                    log.error("Failed to start transcription job", ex);
                    throw new RuntimeException("Failed to start transcription", ex);
                });
    }

    /**
     * Ожидает завершения задачи транскрибации и возвращает текст.
     * <p>
     * Использует polling для проверки статуса задачи.
     *
     * @param jobId ID задачи транскрибации
     * @param maxAttempts максимальное количество попыток polling
     * @param pollIntervalMs интервал между попытками в миллисекундах
     * @return текст транскрипции
     */
    public CompletableFuture<String> waitAndGetTranscriptionResultAsync(
            String jobId, int maxAttempts, long pollIntervalMs) {

        CompletableFuture<String> resultFuture = new CompletableFuture<>();
        scheduler.schedule(new Runnable() {
            private int attempts = 0;

            @Override
            public void run() {
                if (attempts >= maxAttempts) {
                    log.warn("Transcription timeout after {} attempts for job {}", maxAttempts, jobId);
                    resultFuture.completeExceptionally(
                            new RuntimeException("Transcription timeout after " + maxAttempts + " attempts"));
                    return;
                }

                attempts++;
                log.debug("Checking transcription job {} (attempt {}/{})", jobId, attempts, maxAttempts);

                getTranscriptionJobAsync(jobId)
                        .thenAccept(job -> {
                            TranscriptionJobStatus status = job.transcriptionJobStatus();
                            log.debug("Job {} status: {}", jobId, status);

                            if (status == TranscriptionJobStatus.COMPLETED) {
                                log.info("Transcription job {} completed successfully", jobId);
                                downloadTranscriptionText(job.transcript().transcriptFileUri())
                                        .thenAccept(transcript -> {
                                            log.info("Successfully processed transcription for job {}", jobId);
                                            resultFuture.complete(transcript);
                                        })
                                        .exceptionally(ex -> {
                                            log.error("Failed to download transcription for job {}", jobId, ex);
                                            resultFuture.completeExceptionally(
                                                    new RuntimeException("Failed to download transcription", ex));
                                            return null;
                                        });
                            } else if (status == TranscriptionJobStatus.FAILED) {
                                String failureReason = job.failureReason() != null ? job.failureReason() : "Unknown reason";
                                log.error("Transcription job {} failed: {}", jobId, failureReason);
                                resultFuture.completeExceptionally(
                                        new RuntimeException("Transcription failed: " + failureReason));
                            } else {
                                log.debug("Job {} still in progress, scheduling next check in {}ms",
                                        jobId, pollIntervalMs);
                                scheduler.schedule(this, pollIntervalMs, TimeUnit.MILLISECONDS);
                            }
                        })
                        .exceptionally(ex -> {
                            log.error("Failed to check transcription status for job {}", jobId, ex);
                            resultFuture.completeExceptionally(
                                    new RuntimeException("Failed to check transcription status", ex));
                            return null;
                        });
            }
        }, 0, TimeUnit.MILLISECONDS);

        return resultFuture;
    }

    /**
     * Транскрибирует аудиофайл из S3.
     * <p>
     * Запускает задачу транскрибации и ожидает результат.
     *
     * @param audioKey ключ аудиофайла в S3
     * @return текст транскрипции
     */
    public CompletableFuture<String> transcribeAudioFileAsync(String audioKey) {
        String bucketName = s3Service.getBucketName();

        return startTranscriptionAsync(audioKey, bucketName)
                .thenCompose(response ->
                        waitAndGetTranscriptionResultAsync(
                                response.jobId(),
                                config.getMaxPollAttempts(),
                                config.getPollIntervalMs()
                        )
                );
    }

    /**
     * Получает информацию о задаче транскрибации.
     *
     * @param jobId ID задачи
     * @return информация о задаче
     */
    private CompletableFuture<TranscriptionJob> getTranscriptionJobAsync(String jobId) {
        GetTranscriptionJobRequest request = GetTranscriptionJobRequest.builder()
                .transcriptionJobName(jobId)
                .build();

        return transcribeAsyncClient.getTranscriptionJob(request)
                .thenApply(GetTranscriptionJobResponse::transcriptionJob);
    }

    /**
     * Скачивает текст транскрипции из S3.
     *
     * @param transcriptUri URI файла с транскрипцией
     * @return текст транскрипции
     */
    private CompletableFuture<String> downloadTranscriptionText(String transcriptUri) {
        return CompletableFuture.supplyAsync(() -> {
            Path tempFile = null;
            try {
                log.info("Attempting to download transcription from: {}", transcriptUri);

                URI uri = URI.create(transcriptUri);
                String path = parseS3PathFromUri(uri);

                log.info("Downloading transcription from bucket: {}, key: {}", 
                        extractBucketFromS3Path(path), extractKeyFromS3Path(path));

                tempFile = Files.createTempFile("transcription", ".json");
                tempFile.toFile().deleteOnExit();

                s3Service.downloadFileAsync(
                        extractKeyFromS3Path(path),
                        tempFile,
                        extractBucketFromS3Path(path)
                ).get(config.getTranscriptionTimeoutSec(), TimeUnit.SECONDS);

                String jsonContent = Files.readString(tempFile);
                log.info("Downloaded transcription JSON, size: {} bytes", jsonContent.length());

                return parseTranscriptionJson(jsonContent);

            } catch (Exception e) {
                log.error("Failed to download transcription from URI: {}", transcriptUri, e);
                throw new RuntimeException("Failed to download transcription", e);
            } finally {
                if (tempFile != null) {
                    try {
                        Files.deleteIfExists(tempFile);
                    } catch (IOException e) {
                        log.debug("Could not delete temp file immediately: {}", tempFile);
                    }
                }
            }
        });
    }

    /**
     * Парсит S3 путь из URI.
     *
     * @param uri URI транскрипции
     * @return S3 путь (bucket/key)
     */
    private String parseS3PathFromUri(URI uri) {
        String path = uri.getPath();
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        int questionMarkIndex = path.indexOf('?');
        if (questionMarkIndex != -1) {
            path = path.substring(0, questionMarkIndex);
        }
        return path;
    }

    /**
     * Извлекает имя бакета из S3 пути.
     *
     * @param s3Path S3 путь (bucket/key)
     * @return имя бакета
     */
    private String extractBucketFromS3Path(String s3Path) {
        int firstSlash = s3Path.indexOf('/');
        if (firstSlash == -1) {
            throw new RuntimeException("Invalid S3 path: " + s3Path);
        }
        return s3Path.substring(0, firstSlash);
    }

    /**
     * Извлекает ключ файла из S3 пути.
     *
     * @param s3Path S3 путь (bucket/key)
     * @return ключ файла
     */
    private String extractKeyFromS3Path(String s3Path) {
        int firstSlash = s3Path.indexOf('/');
        if (firstSlash == -1) {
            throw new RuntimeException("Invalid S3 path: " + s3Path);
        }
        String key = s3Path.substring(firstSlash + 1);
        return java.net.URLDecoder.decode(key, StandardCharsets.UTF_8);
    }

    /**
     * Парсит текст транскрипции из JSON ответа AWS Transcribe.
     *
     * @param jsonContent JSON контент транскрипции
     * @return текст транскрипции
     * @throws Exception если не удалось распарсить JSON
     */
    private String parseTranscriptionJson(String jsonContent) throws Exception {
        JsonNode root = objectMapper.readTree(jsonContent);
        String transcript = null;

        if (root.has("results") && root.get("results").has("transcripts")) {
            JsonNode transcripts = root.get("results").get("transcripts");
            if (transcripts.isArray() && transcripts.size() > 0) {
                transcript = transcripts.get(0).path("transcript").asText();
            }
        }

        if (transcript == null && root.has("text")) {
            transcript = root.get("text").asText();
        }

        if (transcript == null && root.has("transcript")) {
            transcript = root.get("transcript").asText();
        }

        if (transcript == null) {
            log.warn("Unexpected transcription format: {}", jsonContent);
            throw new RuntimeException("Could not parse transcription from JSON");
        }

        log.info("Parsed transcription: {}", transcript);
        return transcript;
    }

    /**
     * Закрывает сервис и освобождает ресурсы.
     * <p>
     * Останавливает ScheduledExecutorService.
     */
    @Override
    public void close() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
                log.info("TranscribeService scheduler shutdown successfully");
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
                log.error("Scheduler shutdown interrupted", e);
            }
        }
    }
}