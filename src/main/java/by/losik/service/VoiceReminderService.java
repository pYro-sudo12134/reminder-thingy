package by.losik.service;

import by.losik.config.EventBridgeConfig;
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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import by.losik.config.TelegramConfig;
import by.losik.service.TelegramBotService;

/**
 * Сервис для обработки голосовых напоминаний.
 * <p>
 * Оркестрирует полный цикл обработки голосового напоминания:
 * <ol>
 *     <li>Загрузка аудио в S3</li>
 *     <li>Транскрибация через AWS Transcribe</li>
 *     <li>Семантический анализ через NLP сервис (gRPC)</li>
 *     <li>Создание правила в EventBridge для планирования</li>
 *     <li>Сохранение напоминания в OpenSearch</li>
 * </ol>
 * <p>
 * Также предоставляет методы для:
 * <ul>
 *     <li>Отправки напоминаний (email уведомление)</li>
 *     <li>Обновления напоминаний</li>
 *     <li>Удаления напоминаний</li>
 *     <li>Отмены напоминаний</li>
 * </ul>
 *
 * @see S3Service
 * @see TranscribeService
 * @see GRPCService
 * @see EventBridgeService
 * @see OpenSearchService
 */
@Singleton
public class VoiceReminderService {
    private static final Logger log = LoggerFactory.getLogger(VoiceReminderService.class);
    private final S3Service s3Service;
    private final TranscribeService transcribeService;
    private final GRPCService reminderParser;
    private final EventBridgeService eventBridgeService;
    private final OpenSearchService openSearchService;
    private final EmailService emailService;
    private final EventBridgeConfig eventBridgeConfig;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    private final Map<String, java.util.concurrent.ScheduledFuture<?>> scheduledTasks = new java.util.concurrent.ConcurrentHashMap<>();
    private final TelegramBotService telegramBotService;

    /**
     * Создаёт сервис голосовых напоминаний с внедрёнными зависимостями.
     *
     * @param s3Service сервис для работы с S3
     * @param transcribeService сервис для транскрибации аудио
     * @param reminderParser сервис для парсинга текста (NLP)
     * @param eventBridgeService сервис для работы с EventBridge
     * @param openSearchService сервис для работы с OpenSearch
     * @param emailService сервис для отправки email
     * @param eventBridgeConfig конфигурация EventBridge (включая ARN Lambda)
     */
    @Inject
    public VoiceReminderService(
            S3Service s3Service,
            TranscribeService transcribeService,
            GRPCService reminderParser,
            EventBridgeService eventBridgeService,
            OpenSearchService openSearchService,
            EmailService emailService,
            EventBridgeConfig eventBridgeConfig,
            TelegramBotService telegramBotService) {
        this.s3Service = s3Service;
        this.transcribeService = transcribeService;
        this.reminderParser = reminderParser;
        this.eventBridgeService = eventBridgeService;
        this.openSearchService = openSearchService;
        this.emailService = emailService;
        this.eventBridgeConfig = eventBridgeConfig;
        this.telegramBotService = telegramBotService;
    }

    /**
     * Обрабатывает голосовое напоминание.
     * <p>
     * Этапы обработки:
     * <ol>
     *     <li>Загрузка аудио в S3</li>
     *     <li>Транскрибация аудио в текст</li>
     *     <li>Семантический анализ текста (извлечение действия и времени)</li>
     *     <li>Создание правила EventBridge</li>
     *     <li>Сохранение напоминания в OpenSearch</li>
     * </ol>
     *
     * @param userId ID пользователя
     * @param audioFile аудиофайл с напоминанием
     * @param userEmail email пользователя для уведомлений
     * @return ID созданного напоминания
     */
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

                    log.info("Parsed result: reminderId={}, action={}, scheduledTime={}, intent={}, ruleName={}",
                            parsed.reminderId(),
                            parsed.action(),
                            parsed.scheduledTime(),
                            parsed.intent(),
                            parsed.ruleName());

                    String reminderId = parsed.reminderId() != null ?
                            parsed.reminderId() : UUID.randomUUID().toString();

                    ReminderRecord reminder = new ReminderRecord(
                            reminderId,
                            userId,
                            userEmail,
                            transcribedText,
                            parsed.action(),
                            parsed.scheduledTime(),
                            LocalDateTime.now(),
                            ReminderRecord.ReminderStatus.SCHEDULED,
                            false,
                            parsed.intent(),
                            null
                    );

                    Map<String, Object> inputData = createEventInput(reminder, userEmail, parsed);

                    log.info("Scheduling reminder: reminderId={}, scheduledTime={}, delayMillis={}",
                            reminderId,
                            parsed.scheduledTime(),
                            java.time.Duration.between(LocalDateTime.now(), parsed.scheduledTime()).toMillis());

                    ReminderRecord reminderWithScheduler = new ReminderRecord(
                            reminderId,
                            userId,
                            userEmail,
                            transcribedText,
                            parsed.action(),
                            parsed.scheduledTime(),
                            LocalDateTime.now(),
                            ReminderRecord.ReminderStatus.SCHEDULED,
                            false,
                            parsed.intent(),
                            "scheduled-" + reminderId
                    );

