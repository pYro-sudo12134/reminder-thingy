package by.losik.service;

import by.losik.dto.CreateRuleRequest;
import by.losik.dto.ParsedResult;
import by.losik.dto.ReminderRecord;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Singleton
public class VoiceReminderService {
    private static final Logger log = LoggerFactory.getLogger(VoiceReminderService.class);
    private final S3Service s3Service;
    private final TranscribeService transcribeService;
    private final GRPCService reminderParser;
    private final EventBridgeService eventBridgeService;
    private final OpenSearchService openSearchService;
    private final EmailService emailService;

    @Inject
    public VoiceReminderService(
            S3Service s3Service,
            TranscribeService transcribeService,
            GRPCService reminderParser,
            EventBridgeService eventBridgeService,
            OpenSearchService openSearchService,
            EmailService emailService) {
        this.s3Service = s3Service;
        this.transcribeService = transcribeService;
        this.reminderParser = reminderParser;
        this.eventBridgeService = eventBridgeService;
        this.openSearchService = openSearchService;
        this.emailService = emailService;
    }

    public CompletableFuture<String> processVoiceReminder(
            String userId,
            java.io.File audioFile,
            String userEmail) {

        log.info("Processing voice reminder for user: {}", userId);

        return s3Service.uploadAudioFileAsync(audioFile, userId)
                .thenCompose(audioKey -> {
                    log.info("Audio uploaded to S3: {}", audioKey);
                    return transcribeService.transcribeAudioFileAsync(audioKey);
                })
                .thenCompose(transcribedText -> {
                    log.info("Transcribed text: {}", transcribedText);

                    ParsedResult parsed =
                            reminderParser.parse(transcribedText, null, userId);

                    String reminderId = parsed.reminderId() != null ?
                            parsed.reminderId() : UUID.randomUUID().toString();

                    ReminderRecord reminder = new ReminderRecord(
                            reminderId,
                            userId,
                            userEmail,
                            transcribedText,
                            parsed.action(),
                            parsed.scheduledTime(),
                            reminderParser.formatForDisplay(parsed.scheduledTime()),
                            LocalDateTime.now(),
                            ReminderRecord.ReminderStatus.SCHEDULED,
                            false,
                            parsed.intent(),
                            null
                    );

                    Map<String, Object> inputData = createEventInput(reminder, userEmail, parsed);

                    CreateRuleRequest ruleRequest = new CreateRuleRequest(
                            "reminder-" + reminderId,
                            parsed.scheduledTime(),
                            "arn:aws:lambda:us-east-1:000000000000:function:send-reminder",
                            inputData,
                            "Напоминание: " + parsed.action(),
                            parsed.intent()
                    );

                    return eventBridgeService.createScheduleRule(ruleRequest)
                            .thenCompose(rule -> {
                                ReminderRecord reminderWithRule = new ReminderRecord(
                                        reminderId,
                                        userId,
                                        userEmail,
                                        transcribedText,
                                        parsed.action(),
                                        parsed.scheduledTime(),
                                        reminderParser.formatForDisplay(parsed.scheduledTime()),
                                        LocalDateTime.now(),
                                        ReminderRecord.ReminderStatus.SCHEDULED,
                                        false,
                                        parsed.intent(),
                                        rule.ruleName()
                                );

                                return openSearchService.indexReminder(reminderWithRule)
                                        .thenApply(indexedId -> {
                                            log.info("Reminder saved to OpenSearch with rule: {}", rule.ruleName());
                                            return reminderId;
                                        });
                            });
                })
                .exceptionally(ex -> {
                    log.error("Failed to process voice reminder", ex);
                    throw new RuntimeException("Processing failed", ex);
                });
    }

    public CompletableFuture<Void> sendReminder(String reminderId, String userEmail) {
        log.info("Sending reminder: {} to {}", reminderId, userEmail);

        return openSearchService.getReminderById(reminderId)
                .thenCompose(optionalReminder -> {
                    if (optionalReminder.isEmpty()) {
                        log.error("Reminder not found: {}", reminderId);
                        return CompletableFuture.failedFuture(
                                new RuntimeException("Reminder not found"));
                    }

                    ReminderRecord reminder = optionalReminder.get();

                    return emailService.sendReminderNotification(
                            userEmail,
                            reminderId,
                            reminder.extractedAction(),
                            String.valueOf(reminder.scheduledTime())
                    ).thenCompose(emailId -> {
                        log.info("Email sent: {}", emailId);

                        return openSearchService.updateReminderStatus(
                                reminderId,
                                ReminderRecord.ReminderStatus.COMPLETED,
                                true
                        ).thenAccept(success -> {
                            if (success) {
                                log.info("Reminder {} marked as completed", reminderId);
                            }
                        });
                    });
                });
    }

    @Deprecated
    public CompletableFuture<java.util.List<ReminderRecord>> getUserReminders(
            String userId, int limit) {
        return openSearchService.findRemindersByUser(userId, limit);
    }

