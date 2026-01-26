package by.losik.resource;

import by.losik.dto.ReminderRecord;
import by.losik.service.OpenSearchService;
import by.losik.service.VoiceReminderService;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Path("/")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Singleton
public class ReminderResource {
    private static final Logger log = LoggerFactory.getLogger(ReminderResource.class);
    private final VoiceReminderService voiceReminderService;
    private final OpenSearchService openSearchService;

    @Inject
    public ReminderResource(VoiceReminderService voiceReminderService) {
        this.voiceReminderService = voiceReminderService;
        this.openSearchService = voiceReminderService.getOpenSearchService();
    }

    @POST
    @Path("/reminder/record")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public void recordReminder(
            @Suspended AsyncResponse asyncResponse,
            @FormDataParam("userId") String userId,
            @FormDataParam("userEmail") String userEmail,
            @FormDataParam("audio") InputStream audioStream) {

        log.info("Processing voice reminder for user: {}, email: {}", userId, userEmail);

        if (userId == null || userId.isBlank() || userEmail == null || userEmail.isBlank()) {
            asyncResponse.resume(Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "userId and userEmail are required"))
                    .build());
            return;
        }

        File tempFile = null;
        try {
            String tempFileName = "audio_" + UUID.randomUUID() + ".wav";
            tempFile = new File(System.getProperty("java.io.tmpdir"), tempFileName);

            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = audioStream.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                }
            }

            log.info("Audio saved to temp file: {} ({} bytes)",
                    tempFile.getAbsolutePath(), tempFile.length());

            File finalTempFile = tempFile;
            File finalTempFile1 = tempFile;
            voiceReminderService.processVoiceReminder(userId, tempFile, userEmail)
                    .thenAccept(reminderId -> {
                        try {
                            Map<String, Object> response = Map.of(
                                    "reminderId", reminderId,
                                    "userId", userId,
                                    "userEmail", userEmail,
                                    "status", "processing",
                                    "message", "Reminder is being processed",
                                    "timestamp", LocalDateTime.now().toString()
                            );

                            asyncResponse.resume(Response.ok(response).build());
                        } finally {
                            cleanupTempFile(finalTempFile);
                        }
                    })
                    .exceptionally(ex -> {
                        log.error("Error processing voice reminder", ex);
                        cleanupTempFile(finalTempFile1);

                        Map<String, Object> error = Map.of(
                                "error", "Failed to process voice reminder",
                                "message", ex.getMessage(),
                                "timestamp", LocalDateTime.now().toString()
                        );

                        asyncResponse.resume(Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                                .entity(error)
                                .build());
                        return null;
                    });

        } catch (Exception e) {
            log.error("Error handling audio upload", e);
            cleanupTempFile(tempFile);

            Map<String, Object> error = Map.of(
                    "error", "Failed to upload audio",
                    "message", e.getMessage()
            );

            asyncResponse.resume(Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(error)
                    .build());
        }
    }

    @GET
    @Path("/reminder/{id}")
    public void getReminder(
            @Suspended AsyncResponse asyncResponse,
            @PathParam("id") String reminderId) {

        log.info("Getting reminder: {}", reminderId);

        openSearchService.getReminderById(reminderId)
                .thenApply(optionalReminder -> {
                    if (optionalReminder.isEmpty()) {
                        return Response.status(Response.Status.NOT_FOUND)
                                .entity(Map.of(
                                        "error", "Reminder not found",
                                        "reminderId", reminderId
                                ))
                                .build();
                    }

                    ReminderRecord reminder = optionalReminder.get();
                    Map<String, Object> response = new HashMap<>();
                    response.put("reminderId", reminder.reminderId());
                    response.put("userId", reminder.userId());
                    response.put("originalText", reminder.originalText());
                    response.put("extractedAction", reminder.extractedAction());
                    response.put("scheduledTime", reminder.scheduledTime().toString());
                    response.put("reminderTime", reminder.reminderTime());
                    response.put("status", reminder.status().toString());
                    response.put("createdAt", reminder.createdAt().toString());
                    response.put("notificationSent", reminder.notificationSent());
                    response.put("intent", reminder.intent());
                    response.put("eventBridgeRuleName", reminder.eventBridgeRuleName());

                    return Response.ok(response).build();
                })
                .exceptionally(ex -> {
                    log.error("Error getting reminder: {}", reminderId, ex);
                    return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                            .entity(Map.of(
                                    "error", "Failed to get reminder",
                                    "message", ex.getMessage()
                            ))
                            .build();
                })
                .thenAccept(asyncResponse::resume);
    }

    @GET
    @Path("/user/{userId}/reminders")
    public void getUserReminders(
            @Suspended AsyncResponse asyncResponse,
            @PathParam("userId") String userId,
            @QueryParam("limit") @DefaultValue("10") int limit,
            @QueryParam("status") String statusFilter) {

        log.info("Getting reminders for user: {}, limit: {}", userId, limit);

        openSearchService.findRemindersByUser(userId, limit)
                .thenApply(reminders -> {
                    List<ReminderRecord> filteredReminders = reminders;
                    if (statusFilter != null && !statusFilter.isEmpty()) {
                        try {
                            ReminderRecord.ReminderStatus filterStatus =
                                    ReminderRecord.ReminderStatus.valueOf(statusFilter.toUpperCase());
                            filteredReminders = reminders.stream()
                                    .filter(r -> r.status() == filterStatus)
                                    .collect(Collectors.toList());
                        } catch (IllegalArgumentException e) {
                            log.warn("Invalid status filter: {}", statusFilter);
                        }
                    }

                    List<Map<String, Object>> reminderList = filteredReminders.stream()
                            .map(reminder -> {
                                Map<String, Object> map = new HashMap<>();
                                map.put("reminderId", reminder.reminderId());
                                map.put("extractedAction", reminder.extractedAction());
                                map.put("scheduledTime", reminder.scheduledTime().toString());
                                map.put("reminderTime", reminder.reminderTime());
                                map.put("status", reminder.status().toString());
                                map.put("createdAt", reminder.createdAt().toString());
                                map.put("notificationSent", reminder.notificationSent());
                                return map;
                            })
                            .collect(Collectors.toList());

                    Map<String, Object> response = Map.of(
                            "userId", userId,
                            "total", reminderList.size(),
                            "filtered", filteredReminders.size(),
                            "reminders", reminderList,
                            "timestamp", LocalDateTime.now().toString()
                    );

                    return Response.ok(response).build();
                })
                .exceptionally(ex -> {
                    log.error("Error getting user reminders: {}", userId, ex);
                    return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                            .entity(Map.of(
                                    "error", "Failed to get user reminders",
                                    "message", ex.getMessage()
                            ))
                            .build();
                })
                .thenAccept(asyncResponse::resume);
    }

    @DELETE
    @Path("/reminder/{id}")
    public void deleteReminder(
            @Suspended AsyncResponse asyncResponse,
            @PathParam("id") String reminderId) {

        log.info("Deleting reminder: {}", reminderId);

        voiceReminderService.deleteReminder(reminderId)
                .thenApply(success -> {
                    Map<String, Object> response = Map.of(
                            "reminderId", reminderId,
                            "deleted", success,
                            "timestamp", LocalDateTime.now().toString()
                    );

                    if (success) {
                        return Response.ok(response).build();
                    } else {
                        return Response.status(Response.Status.NOT_FOUND)
                                .entity(response)
                                .build();
                    }
                })
                .exceptionally(ex -> {
                    log.error("Error deleting reminder: {}", reminderId, ex);
                    return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                            .entity(Map.of(
                                    "error", "Failed to delete reminder",
                                    "message", ex.getMessage()
                            ))
                            .build();
                })
                .thenAccept(asyncResponse::resume);
    }

    @GET
    @Path("/reminder/{id}/transcription")
    public void getReminderTranscription(
            @Suspended AsyncResponse asyncResponse,
            @PathParam("id") String reminderId) {

        log.info("Getting transcription for reminder: {}", reminderId);

        openSearchService.getReminderById(reminderId)
                .thenCompose(optionalReminder -> {
                    if (optionalReminder.isEmpty()) {
                        return CompletableFuture.completedFuture(
                                Response.status(Response.Status.NOT_FOUND)
                                        .entity(Map.of("error", "Reminder not found"))
                                        .build()
                        );
                    }

                    ReminderRecord reminder = optionalReminder.get();
                    String originalText = reminder.originalText();

                    return openSearchService.searchTranscriptions(originalText, reminder.userId(), 1)
                            .thenApply(transcriptions -> {
                                if (transcriptions.isEmpty()) {
                                    return Response.ok(Map.of(
                                            "reminderId", reminderId,
                                            "transcription", originalText,
                                            "source", "reminder_text",
                                            "message", "No separate transcription found, using reminder text"
                                    )).build();
                                }

                                var transcription = transcriptions.get(0);
                                return Response.ok(Map.of(
                                        "reminderId", reminderId,
                                        "transcription", transcription.transcribedText(),
                                        "confidence", transcription.confidence(),
                                        "language", transcription.language(),
                                        "duration", transcription.durationSeconds(),
                                        "source", "transcription_service",
                                        "completedAt", transcription.completedAt().toString()
                                )).build();
                            });
                })
                .exceptionally(ex -> {
                    log.error("Error getting transcription for reminder: {}", reminderId, ex);
                    return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                            .entity(Map.of(
                                    "error", "Failed to get transcription",
                                    "message", ex.getMessage()
                            ))
                            .build();
                })
                .thenAccept(asyncResponse::resume);
    }

    @GET
    @Path("/stats/{userId}")
    public void getUserStats(
            @Suspended AsyncResponse asyncResponse,
            @PathParam("userId") String userId) {

        log.info("Getting stats for user: {}", userId);

        openSearchService.getReminderStats(userId)
                .thenApply(stats -> {
                    Map<String, Object> response = new java.util.HashMap<>(stats);
                    response.put("userId", userId);
                    response.put("timestamp", LocalDateTime.now().toString());

                    return Response.ok(response).build();
                })
                .exceptionally(ex -> {
                    log.error("Error getting stats for user: {}", userId, ex);
                    return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                            .entity(Map.of(
                                    "error", "Failed to get user stats",
                                    "message", ex.getMessage()
                            ))
                            .build();
                })
                .thenAccept(asyncResponse::resume);
    }

    @GET
    @Path("/test")
    @Produces(MediaType.TEXT_PLAIN)
    public String test() {
        log.info("Test endpoint called");
        return "API is working!";
    }

    private void cleanupTempFile(File tempFile) {
        if (tempFile != null && tempFile.exists()) {
            try {
                boolean deleted = Files.deleteIfExists(tempFile.toPath());
                if (deleted) {
                    log.debug("Temp file deleted: {}", tempFile.getAbsolutePath());
                }
            } catch (Exception e) {
                log.warn("Failed to delete temp file: {}", tempFile.getAbsolutePath(), e);
            }
        }
    }
}