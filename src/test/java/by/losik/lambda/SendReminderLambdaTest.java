package by.losik.lambda;

import by.losik.service.VoiceReminderService;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.events.ScheduledEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SendReminderLambdaTest {

    @Mock
    private Context context;

    @Mock
    private LambdaLogger logger;

    @Mock
    private VoiceReminderService reminderService;

    private SendReminderLambda lambda;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        lambda = new SendReminderLambda();
        setPrivateField(lambda, "reminderService", reminderService);
        setPrivateField(lambda, "objectMapper", objectMapper);
        Mockito.when(context.getLogger()).thenReturn(logger);
    }

    private void setPrivateField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void handleRequest_ShouldProcessReminderSuccessfully() {
        ScheduledEvent event = createScheduledEvent("reminder-123", "user@example.com");

        CompletableFuture<Void> future = CompletableFuture.completedFuture(null);
        Mockito.when(reminderService.sendReminder("reminder-123", "user@example.com"))
                .thenReturn(future);

        String result = lambda.handleRequest(event, context);

        Assertions.assertEquals("Reminder processed: reminder-123", result);
        Mockito.verify(reminderService).sendReminder("reminder-123", "user@example.com");
        Mockito.verify(logger).log("Processing reminder: reminder-123 for email: user@example.com");
    }

    @Test
    void handleRequest_WhenDetailIsEmpty_ShouldReturnErrorMessage() {
        ScheduledEvent event = new ScheduledEvent();
        event.setDetail(null);

        String result = lambda.handleRequest(event, context);

        Assertions.assertEquals("No detail in event", result);
        Mockito.verify(reminderService, Mockito.never()).sendReminder(anyString(), anyString());
        Mockito.verify(logger).log("Event detail is empty");
    }

    @Test
    void handleRequest_WhenMissingReminderId_ShouldReturnErrorMessage() {
        ScheduledEvent event = createScheduledEvent("", "user@example.com");

        String result = lambda.handleRequest(event, context);

        Assertions.assertEquals("Missing required fields", result);
        Mockito.verify(reminderService, Mockito.never()).sendReminder(anyString(), anyString());
        Mockito.verify(logger).log("Missing required fields in event detail");
    }

    @Test
    void handleRequest_WhenMissingUserEmail_ShouldReturnErrorMessage() {
        ScheduledEvent event = createScheduledEvent("reminder-123", "");

        String result = lambda.handleRequest(event, context);

        Assertions.assertEquals("Missing required fields", result);
        Mockito.verify(reminderService, Mockito.never()).sendReminder(anyString(), anyString());
        Mockito.verify(logger).log("Missing required fields in event detail");
    }

    @Test
    void handleRequest_WhenSendReminderThrowsException_ShouldThrowRuntimeException() {
        ScheduledEvent event = createScheduledEvent("reminder-123", "user@example.com");

        RuntimeException expectedException = new RuntimeException("Service error");
        CompletableFuture<Void> future = CompletableFuture.completedFuture(null)
                .thenApply(v -> { throw expectedException; });

        Mockito.when(reminderService.sendReminder("reminder-123", "user@example.com"))
                .thenReturn(future);

        RuntimeException exception = Assertions.assertThrows(RuntimeException.class,
                () -> lambda.handleRequest(event, context));

        Assertions.assertEquals("Lambda execution failed", exception.getMessage());
    }

    @Test
    void handleRequest_WithAdditionalFieldsInDetail_ShouldProcessSuccessfully() {
        Map<String, Object> detail = Map.of(
                "reminderId", "reminder-456",
                "userEmail", "test@example.com",
                "additionalField", "extraValue",
                "priority", "high"
        );

        ScheduledEvent event = new ScheduledEvent();
        event.setDetail(detail);

        CompletableFuture<Void> future = CompletableFuture.completedFuture(null);
        Mockito.when(reminderService.sendReminder("reminder-456", "test@example.com"))
                .thenReturn(future);

        String result = lambda.handleRequest(event, context);

        Assertions.assertEquals("Reminder processed: reminder-456", result);
        Mockito.verify(reminderService).sendReminder("reminder-456", "test@example.com");
    }

    @Test
    void constructor_ShouldInitializeDependencies() {
        try (MockedStatic<Guice> guiceMock = Mockito.mockStatic(Guice.class)) {
            Injector mockInjector = Mockito.mock(Injector.class);
            VoiceReminderService mockService = Mockito.mock(VoiceReminderService.class);

            guiceMock.when(() -> Guice.createInjector((Module) any())).thenReturn(mockInjector);
            Mockito.when(mockInjector.getInstance(VoiceReminderService.class)).thenReturn(mockService);

            SendReminderLambda lambda = new SendReminderLambda();

            Assertions.assertNotNull(lambda);
        }
    }

    private ScheduledEvent createScheduledEvent(String reminderId, String userEmail) {
        Map<String, Object> detail = Map.of(
                "reminderId", reminderId,
                "userEmail", userEmail
        );

        ScheduledEvent event = new ScheduledEvent();
        event.setDetail(detail);
        return event;
    }
}