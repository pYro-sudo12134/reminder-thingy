package by.losik.lambda;

import by.losik.composition.root.AWSModule;
import by.losik.composition.root.MailModule;
import by.losik.service.VoiceReminderService;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.ScheduledEvent;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class SendReminderLambda implements RequestHandler<ScheduledEvent, String> {

    private final VoiceReminderService reminderService;
    private final ObjectMapper objectMapper;

    public SendReminderLambda() {
        Injector injector = Guice.createInjector(new AWSModule(), new MailModule());
        this.reminderService = injector.getInstance(VoiceReminderService.class);
        this.objectMapper = new ObjectMapper();
        this.objectMapper.findAndRegisterModules();
    }

    @Override
    public String handleRequest(ScheduledEvent event, Context context) {
        try {
            Map<String, Object> detailMap = event.getDetail();

            if (detailMap == null || detailMap.isEmpty()) {
                context.getLogger().log("Event detail is empty");
                return "No detail in event";
            }

            String detailJson = objectMapper.writeValueAsString(detailMap);
            JsonNode detailNode = objectMapper.readTree(detailJson);

            String reminderId = detailNode.path("reminderId").asText();
            String userEmail = detailNode.path("userEmail").asText();

            if (reminderId.isEmpty() || userEmail.isEmpty()) {
                context.getLogger().log("Missing required fields in event detail");
                return "Missing required fields";
            }

            context.getLogger().log("Processing reminder: " + reminderId + " for email: " + userEmail);

            CompletableFuture<Void> future = reminderService.sendReminder(reminderId, userEmail);

            future.get();

            return "Reminder processed: " + reminderId;

        } catch (Exception e) {
            context.getLogger().log("Error: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Lambda execution failed", e);
        }
    }
}