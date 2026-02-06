package by.losik.service;

import by.losik.config.LocalStackConfig;
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

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Singleton
public class TranscribeService {

    private static final Logger log = LoggerFactory.getLogger(TranscribeService.class);

    private final TranscribeAsyncClient transcribeAsyncClient;
    private final S3Service s3Service;
    private final ObjectMapper objectMapper;
    private final ScheduledExecutorService scheduler;

    @Inject
    public TranscribeService(LocalStackConfig config, S3Service s3Service) {
        this.transcribeAsyncClient = config.getTranscribeAsyncClient();
        this.s3Service = s3Service;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.findAndRegisterModules();
        this.scheduler = Executors.newScheduledThreadPool(2);
    }

    public CompletableFuture<TranscriptionJobResponse> startTranscriptionAsync(
            String audioKey, String bucketName) {

        String jobName = "transcribe-job-" + System.currentTimeMillis();

        Media media = Media.builder()
                .mediaFileUri(String.format("s3://%s/%s", bucketName, audioKey))
                .build();

        StartTranscriptionJobRequest request = StartTranscriptionJobRequest.builder()
                .transcriptionJobName(jobName)
                .languageCode("ru-RU")
                .mediaFormat("wav")
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

    public CompletableFuture<String> transcribeAudioFileAsync(String audioKey) {
        String bucketName = s3Service.getBucketName();

        return startTranscriptionAsync(audioKey, bucketName)
                .thenCompose(response ->
                        waitAndGetTranscriptionResultAsync(response.jobId(), 180, 2000)
                );
    }

    private CompletableFuture<TranscriptionJob> getTranscriptionJobAsync(String jobId) {
        GetTranscriptionJobRequest request = GetTranscriptionJobRequest.builder()
                .transcriptionJobName(jobId)
                .build();

        return transcribeAsyncClient.getTranscriptionJob(request)
                .thenApply(GetTranscriptionJobResponse::transcriptionJob);
    }

    private CompletableFuture<String> downloadTranscriptionText(String transcriptUri) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("Attempting to download transcription from: {}", transcriptUri);

                URI uri = URI.create(transcriptUri);
                String path = uri.getPath();

                if (path.startsWith("/")) {
                    path = path.substring(1);
                }

                int questionMarkIndex = path.indexOf('?');
                if (questionMarkIndex != -1) {
                    path = path.substring(0, questionMarkIndex);
                }

                int firstSlash = path.indexOf('/');
                if (firstSlash == -1) {
                    throw new RuntimeException("Invalid S3 path in transcript URI: " + path);
                }

                String bucketName = path.substring(0, firstSlash);
                String key = path.substring(firstSlash + 1);

                log.info("Parsed S3 location - bucket: {}, key: {}", bucketName, key);

                Path tempFile = Files.createTempFile("transcription", ".json");

                try {
                    s3Service.downloadFileAsync(key, tempFile, bucketName)
                            .get(30, TimeUnit.SECONDS);

                    String jsonContent = Files.readString(tempFile);
                    log.info("Downloaded transcription JSON, size: {} bytes", jsonContent.length());

                    return parseTranscriptionJson(jsonContent);

                } finally {
                    Files.deleteIfExists(tempFile);
                }

            } catch (Exception e) {
                log.error("Failed to download transcription text from URI: {}", transcriptUri, e);
                throw new RuntimeException("Failed to download transcription from: " + transcriptUri, e);
            }
        });
    }

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
}