    public CompletableFuture<Boolean> deleteReminder(String reminderId) {
        return openSearchService.getReminderById(reminderId)
                .thenCompose(optionalReminder -> {
                    if (optionalReminder.isEmpty()) {
                        return CompletableFuture.completedFuture(false);
                    }

                    ReminderRecord reminder = optionalReminder.get();

                    if (reminder.eventBridgeRuleName() != null && !reminder.eventBridgeRuleName().isEmpty()) {
                        return eventBridgeService.deleteRule(reminder.eventBridgeRuleName())
                                .thenCompose(success -> {
                                    if (success) {
                                        log.info("EventBridge rule deleted: {}", reminder.eventBridgeRuleName());
                                    }
                                    return openSearchService.deleteReminder(reminderId);
                                });
                    } else {
                        return openSearchService.deleteReminder(reminderId);
                    }
                });
    }

    private Map<String, Object> createEventInput(
            ReminderRecord reminder,
            String userEmail,
            ParsedResult parsed) {

        return Map.of(
                "reminderId", reminder.reminderId(),
                "userEmail", userEmail,
                "action", reminder.extractedAction(),
                "scheduledTime", reminder.scheduledTime().toString(),
                "intent", parsed.intent() != null ? parsed.intent() : "reminder",
                "confidence", parsed.confidence(),
                "language", parsed.language()
        );
    }

    public CompletableFuture<Boolean> updateReminder(String reminderId,
                                                     String extractedAction,
                                                     LocalDateTime scheduledTime,
                                                     String reminderTime,
                                                     ReminderRecord.ReminderStatus status,
                                                     String userEmail) {  // Добавить параметр

        log.info("Updating reminder: {}", reminderId);

        if (scheduledTime == null) {
            log.error("scheduledTime is null for reminder: {}", reminderId);
            return CompletableFuture.completedFuture(false);
        }

        return openSearchService.getReminderById(reminderId)
                .thenCompose(optionalReminder -> {
                    if (optionalReminder.isEmpty()) {
                        return CompletableFuture.completedFuture(false);
                    }

                    ReminderRecord existing = optionalReminder.get();

                    String finalUserEmail = userEmail != null ? userEmail : existing.userEmail();

                    String oldRuleName = existing.eventBridgeRuleName();
                    CompletableFuture<Boolean> deleteFuture = oldRuleName != null && !oldRuleName.isEmpty()
                            ? eventBridgeService.deleteRule(oldRuleName)
                            .exceptionally(ex -> {
                                log.warn("Failed to delete old rule: {}", ex.getMessage());
                                return false;
                            })
                            : CompletableFuture.completedFuture(true);

                    return deleteFuture.thenCompose(deleted -> {
                        Map<String, Object> inputData = new HashMap<>();
                        inputData.put("reminderId", reminderId);
                        inputData.put("userEmail", finalUserEmail);
                        inputData.put("action", extractedAction != null ? extractedAction : existing.extractedAction());
                        inputData.put("scheduledTime", scheduledTime.toString());
                        inputData.put("intent", existing.intent() != null ? existing.intent() : "reminder");

                        CreateRuleRequest ruleRequest = new CreateRuleRequest(
                                "reminder-" + reminderId + "-" + System.currentTimeMillis(),
                                scheduledTime,
                                "arn:aws:lambda:us-east-1:000000000000:function:send-reminder",
                                inputData,
                                "Напоминание: " + (extractedAction != null ? extractedAction : existing.extractedAction()),
                                existing.intent()
                        );

                        return eventBridgeService.createScheduleRule(ruleRequest)
                                .thenCompose(rule -> {
                                    String finalReminderTime = reminderTime != null ?
                                            reminderTime : formatForDisplay(scheduledTime);

                                    ReminderRecord updated = new ReminderRecord(
                                            existing.reminderId(),
                                            existing.userId(),
                                            finalUserEmail,
                                            existing.originalText(),
                                            extractedAction != null ? extractedAction : existing.extractedAction(),
                                            scheduledTime,
                                            finalReminderTime,
                                            existing.createdAt(),
                                            status != null ? status : existing.status(),
                                            existing.notificationSent(),
                                            existing.intent(),
                                            rule.ruleName()
                                    );

                                    return openSearchService.updateReminder(updated)
                                            .thenApply(success -> {
                                                log.info("Reminder {} updated with new rule: {}",
                                                        reminderId, rule.ruleName());
                                                return success;
                                            });
                                });
                    });
                });
    }

    public CompletableFuture<Boolean> cancelReminder(String reminderId) {
        log.info("Cancelling reminder: {}", reminderId);

        return openSearchService.getReminderById(reminderId)
                .thenCompose(optionalReminder -> {
                    if (optionalReminder.isEmpty()) {
                        return CompletableFuture.completedFuture(false);
                    }

                    ReminderRecord reminder = optionalReminder.get();

                    CompletableFuture<Boolean> updateFuture = openSearchService.updateReminderStatus(
                            reminderId,
                            ReminderRecord.ReminderStatus.CANCELLED,
                            reminder.notificationSent()
                    );

                    if (reminder.eventBridgeRuleName() != null && !reminder.eventBridgeRuleName().isEmpty()) {
                        return eventBridgeService.deleteRule(reminder.eventBridgeRuleName())
                                .thenCompose(success -> {
                                    log.info("EventBridge rule deleted for cancelled reminder: {}",
                                            reminder.eventBridgeRuleName());
                                    return updateFuture;
                                });
                    }

                    return updateFuture;
                });
    }

    private String formatForDisplay(LocalDateTime dateTime) {
        if (dateTime == null) return "";
        return dateTime.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
    }

    public OpenSearchService getOpenSearchService() {
        return openSearchService;
    }
}