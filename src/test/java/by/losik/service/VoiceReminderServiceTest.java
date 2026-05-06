package by.losik.service;

import by.losik.config.EventBridgeConfig;
import by.losik.dto.CreateRuleRequest;
import by.losik.dto.EventBridgeRuleRecord;
import by.losik.dto.ParsedResult;
import by.losik.dto.ReminderRecord;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

@ExtendWith(MockitoExtension.class)
class VoiceReminderServiceTest {

    @Mock
    private S3Service s3Service;

    @Mock
    private TranscribeService transcribeService;

    @Mock
    private GRPCService reminderParser;

    @Mock
    private EventBridgeService eventBridgeService;

    @Mock
    private OpenSearchService openSearchService;

    @Mock
    private EmailService emailService;

    @Mock
    private EventBridgeConfig eventBridgeConfig;

    @Mock
    private TelegramBotService telegramBotService;

    @Mock
    private File audioFile;

    private VoiceReminderService voiceReminderService;

    @BeforeEach
    void setUp() {
        voiceReminderService = new VoiceReminderService(
                s3Service,
                transcribeService,
                reminderParser,
                eventBridgeService,
                openSearchService,
                emailService,
                eventBridgeConfig,
                telegramBotService
        );
    }

    @Test
    void processVoiceReminder_Success() {
        String userId = "user123";
        String userEmail = "user@example.com";
        String audioKey = "audio/user123/123456/file.wav";
        String transcribedText = "купить молоко завтра в 9 утра";
        String reminderId = "reminder-123";

        ParsedResult parsedResult = new ParsedResult(
                ZonedDateTime.now(ZoneOffset.UTC).plusDays(1).withHour(9).withMinute(0),
                "купить молоко",
                0.95,
                "ru",
                reminderId,
                "reminder",
                null,
                transcribedText,
                transcribedText
        );

        Mockito.when(s3Service.uploadAudioFileAsync(any(File.class), anyString()))
                .thenReturn(CompletableFuture.completedFuture(audioKey));
        Mockito.when(transcribeService.transcribeAudioFileAsync(anyString()))
                .thenReturn(CompletableFuture.completedFuture(transcribedText));
        Mockito.when(reminderParser.parse(anyString(), any(), anyString()))
                .thenReturn(parsedResult);
        Mockito.when(eventBridgeConfig.getDefaultLambdaArn())
                .thenReturn("arn:aws:lambda:us-east-1:000000000000:function:send-reminder");
        Mockito.when(eventBridgeService.createEmailRule(any(CreateRuleRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(
                        new EventBridgeRuleRecord("reminder-" + reminderId, null, null, true, null, null)
                ));
        Mockito.when(openSearchService.indexReminder(any()))
                .thenReturn(CompletableFuture.completedFuture(reminderId));

        CompletableFuture<String> result = voiceReminderService.processVoiceReminder(
                userId, audioFile, userEmail);

        Assertions.assertNotNull(result.join());
        Assertions.assertEquals(reminderId, result.join());
        Mockito.verify(s3Service).uploadAudioFileAsync(any(File.class), anyString());
        Mockito.verify(transcribeService).transcribeAudioFileAsync(audioKey);
        Mockito.verify(reminderParser).parse(transcribedText, null, userId);
        Mockito.verify(eventBridgeService).createEmailRule(any());
        Mockito.verify(openSearchService).indexReminder(any());
    }

    @Test
    void processVoiceReminder_WhenTranscribeFails_ShouldThrowException() {
        String userId = "user123";
        String userEmail = "user@example.com";
        String audioKey = "audio/user123/123456/file.wav";

        Mockito.when(s3Service.uploadAudioFileAsync(any(File.class), anyString()))
                .thenReturn(CompletableFuture.completedFuture(audioKey));
        Mockito.when(transcribeService.transcribeAudioFileAsync(anyString()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Transcribe failed")));

        CompletableFuture<String> result = voiceReminderService.processVoiceReminder(
                userId, audioFile, userEmail);

        Assertions.assertThrows(RuntimeException.class, result::join);
        Mockito.verify(s3Service).uploadAudioFileAsync(any(File.class), anyString());
        Mockito.verify(transcribeService).transcribeAudioFileAsync(audioKey);
        Mockito.verifyNoMoreInteractions(reminderParser, eventBridgeService, openSearchService);
    }

    @Test
    void sendReminder_WhenSuccess_ShouldSendEmailAndUpdateStatus() {
        String reminderId = "reminder-123";
        String userEmail = "user@example.com";
        String action = "купить молоко";
        ZonedDateTime scheduledTime = ZonedDateTime.now(ZoneOffset.UTC).plusDays(1);

        ReminderRecord reminder = new ReminderRecord(
                reminderId,
                "user123",
                userEmail,
                "купить молоко завтра в 9 утра",
                action,
                scheduledTime,
                ZonedDateTime.now(ZoneOffset.UTC),
                ReminderRecord.ReminderStatus.SCHEDULED,
                false,
                "reminder",
                "reminder-123"
        );

        Mockito.when(openSearchService.getReminderById(reminderId))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(reminder)));
        Mockito.when(emailService.sendReminderNotification(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture("email-123"));
        Mockito.when(openSearchService.updateReminderStatus(anyString(), any(), Mockito.anyBoolean()))
                .thenReturn(CompletableFuture.completedFuture(true));

        CompletableFuture<Void> result = voiceReminderService.sendReminder(reminderId, userEmail);

        Assertions.assertDoesNotThrow(result::join);
        Mockito.verify(openSearchService).getReminderById(reminderId);
        Mockito.verify(emailService).sendReminderNotification(userEmail, reminderId, action, scheduledTime.toString());
        Mockito.verify(openSearchService).updateReminderStatus(reminderId, ReminderRecord.ReminderStatus.COMPLETED, true);
    }

    @Test
    void sendReminder_WhenNotFound_ShouldReturnError() {
        String reminderId = "nonexistent";
        String userEmail = "user@example.com";

        Mockito.when(openSearchService.getReminderById(reminderId))
                .thenReturn(CompletableFuture.completedFuture(Optional.empty()));

        CompletableFuture<Void> result = voiceReminderService.sendReminder(reminderId, userEmail);

        Assertions.assertThrows(RuntimeException.class, result::join);
        Mockito.verify(openSearchService).getReminderById(reminderId);
        Mockito.verifyNoMoreInteractions(emailService, openSearchService);
    }

    @Test
    void deleteReminder_WhenSuccess_ShouldDelete() {
        String reminderId = "reminder-123";
        String eventBridgeRuleName = "reminder-reminder-123";

        ReminderRecord reminder = new ReminderRecord(
                reminderId,
                "user123",
                "user@example.com",
                "купить молоко",
                "купить молоко",
                ZonedDateTime.now(ZoneOffset.UTC),
                ZonedDateTime.now(ZoneOffset.UTC),
                ReminderRecord.ReminderStatus.SCHEDULED,
                false,
                "reminder",
                eventBridgeRuleName
        );

        Mockito.when(openSearchService.getReminderById(reminderId))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(reminder)));
        Mockito.when(eventBridgeService.deleteRule(eventBridgeRuleName))
                .thenReturn(CompletableFuture.completedFuture(true));
        Mockito.when(openSearchService.deleteReminder(reminderId))
                .thenReturn(CompletableFuture.completedFuture(true));

        CompletableFuture<Boolean> result = voiceReminderService.deleteReminder(reminderId);

        Assertions.assertTrue(result.join());
        Mockito.verify(openSearchService).getReminderById(reminderId);
        Mockito.verify(eventBridgeService).deleteRule(eventBridgeRuleName);
        Mockito.verify(openSearchService).deleteReminder(reminderId);
    }

    @Test
    void deleteReminder_WhenNoEventBridgeRule_ShouldDeleteOnlyFromOpenSearch() {
        String reminderId = "reminder-123";

        ReminderRecord reminder = new ReminderRecord(
                reminderId,
                "user123",
                "user@example.com",
                "купить молоко",
                "купить молоко",
                ZonedDateTime.now(ZoneOffset.UTC),
                ZonedDateTime.now(ZoneOffset.UTC),
                ReminderRecord.ReminderStatus.SCHEDULED,
                false,
                "reminder",
                null
        );

        Mockito.when(openSearchService.getReminderById(reminderId))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(reminder)));
        Mockito.when(openSearchService.deleteReminder(reminderId))
                .thenReturn(CompletableFuture.completedFuture(true));

