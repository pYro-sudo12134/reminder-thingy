package by.losik.service;

import by.losik.config.LocalStackConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.ses.SesAsyncClient;
import software.amazon.awssdk.services.ses.model.Content;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;
import software.amazon.awssdk.services.ses.model.SendEmailResponse;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private LocalStackConfig config;

    @Mock
    private SesAsyncClient sesAsyncClient;

    private EmailService emailService;

    private static final String FROM_EMAIL = "sender@example.com";
    private static final String TEST_EMAIL = "test@example.com";

    @BeforeEach
    void setUp() {
        when(config.getEmail()).thenReturn(FROM_EMAIL);
        when(config.getSesAsyncClient()).thenReturn(sesAsyncClient);

        emailService = new EmailService(config);
    }

    @Test
    void constructor_ShouldInitializeFromEmailCorrectly() {
        String customFromEmail = "custom@example.com";
        when(config.getEmail()).thenReturn(customFromEmail);
        when(config.getSesAsyncClient()).thenReturn(sesAsyncClient);

        EmailService customEmailService = new EmailService(config);

        verify(config, atLeast(1)).getEmail();
        verify(config, atLeast(1)).getSesAsyncClient();
        assertNotNull(customEmailService);
    }

    @Test
    void sendReminderEmail_ShouldCallSesClientWithCorrectParameters() {
        String toEmail = "recipient@example.com";
        String subject = "Test Subject";
        String body = "<h1>Test Body</h1>";

        SendEmailResponse mockResponse = SendEmailResponse.builder()
                .messageId("test-message-id")
                .build();

        when(sesAsyncClient.sendEmail(any(SendEmailRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(mockResponse));

        CompletableFuture<String> resultFuture = emailService.sendReminderEmail(toEmail, subject, body);
        String messageId = resultFuture.join();

        assertEquals("test-message-id", messageId);

        ArgumentCaptor<SendEmailRequest> requestCaptor = ArgumentCaptor.forClass(SendEmailRequest.class);
        verify(sesAsyncClient).sendEmail(requestCaptor.capture());

        SendEmailRequest capturedRequest = requestCaptor.getValue();
        assertEquals(FROM_EMAIL, capturedRequest.source());
        assertEquals(toEmail, capturedRequest.destination().toAddresses().get(0));
        assertEquals(subject, capturedRequest.message().subject().data());
        assertEquals(body, capturedRequest.message().body().html().data());
    }

    @Test
    void sendReminderEmail_WhenSesFails_ShouldThrowException() {
        String toEmail = "recipient@example.com";
        String subject = "Test Subject";
        String body = "Test Body";

        RuntimeException expectedException = new RuntimeException("SES error");
        when(sesAsyncClient.sendEmail(any(SendEmailRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(expectedException));

        CompletableFuture<String> resultFuture = emailService.sendReminderEmail(toEmail, subject, body);

        CompletionException completionException = assertThrows(CompletionException.class, resultFuture::join);

        Throwable actualException = completionException.getCause();
        assertNotNull(actualException);
        assertEquals(RuntimeException.class, actualException.getClass());
        assertEquals("Failed to send email", actualException.getMessage());
    }

    @Test
    void sendReminderEmail_WithEmptyBody_ShouldStillSend() {
        String subject = "Empty Test";
        String body = "";

        SendEmailResponse mockResponse = SendEmailResponse.builder()
                .messageId("empty-body-id")
                .build();

        when(sesAsyncClient.sendEmail(any(SendEmailRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(mockResponse));

        CompletableFuture<String> resultFuture = emailService.sendReminderEmail(TEST_EMAIL, subject, body);
        String messageId = resultFuture.join();

        assertEquals("empty-body-id", messageId);

        ArgumentCaptor<SendEmailRequest> requestCaptor = ArgumentCaptor.forClass(SendEmailRequest.class);
        verify(sesAsyncClient).sendEmail(requestCaptor.capture());

        SendEmailRequest capturedRequest = requestCaptor.getValue();
        assertEquals(body, capturedRequest.message().body().html().data());
    }

    @Test
    void sendReminderEmail_WithNullBody_ShouldHandleGracefully() {
        String subject = "Null Test";
        String body = null;

        SendEmailResponse mockResponse = SendEmailResponse.builder()
                .messageId("null-body-id")
                .build();

        when(sesAsyncClient.sendEmail(any(SendEmailRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(mockResponse));

        CompletableFuture<String> resultFuture = emailService.sendReminderEmail(TEST_EMAIL, subject, body);
        String messageId = resultFuture.join();

        assertEquals("null-body-id", messageId);

        ArgumentCaptor<SendEmailRequest> requestCaptor = ArgumentCaptor.forClass(SendEmailRequest.class);
        verify(sesAsyncClient).sendEmail(requestCaptor.capture());

        SendEmailRequest capturedRequest = requestCaptor.getValue();
        Content htmlContent = capturedRequest.message().body().html();
        if (htmlContent != null) {
            assertNull(htmlContent.data());
        }
    }

    @Test
    void sendReminderNotification_ShouldFormatHtmlCorrectly() {
        String reminderId = "rem-12345";
        String action = "Позвонить врачу";
        String scheduledTime = "15:30";

        SendEmailResponse mockResponse = SendEmailResponse.builder()
                .messageId("notification-id")
                .build();

        when(sesAsyncClient.sendEmail(any(SendEmailRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(mockResponse));

        CompletableFuture<String> resultFuture = emailService.sendReminderNotification(
                TEST_EMAIL, reminderId, action, scheduledTime);
        String messageId = resultFuture.join();

        assertEquals("notification-id", messageId);

        ArgumentCaptor<SendEmailRequest> requestCaptor = ArgumentCaptor.forClass(SendEmailRequest.class);
        verify(sesAsyncClient).sendEmail(requestCaptor.capture());

        SendEmailRequest capturedRequest = requestCaptor.getValue();
        String subject = capturedRequest.message().subject().data();
        String htmlBody = capturedRequest.message().body().html().data();

        assertEquals("Напоминание: " + action, subject);
        assertTrue(htmlBody.contains(action));
        assertTrue(htmlBody.contains(scheduledTime));
        assertTrue(htmlBody.contains(reminderId));
        assertTrue(htmlBody.contains("Голосовое напоминание"));
        assertTrue(htmlBody.contains("<!DOCTYPE html>"));
        assertTrue(htmlBody.contains("<html>"));
        assertTrue(htmlBody.contains("</html>"));
    }

    @Test
    void sendReminderNotification_ShouldIncludeAllDetailsInHtml() {
        String reminderId = "test-reminder-001";
        String action = "Купить продукты";
        String scheduledTime = "18:45";

        SendEmailResponse mockResponse = SendEmailResponse.builder()
                .messageId("test-id")
                .build();

        when(sesAsyncClient.sendEmail(any(SendEmailRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(mockResponse));

        CompletableFuture<String> resultFuture = emailService.sendReminderNotification(
                TEST_EMAIL, reminderId, action, scheduledTime);
        resultFuture.join();

        ArgumentCaptor<SendEmailRequest> requestCaptor = ArgumentCaptor.forClass(SendEmailRequest.class);
        verify(sesAsyncClient).sendEmail(requestCaptor.capture());

        SendEmailRequest capturedRequest = requestCaptor.getValue();
        String html = capturedRequest.message().body().html().data();

        assertTrue(html.contains(action));
        assertTrue(html.contains(scheduledTime));
        assertTrue(html.contains(reminderId));
        assertTrue(html.contains("Голосовое напоминание"));
        assertTrue(html.contains("<style>"));
        assertTrue(html.contains("</style>"));
        assertTrue(html.contains("<div class=\"container\">"));
    }

    @Test
    void sendReminderNotification_WithSpecialCharacters_ShouldHandleCorrectly() {
        String reminderId = "rem-id-<test>&\"'";
        String action = "Test <script>alert('xss')</script> Action";
        String scheduledTime = "12:00 & 13:00";

        SendEmailResponse mockResponse = SendEmailResponse.builder()
                .messageId("safe-id")
                .build();

        when(sesAsyncClient.sendEmail(any(SendEmailRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(mockResponse));

        CompletableFuture<String> resultFuture = emailService.sendReminderNotification(
                TEST_EMAIL, reminderId, action, scheduledTime);
        resultFuture.join();

        ArgumentCaptor<SendEmailRequest> requestCaptor = ArgumentCaptor.forClass(SendEmailRequest.class);
        verify(sesAsyncClient).sendEmail(requestCaptor.capture());

        SendEmailRequest capturedRequest = requestCaptor.getValue();
        String html = capturedRequest.message().body().html().data();

        assertTrue(html.contains(action));
        assertTrue(html.contains(scheduledTime));
        assertTrue(html.contains(reminderId));
    }

    @Test
    void sendReminderNotification_WithEmptyValues_ShouldHandleGracefully() {
        String reminderId = "";
        String action = "";
        String scheduledTime = "";

        SendEmailResponse mockResponse = SendEmailResponse.builder()
                .messageId("empty-values-id")
                .build();

        when(sesAsyncClient.sendEmail(any(SendEmailRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(mockResponse));

        CompletableFuture<String> resultFuture = emailService.sendReminderNotification(
                TEST_EMAIL, reminderId, action, scheduledTime);
        assertDoesNotThrow(resultFuture::join);

        ArgumentCaptor<SendEmailRequest> requestCaptor = ArgumentCaptor.forClass(SendEmailRequest.class);
        verify(sesAsyncClient).sendEmail(requestCaptor.capture());

        SendEmailRequest capturedRequest = requestCaptor.getValue();
        assertEquals("Напоминание: ", capturedRequest.message().subject().data());
    }

    @Test
    void sendReminderNotification_WithNullValues_ShouldHandleGracefully() {
        String reminderId = null;
        String action = null;
        String scheduledTime = null;

        SendEmailResponse mockResponse = SendEmailResponse.builder()
                .messageId("null-values-id")
                .build();

        when(sesAsyncClient.sendEmail(any(SendEmailRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(mockResponse));

        CompletableFuture<String> resultFuture = emailService.sendReminderNotification(
                TEST_EMAIL, reminderId, action, scheduledTime);
        assertDoesNotThrow(resultFuture::join);

        ArgumentCaptor<SendEmailRequest> requestCaptor = ArgumentCaptor.forClass(SendEmailRequest.class);
        verify(sesAsyncClient).sendEmail(requestCaptor.capture());

        SendEmailRequest capturedRequest = requestCaptor.getValue();
        assertEquals("Напоминание: null", capturedRequest.message().subject().data());
    }

    @Test
    void sendReminderEmail_MultipleCalls_ShouldWorkIndependently() {
        SendEmailResponse mockResponse1 = SendEmailResponse.builder()
                .messageId("id-1")
                .build();
        SendEmailResponse mockResponse2 = SendEmailResponse.builder()
                .messageId("id-2")
                .build();

        when(sesAsyncClient.sendEmail(any(SendEmailRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(mockResponse1))
                .thenReturn(CompletableFuture.completedFuture(mockResponse2));

        CompletableFuture<String> result1 = emailService.sendReminderEmail(
                "email1@example.com", "Subject 1", "Body 1");
        CompletableFuture<String> result2 = emailService.sendReminderEmail(
                "email2@example.com", "Subject 2", "Body 2");

        assertEquals("id-1", result1.join());
        assertEquals("id-2", result2.join());
        verify(sesAsyncClient, times(2)).sendEmail(any(SendEmailRequest.class));
    }

    @Test
    void sendReminderNotification_MultipleCalls_ShouldWorkIndependently() {
        SendEmailResponse mockResponse1 = SendEmailResponse.builder()
                .messageId("id-1")
                .build();
        SendEmailResponse mockResponse2 = SendEmailResponse.builder()
                .messageId("id-2")
                .build();

        when(sesAsyncClient.sendEmail(any(SendEmailRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(mockResponse1))
                .thenReturn(CompletableFuture.completedFuture(mockResponse2));

        CompletableFuture<String> result1 = emailService.sendReminderNotification(
                "email1@example.com", "id1", "Action 1", "10:00");
        CompletableFuture<String> result2 = emailService.sendReminderNotification(
                "email2@example.com", "id2", "Action 2", "11:00");

        assertEquals("id-1", result1.join());
        assertEquals("id-2", result2.join());
        verify(sesAsyncClient, times(2)).sendEmail(any(SendEmailRequest.class));
    }

    @Test
    void sendReminderEmail_ShouldSetCorrectDestination() {
        String toEmail = "single@example.com";
        String subject = "Test";
        String body = "Test";

        SendEmailResponse mockResponse = SendEmailResponse.builder()
                .messageId("test-id")
                .build();

        when(sesAsyncClient.sendEmail(any(SendEmailRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(mockResponse));

        CompletableFuture<String> resultFuture = emailService.sendReminderEmail(toEmail, subject, body);
        resultFuture.join();

        ArgumentCaptor<SendEmailRequest> requestCaptor = ArgumentCaptor.forClass(SendEmailRequest.class);
        verify(sesAsyncClient).sendEmail(requestCaptor.capture());

        SendEmailRequest capturedRequest = requestCaptor.getValue();
        assertEquals(1, capturedRequest.destination().toAddresses().size());
        assertEquals(toEmail, capturedRequest.destination().toAddresses().get(0));

        assertTrue(capturedRequest.destination().ccAddresses() == null ||
                capturedRequest.destination().ccAddresses().isEmpty());
        assertTrue(capturedRequest.destination().bccAddresses() == null ||
                capturedRequest.destination().bccAddresses().isEmpty());
    }

    @Test
    void sendReminderEmail_ShouldCreateProperMessageStructure() {
        String toEmail = "test@example.com";
        String subject = "Subject";
        String body = "Body";

        SendEmailResponse mockResponse = SendEmailResponse.builder()
                .messageId("test-id")
                .build();

        when(sesAsyncClient.sendEmail(any(SendEmailRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(mockResponse));

        CompletableFuture<String> resultFuture = emailService.sendReminderEmail(toEmail, subject, body);
        resultFuture.join();

        ArgumentCaptor<SendEmailRequest> requestCaptor = ArgumentCaptor.forClass(SendEmailRequest.class);
        verify(sesAsyncClient).sendEmail(requestCaptor.capture());

        SendEmailRequest capturedRequest = requestCaptor.getValue();
        assertNotNull(capturedRequest.message());
        assertNotNull(capturedRequest.message().subject());
        assertNotNull(capturedRequest.message().body());
        assertNotNull(capturedRequest.message().body().html());
    }
}