                    return openSearchService.indexReminder(reminder)
                            .thenCompose(indexedId -> {
                                log.info("Reminder saved to OpenSearch: {}", reminderId);

                                CreateRuleRequest ruleRequest = new CreateRuleRequest(
                                        "reminder-" + reminderId,
                                        parsed.scheduledTime(),
                                        eventBridgeConfig.getDefaultLambdaArn(),
                                        inputData,
                                        "Напоминание: " + parsed.action(),
                                        parsed.intent()
                                );

                                return eventBridgeService.createEmailRule(ruleRequest)
                                        .thenCompose(rule -> {
                                            log.info("EventBridge rule created: {}", rule.ruleName());

                                            ReminderRecord reminderWithRule = new ReminderRecord(
                                                    reminderId,
                                                    userId,
                                                    userEmail,
                                                    transcribedText,
                                                    parsed.action(),
                                                    parsed.scheduledTime(),
                                                    LocalDateTime.now(),
                                                    ReminderRecord.ReminderStatus.SCHEDULED,
                                                    false,
                                                    parsed.intent(),
                                                    rule.ruleName()
                                            );

                                            return openSearchService.updateReminder(reminderWithRule)
                                                    .thenApply(success -> reminderId);
                                        });
                            });
                })
                .exceptionally(ex -> {
                    log.error("Failed to process voice reminder", ex);
                    log.error("Exception stack trace:", ex);
                    throw new RuntimeException("Processing failed", ex);
                });
    }

    /**
     * Отправляет напоминание пользователю.
     * <p>
     * Этапы отправки:
     * <ol>
     *     <li>Получение напоминания из OpenSearch</li>
     *     <li>Отправка email уведомления</li>
     *     <li>Обновление статуса на COMPLETED</li>
     * </ol>
     *
     * @param reminderId ID напоминания
     * @param userEmail email пользователя
     * @return CompletableFuture для асинхронного ожидания
     */
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

                    CompletableFuture<String> emailFuture = emailService.sendReminderNotification(
                            userEmail,
                            reminderId,
                            reminder.extractedAction(),
                            String.valueOf(reminder.scheduledTime())
                    );

                    CompletableFuture<Boolean> telegramFuture = telegramBotService.getChatIdForUser(reminder.userId())
                            .map(chatId -> {
                                log.info("Sending Telegram notification to chat {} for reminder {}", chatId, reminderId);
                                return telegramBotService.sendReminderNotification(
                                        chatId,
                                        reminderId,
                                        reminder.extractedAction(),
                                        String.valueOf(reminder.scheduledTime())
                                );
                            })
                            .orElseGet(() -> {
                                log.debug("User {} not connected to Telegram, skipping", reminder.userId());
                                return CompletableFuture.completedFuture(false);
                            });

                    return CompletableFuture.allOf(emailFuture, telegramFuture)
                            .thenCompose(v -> {
                                return emailFuture.thenCompose(emailId -> {
                                    log.info("Email sent: {}", emailId);
                                    return telegramFuture.thenAccept(telegramSent -> {
                                        if (telegramSent) {
                                            log.info("Telegram notification sent for reminder {}", reminderId);
                                        }
                                    });
                                });
                            })
                            .thenCompose(v -> openSearchService.updateReminderStatus(
                                    reminderId,
                                    ReminderRecord.ReminderStatus.COMPLETED,
                                    true
                            ))
                            .thenAccept(success -> {
                                if (success) {
                                    log.info("Reminder {} marked as completed", reminderId);
                                }
                            });
                });
    }

    /**
     * Удаляет напоминание.
     * <p>
     * Этапы удаления:
     * <ol>
     *     <li>Получение напоминания из OpenSearch</li>
     *     <li>Удаление правила EventBridge (если есть)</li>
     *     <li>Удаление напоминания из OpenSearch</li>
     * </ol>
     *
     * @param reminderId ID напоминания
     * @return true если удалено успешно
     */
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

    /**
     * Обновляет напоминание.
     * <p>
     * Этапы обновления:
     * <ol>
     *     <li>Получение текущего напоминания из OpenSearch</li>
     *     <li>Удаление старого правила EventBridge</li>
     *     <li>Создание нового правила EventBridge</li>
     *     <li>Обновление напоминания в OpenSearch</li>
     * </ol>
     *
     * @param reminderId ID напоминания
     * @param extractedAction новое действие
     * @param scheduledTime новое время выполнения
     * @param status новый статус
     * @param userEmail новый email пользователя
     * @return true если обновлено успешно
     */
    public CompletableFuture<Boolean> updateReminder(String reminderId,
                                                     String extractedAction,
                                                     LocalDateTime scheduledTime,
                                                     ReminderRecord.ReminderStatus status,
                                                     String userEmail) {

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
                                eventBridgeConfig.getDefaultLambdaArn(),
                                inputData,
                                "Напоминание: " + (extractedAction != null ? extractedAction : existing.extractedAction()),
                                existing.intent()
                        );

                        return eventBridgeService.createEmailRule(ruleRequest)
                                .thenCompose(rule -> {
                                    ReminderRecord updated = new ReminderRecord(
                                            existing.reminderId(),
                                            existing.userId(),
                                            finalUserEmail,
                                            existing.originalText(),
                                            extractedAction != null ? extractedAction : existing.extractedAction(),
                                            scheduledTime,
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

    /**
     * Отменяет напоминание.
     * <p>
     * Этапы отмены:
     * <ol>
     *     <li>Получение напоминания из OpenSearch</li>
     *     <li>Обновление статуса на CANCELLED</li>
     *     <li>Удаление правила EventBridge (если есть)</li>
     * </ol>
     *
     * @param reminderId ID напоминания
     * @return true если отменено успешно
     */
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

                    return updateFuture;
                });
    }
}