        CompletableFuture<Boolean> result = voiceReminderService.deleteReminder(reminderId);

        Assertions.assertTrue(result.join());
        Mockito.verify(openSearchService).getReminderById(reminderId);
        Mockito.verifyNoMoreInteractions(eventBridgeService);
        Mockito.verify(openSearchService).deleteReminder(reminderId);
    }

    @Test
    void cancelReminder_WhenSuccess_ShouldCancelAndDeleteRule() {
        String reminderId = "reminder-123";
        String eventBridgeRuleName = "reminder-reminder-123";

        ReminderRecord reminder = new ReminderRecord(
                reminderId,
                "user123",
                "user@example.com",
                "купить молоко",
                "купить молоко",
                ZonedDateTime.now(ZoneOffset.UTC),
                ZonedDateTime.now(ZoneOffset.UTC),
                ReminderRecord.ReminderStatus.SCHEDULED,
                false,
                "reminder",
                eventBridgeRuleName
        );

        Mockito.when(openSearchService.getReminderById(reminderId))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(reminder)));
        Mockito.when(eventBridgeService.deleteRule(eventBridgeRuleName))
                .thenReturn(CompletableFuture.completedFuture(true));
        Mockito.when(openSearchService.updateReminderStatus(anyString(), any(), Mockito.anyBoolean()))
                .thenReturn(CompletableFuture.completedFuture(true));

        CompletableFuture<Boolean> result = voiceReminderService.cancelReminder(reminderId);

        Assertions.assertTrue(result.join());
        Mockito.verify(openSearchService).getReminderById(reminderId);
        Mockito.verify(eventBridgeService).deleteRule(eventBridgeRuleName);
        Mockito.verify(openSearchService).updateReminderStatus(reminderId, ReminderRecord.ReminderStatus.CANCELLED, false);
    }
}
