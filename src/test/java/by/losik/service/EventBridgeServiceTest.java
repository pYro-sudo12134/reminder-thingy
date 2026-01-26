package by.losik.service;

import by.losik.config.LocalStackConfig;
import by.losik.dto.CreateRuleRequest;
import by.losik.dto.EventBridgeRuleRecord;
import by.losik.dto.SendEventRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.eventbridge.EventBridgeAsyncClient;
import software.amazon.awssdk.services.eventbridge.model.DeleteRuleRequest;
import software.amazon.awssdk.services.eventbridge.model.DeleteRuleResponse;
import software.amazon.awssdk.services.eventbridge.model.ListRulesRequest;
import software.amazon.awssdk.services.eventbridge.model.ListRulesResponse;
import software.amazon.awssdk.services.eventbridge.model.ListTargetsByRuleRequest;
import software.amazon.awssdk.services.eventbridge.model.ListTargetsByRuleResponse;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequest;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequestEntry;
import software.amazon.awssdk.services.eventbridge.model.PutEventsResponse;
import software.amazon.awssdk.services.eventbridge.model.PutEventsResultEntry;
import software.amazon.awssdk.services.eventbridge.model.PutRuleRequest;
import software.amazon.awssdk.services.eventbridge.model.PutRuleResponse;
import software.amazon.awssdk.services.eventbridge.model.PutTargetsRequest;
import software.amazon.awssdk.services.eventbridge.model.PutTargetsResponse;
import software.amazon.awssdk.services.eventbridge.model.RemoveTargetsRequest;
import software.amazon.awssdk.services.eventbridge.model.RemoveTargetsResponse;
import software.amazon.awssdk.services.eventbridge.model.Rule;
import software.amazon.awssdk.services.eventbridge.model.RuleState;
import software.amazon.awssdk.services.eventbridge.model.Target;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventBridgeServiceTest {

    @Mock
    private LocalStackConfig config;

    @Mock
    private EventBridgeAsyncClient eventBridgeAsyncClient;

    private EventBridgeService eventBridgeService;

    @BeforeEach
    void setUp() {
        when(config.getEventBridgeAsyncClient()).thenReturn(eventBridgeAsyncClient);
        eventBridgeService = new EventBridgeService(config);
    }

    @Test
    void constructor_ShouldInitializeEventBridgeClient() {
        when(config.getEventBridgeAsyncClient()).thenReturn(eventBridgeAsyncClient);

        EventBridgeService service = new EventBridgeService(config);

        verify(config, atLeast(1)).getEventBridgeAsyncClient();
        assertNotNull(service);
    }

    @Test
    void createScheduleRule_ShouldCreateRuleWithCorrectParameters() {
        LocalDateTime scheduleTime = LocalDateTime.of(2024, 1, 15, 14, 30);
        String ruleName = "test-rule";
        String targetArn = "arn:aws:lambda:us-east-1:123456789012:function:test-function";
        Map<String, Object> inputData = Map.of("key", "value");
        String description = "Test description";

        CreateRuleRequest request = new CreateRuleRequest(
                ruleName,
                scheduleTime,
                targetArn,
                inputData,
                description,
                parsed.intent());

        PutRuleResponse putRuleResponse = PutRuleResponse.builder()
                .ruleArn("arn:aws:events:us-east-1:123456789012:rule/test-rule")
                .build();

        PutTargetsResponse putTargetsResponse = PutTargetsResponse.builder()
                .failedEntryCount(0)
                .build();

        when(eventBridgeAsyncClient.putRule(any(PutRuleRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(putRuleResponse));

        when(eventBridgeAsyncClient.putTargets(any(PutTargetsRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(putTargetsResponse));

        CompletableFuture<EventBridgeRuleRecord> resultFuture = eventBridgeService.createScheduleRule(request);
        EventBridgeRuleRecord ruleRecord = resultFuture.join();

        assertEquals(ruleName, ruleRecord.ruleName());
        assertEquals("cron(30 14 15 1 ? 2024)", ruleRecord.scheduleExpression());
        assertEquals(targetArn, ruleRecord.targetLambdaArn());
        assertTrue(ruleRecord.enabled());
        assertEquals(description, ruleRecord.description());
        assertNotNull(ruleRecord.targetInput());
        assertEquals("{\"key\":\"value\"}", ruleRecord.targetInput());

        ArgumentCaptor<PutRuleRequest> ruleRequestCaptor = ArgumentCaptor.forClass(PutRuleRequest.class);
        verify(eventBridgeAsyncClient).putRule(ruleRequestCaptor.capture());

        PutRuleRequest capturedRuleRequest = ruleRequestCaptor.getValue();
        assertEquals(ruleName, capturedRuleRequest.name());
        assertEquals("cron(30 14 15 1 ? 2024)", capturedRuleRequest.scheduleExpression());
        assertEquals(RuleState.ENABLED, capturedRuleRequest.state());
        assertEquals(description, capturedRuleRequest.description());
        assertEquals("default", capturedRuleRequest.eventBusName());

        ArgumentCaptor<PutTargetsRequest> targetsRequestCaptor = ArgumentCaptor.forClass(PutTargetsRequest.class);
        verify(eventBridgeAsyncClient).putTargets(targetsRequestCaptor.capture());

        PutTargetsRequest capturedTargetsRequest = targetsRequestCaptor.getValue();
        assertEquals(ruleName, capturedTargetsRequest.rule());
        assertEquals("default", capturedTargetsRequest.eventBusName());
        assertEquals(1, capturedTargetsRequest.targets().size());
        assertTrue(capturedTargetsRequest.targets().get(0).arn().contains("lambda"));
    }

    @Test
    void createScheduleRule_WithNullRuleName_ShouldGenerateName() {
        LocalDateTime scheduleTime = LocalDateTime.now();
        String targetArn = "arn:aws:lambda:test";
        Map<String, Object> inputData = Map.of("test", "data");
        String description = "Test description";

        CreateRuleRequest request = new CreateRuleRequest(
                null,
                scheduleTime,
                targetArn,
                inputData,
                description,
                parsed.intent());

        PutRuleResponse putRuleResponse = PutRuleResponse.builder()
                .ruleArn("arn:aws:events:test-rule")
                .build();

        PutTargetsResponse putTargetsResponse = PutTargetsResponse.builder()
                .failedEntryCount(0)
                .build();

        when(eventBridgeAsyncClient.putRule(any(PutRuleRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(putRuleResponse));

        when(eventBridgeAsyncClient.putTargets(any(PutTargetsRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(putTargetsResponse));

        CompletableFuture<EventBridgeRuleRecord> resultFuture = eventBridgeService.createScheduleRule(request);
        EventBridgeRuleRecord ruleRecord = resultFuture.join();

        assertNotNull(ruleRecord.ruleName());
        assertTrue(ruleRecord.ruleName().startsWith("reminder-rule-"));

        ArgumentCaptor<PutRuleRequest> ruleRequestCaptor = ArgumentCaptor.forClass(PutRuleRequest.class);
        verify(eventBridgeAsyncClient).putRule(ruleRequestCaptor.capture());

        PutRuleRequest capturedRequest = ruleRequestCaptor.getValue();
        assertNotNull(capturedRequest.name());
        assertTrue(capturedRequest.name().startsWith("reminder-rule-"));
    }

    @Test
    void createScheduleRule_WithNullDescription_ShouldUseDefaultDescription() {
        LocalDateTime scheduleTime = LocalDateTime.of(2024, 1, 15, 10, 0);
        String ruleName = "test-rule";
        String targetArn = "arn:aws:lambda:test";
        Map<String, Object> inputData = Map.of();

        CreateRuleRequest request = new CreateRuleRequest(
                ruleName,
                scheduleTime,
                targetArn,
                inputData,
                null,
                parsed.intent());

        PutRuleResponse putRuleResponse = PutRuleResponse.builder()
                .build();

        PutTargetsResponse putTargetsResponse = PutTargetsResponse.builder().build();

        when(eventBridgeAsyncClient.putRule(any(PutRuleRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(putRuleResponse));

        when(eventBridgeAsyncClient.putTargets(any(PutTargetsRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(putTargetsResponse));

        CompletableFuture<EventBridgeRuleRecord> resultFuture = eventBridgeService.createScheduleRule(request);
        resultFuture.join();

        ArgumentCaptor<PutRuleRequest> ruleRequestCaptor = ArgumentCaptor.forClass(PutRuleRequest.class);
        verify(eventBridgeAsyncClient).putRule(ruleRequestCaptor.capture());

        PutRuleRequest capturedRequest = ruleRequestCaptor.getValue();
        assertEquals("Reminder for: " + scheduleTime, capturedRequest.description());
    }

    @Test
    void createScheduleRule_WhenInputDataSerializationFails_ShouldUseEmptyJson() {
        LocalDateTime scheduleTime = LocalDateTime.now();
        String ruleName = "test-rule";
        String targetArn = "arn:aws:lambda:test";
        String description = "Test";

        Object nonSerializable = new Object() {
            @Override
            public String toString() {
                return "Non-serializable";
            }
        };
        Map<String, Object> inputData = Map.of("selfReference", nonSerializable);

        CreateRuleRequest request = new CreateRuleRequest(
                ruleName,
                scheduleTime,
                targetArn,
                inputData,
                description,
                parsed.intent());

        PutRuleResponse putRuleResponse = PutRuleResponse.builder().build();
        PutTargetsResponse putTargetsResponse = PutTargetsResponse.builder().build();

        when(eventBridgeAsyncClient.putRule(any(PutRuleRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(putRuleResponse));

        when(eventBridgeAsyncClient.putTargets(any(PutTargetsRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(putTargetsResponse));

        CompletableFuture<EventBridgeRuleRecord> resultFuture = eventBridgeService.createScheduleRule(request);
        EventBridgeRuleRecord ruleRecord = resultFuture.join();

        assertEquals("{}", ruleRecord.targetInput());
    }

    @Test
    void createScheduleRule_WhenPutRuleFails_ShouldThrowException() {
        LocalDateTime scheduleTime = LocalDateTime.now();
        String ruleName = "test-rule";
        String targetArn = "arn:aws:lambda:test";
        Map<String, Object> inputData = Map.of();
        String description = "Test";

        CreateRuleRequest request = new CreateRuleRequest(
                ruleName,
                scheduleTime,
                targetArn,
                inputData,
                description,
                parsed.intent());

        RuntimeException expectedException = new RuntimeException("EventBridge error");
        when(eventBridgeAsyncClient.putRule(any(PutRuleRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(expectedException));

        CompletableFuture<EventBridgeRuleRecord> resultFuture = eventBridgeService.createScheduleRule(request);

        CompletionException completionException = assertThrows(CompletionException.class, resultFuture::join);
        Throwable actualException = completionException.getCause();
        assertNotNull(actualException);
        assertEquals(RuntimeException.class, actualException.getClass());
        assertEquals("Failed to create EventBridge rule", actualException.getMessage());
    }

    @Test
    void createRateRule_ShouldCreateRateBasedRule() {
        String ruleName = "rate-rule";
        String rateExpression = "5 minutes";
        String targetArn = "arn:aws:lambda:test";
        String inputJson = "{\"key\": \"value\"}";

        PutRuleResponse putRuleResponse = PutRuleResponse.builder().build();
        PutTargetsResponse putTargetsResponse = PutTargetsResponse.builder().build();

        when(eventBridgeAsyncClient.putRule(any(PutRuleRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(putRuleResponse));

        when(eventBridgeAsyncClient.putTargets(any(PutTargetsRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(putTargetsResponse));

        CompletableFuture<EventBridgeRuleRecord> resultFuture = eventBridgeService.createRateRule(
                ruleName, rateExpression, targetArn, inputJson);
        EventBridgeRuleRecord ruleRecord = resultFuture.join();

        assertEquals(ruleName, ruleRecord.ruleName());
        assertEquals("rate(5 minutes)", ruleRecord.scheduleExpression());
        assertEquals(targetArn, ruleRecord.targetLambdaArn());
        assertEquals("Rate rule: 5 minutes", ruleRecord.description());
        assertEquals(inputJson, ruleRecord.targetInput());

        ArgumentCaptor<PutRuleRequest> ruleRequestCaptor = ArgumentCaptor.forClass(PutRuleRequest.class);
        verify(eventBridgeAsyncClient).putRule(ruleRequestCaptor.capture());

        PutRuleRequest capturedRequest = ruleRequestCaptor.getValue();
        assertEquals("rate(5 minutes)", capturedRequest.scheduleExpression());
        assertEquals("Rate rule: 5 minutes", capturedRequest.description());
    }

    @Test
    void deleteRule_WithTargets_ShouldDeleteTargetsAndRule() {
        String ruleName = "rule-to-delete";

        Target target = Target.builder()
                .id("target-1")
                .arn("arn:aws:lambda:test")
                .build();

        ListTargetsByRuleResponse listResponse = ListTargetsByRuleResponse.builder()
                .targets(target)
                .build();

        RemoveTargetsResponse removeResponse = RemoveTargetsResponse.builder()
                .failedEntryCount(0)
                .build();

        DeleteRuleResponse deleteResponse = DeleteRuleResponse.builder().build();

        when(eventBridgeAsyncClient.listTargetsByRule(any(ListTargetsByRuleRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(listResponse));

        when(eventBridgeAsyncClient.removeTargets(any(RemoveTargetsRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(removeResponse));

        when(eventBridgeAsyncClient.deleteRule(any(DeleteRuleRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(deleteResponse));

        CompletableFuture<Boolean> resultFuture = eventBridgeService.deleteRule(ruleName);
        Boolean result = resultFuture.join();

        assertTrue(result);

        ArgumentCaptor<ListTargetsByRuleRequest> listRequestCaptor = ArgumentCaptor.forClass(ListTargetsByRuleRequest.class);
        verify(eventBridgeAsyncClient).listTargetsByRule(listRequestCaptor.capture());
        assertEquals(ruleName, listRequestCaptor.getValue().rule());
        assertEquals("default", listRequestCaptor.getValue().eventBusName());

        ArgumentCaptor<RemoveTargetsRequest> removeRequestCaptor = ArgumentCaptor.forClass(RemoveTargetsRequest.class);
        verify(eventBridgeAsyncClient).removeTargets(removeRequestCaptor.capture());
        assertEquals(ruleName, removeRequestCaptor.getValue().rule());
        assertTrue(removeRequestCaptor.getValue().ids().contains("target-1"));

        ArgumentCaptor<DeleteRuleRequest> deleteRequestCaptor = ArgumentCaptor.forClass(DeleteRuleRequest.class);
        verify(eventBridgeAsyncClient).deleteRule(deleteRequestCaptor.capture());
        assertEquals(ruleName, deleteRequestCaptor.getValue().name());
        assertTrue(deleteRequestCaptor.getValue().force());
    }

    @Test
    void deleteRule_WithoutTargets_ShouldDeleteRuleOnly() {
        String ruleName = "rule-without-targets";

        ListTargetsByRuleResponse listResponse = ListTargetsByRuleResponse.builder()
                .targets(List.of())
                .build();

        DeleteRuleResponse deleteResponse = DeleteRuleResponse.builder().build();

        when(eventBridgeAsyncClient.listTargetsByRule(any(ListTargetsByRuleRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(listResponse));

        when(eventBridgeAsyncClient.deleteRule(any(DeleteRuleRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(deleteResponse));

        CompletableFuture<Boolean> resultFuture = eventBridgeService.deleteRule(ruleName);
        Boolean result = resultFuture.join();

        assertTrue(result);

        verify(eventBridgeAsyncClient).deleteRule(any(DeleteRuleRequest.class));
    }

    @Test
    void deleteRule_WhenDeleteFails_ShouldReturnFalse() {
        String ruleName = "failing-rule";

        ListTargetsByRuleResponse listResponse = ListTargetsByRuleResponse.builder()
                .targets(List.of())
                .build();

        RuntimeException expectedException = new RuntimeException("Delete failed");
        when(eventBridgeAsyncClient.listTargetsByRule(any(ListTargetsByRuleRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(listResponse));

        when(eventBridgeAsyncClient.deleteRule(any(DeleteRuleRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(expectedException));

        CompletableFuture<Boolean> resultFuture = eventBridgeService.deleteRule(ruleName);
        Boolean result = resultFuture.join();

        assertFalse(result);
    }

    @Test
    void sendEvent_ShouldSendEventSuccessfully() {
        SendEventRequest request = new SendEventRequest(
                "test.source",
                "test.detailType",
                "{\"key\": \"value\"}"
        );

        PutEventsResultEntry resultEntry = PutEventsResultEntry.builder()
                .eventId("test-event-id")
                .build();

        PutEventsResponse response = PutEventsResponse.builder()
                .entries(resultEntry)
                .build();

        when(eventBridgeAsyncClient.putEvents(any(PutEventsRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(response));

        CompletableFuture<String> resultFuture = eventBridgeService.sendEvent(request);
        String eventId = resultFuture.join();

        assertEquals("test-event-id", eventId);

        ArgumentCaptor<PutEventsRequest> eventsRequestCaptor = ArgumentCaptor.forClass(PutEventsRequest.class);
        verify(eventBridgeAsyncClient).putEvents(eventsRequestCaptor.capture());

        PutEventsRequest capturedRequest = eventsRequestCaptor.getValue();
        assertEquals(1, capturedRequest.entries().size());

        PutEventsRequestEntry capturedEntry = capturedRequest.entries().get(0);
        assertEquals("test.source", capturedEntry.source());
        assertEquals("test.detailType", capturedEntry.detailType());
        assertEquals("{\"key\": \"value\"}", capturedEntry.detail());
        assertEquals("default", capturedEntry.eventBusName());
    }

    @Test
    void sendEvent_WithCustomEventBus_ShouldUseSpecifiedBus() {
        SendEventRequest request = new SendEventRequest(
                "test.source",
                "test.detailType",
                "{}",
                "custom-bus"
        );

        PutEventsResultEntry resultEntry = PutEventsResultEntry.builder()
                .eventId("event-id")
                .build();

        PutEventsResponse response = PutEventsResponse.builder()
                .entries(resultEntry)
                .build();

        when(eventBridgeAsyncClient.putEvents(any(PutEventsRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(response));

        CompletableFuture<String> resultFuture = eventBridgeService.sendEvent(request);
        resultFuture.join();

        ArgumentCaptor<PutEventsRequest> eventsRequestCaptor = ArgumentCaptor.forClass(PutEventsRequest.class);
        verify(eventBridgeAsyncClient).putEvents(eventsRequestCaptor.capture());

        PutEventsRequestEntry capturedEntry = eventsRequestCaptor.getValue().entries().get(0);
        assertEquals("custom-bus", capturedEntry.eventBusName());
    }

    @Test
    void sendEvent_WhenResponseHasNoEntries_ShouldThrowException() {
        SendEventRequest request = new SendEventRequest(
                "test.source",
                "test.detailType",
                "{}"
        );

        PutEventsResponse response = PutEventsResponse.builder()
                .entries(List.of())
                .build();

        when(eventBridgeAsyncClient.putEvents(any(PutEventsRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(response));

        CompletableFuture<String> resultFuture = eventBridgeService.sendEvent(request);

        CompletionException exception = assertThrows(CompletionException.class, resultFuture::join);
        assertEquals("Failed to send event", exception.getCause().getMessage()); // Исправлено
    }

    @Test
    void sendEvent_WhenEventIdIsNull_ShouldThrowException() {
        SendEventRequest request = new SendEventRequest(
                "test.source",
                "test.detailType",
                "{}"
        );

        PutEventsResultEntry resultEntry = PutEventsResultEntry.builder()
                .eventId(null)
                .build();

        PutEventsResponse response = PutEventsResponse.builder()
                .entries(resultEntry)
                .build();

        when(eventBridgeAsyncClient.putEvents(any(PutEventsRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(response));

        CompletableFuture<String> resultFuture = eventBridgeService.sendEvent(request);

        CompletionException exception = assertThrows(CompletionException.class, resultFuture::join);
        assertTrue(exception.getCause() instanceof RuntimeException);
        assertEquals("Failed to send event", exception.getCause().getMessage());
    }

    @Test
    void getAllRules_ShouldReturnAllRulesWithDetails() {
        Rule rule1 = Rule.builder()
                .name("rule-1")
                .scheduleExpression("cron(0 10 * * ? *)")
                .state(RuleState.ENABLED)
                .description("Rule 1")
                .build();

        Rule rule2 = Rule.builder()
                .name("rule-2")
                .scheduleExpression("rate(5 minutes)")
                .state(RuleState.DISABLED)
                .description("Rule 2")
                .build();

        ListRulesResponse listRulesResponse = ListRulesResponse.builder()
                .rules(rule1, rule2)
                .build();

        Target target1 = Target.builder()
                .id("target-1")
                .arn("arn:aws:lambda:function-1")
                .input("{\"data\": \"test1\"}")
                .build();

        Target target2 = Target.builder()
                .id("target-2")
                .arn("arn:aws:lambda:function-2")
                .input("{\"data\": \"test2\"}")
                .build();

        ListTargetsByRuleResponse targetsResponse1 = ListTargetsByRuleResponse.builder()
                .targets(target1)
                .build();

        ListTargetsByRuleResponse targetsResponse2 = ListTargetsByRuleResponse.builder()
                .targets(target2)
                .build();

        when(eventBridgeAsyncClient.listRules(any(ListRulesRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(listRulesResponse));

        when(eventBridgeAsyncClient.listTargetsByRule(any(ListTargetsByRuleRequest.class)))
                .thenAnswer(invocation -> {
                    ListTargetsByRuleRequest request = invocation.getArgument(0);
                    String ruleName = request.rule();

                    if ("rule-1".equals(ruleName)) {
                        return CompletableFuture.completedFuture(targetsResponse1);
                    } else if ("rule-2".equals(ruleName)) {
                        return CompletableFuture.completedFuture(targetsResponse2);
                    }
                    return CompletableFuture.completedFuture(ListTargetsByRuleResponse.builder().build());
                });

        CompletableFuture<List<EventBridgeRuleRecord>> resultFuture = eventBridgeService.getAllRules();
        List<EventBridgeRuleRecord> rules = resultFuture.join();

        assertEquals(2, rules.size());

        EventBridgeRuleRecord record1 = rules.stream()
                .filter(r -> r.ruleName().equals("rule-1"))
                .findFirst()
                .orElseThrow();
        assertEquals("rule-1", record1.ruleName());
        assertEquals("cron(0 10 * * ? *)", record1.scheduleExpression());
        assertEquals("arn:aws:lambda:function-1", record1.targetLambdaArn());
        assertTrue(record1.enabled());
        assertEquals("Rule 1", record1.description());
        assertEquals("{\"data\": \"test1\"}", record1.targetInput());

        EventBridgeRuleRecord record2 = rules.stream()
                .filter(r -> r.ruleName().equals("rule-2"))
                .findFirst()
                .orElseThrow();
        assertEquals("rule-2", record2.ruleName());
        assertEquals("rate(5 minutes)", record2.scheduleExpression());
        assertEquals("arn:aws:lambda:function-2", record2.targetLambdaArn());
        assertFalse(record2.enabled());
        assertEquals("Rule 2", record2.description());
        assertEquals("{\"data\": \"test2\"}", record2.targetInput());
    }

    @Test
    void getRuleDetails_ShouldReturnRuleWithTargets() {
        String ruleName = "test-rule";

        Rule rule = Rule.builder()
                .name(ruleName)
                .scheduleExpression("cron(0 12 * * ? *)")
                .state(RuleState.ENABLED)
                .description("Test rule description")
                .build();

        ListRulesResponse listRulesResponse = ListRulesResponse.builder()
                .rules(rule)
                .build();

        Target target = Target.builder()
                .id("target-1")
                .arn("arn:aws:lambda:test-function")
                .input("{\"key\": \"value\"}")
                .build();

        ListTargetsByRuleResponse targetsResponse = ListTargetsByRuleResponse.builder()
                .targets(target)
                .build();

        when(eventBridgeAsyncClient.listRules(any(ListRulesRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(listRulesResponse));

        when(eventBridgeAsyncClient.listTargetsByRule(any(ListTargetsByRuleRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(targetsResponse));

        CompletableFuture<EventBridgeRuleRecord> resultFuture = eventBridgeService.getRuleDetails(ruleName);
        EventBridgeRuleRecord ruleRecord = resultFuture.join();

        assertEquals(ruleName, ruleRecord.ruleName());
        assertEquals("cron(0 12 * * ? *)", ruleRecord.scheduleExpression());
        assertEquals("arn:aws:lambda:test-function", ruleRecord.targetLambdaArn());
        assertTrue(ruleRecord.enabled());
        assertEquals("Test rule description", ruleRecord.description());
        assertEquals("{\"key\": \"value\"}", ruleRecord.targetInput());
    }

    @Test
    void getRuleDetails_WhenRuleNotFound_ShouldThrowException() {
        String ruleName = "non-existent-rule";

        ListRulesResponse listRulesResponse = ListRulesResponse.builder()
                .rules(List.of())
                .build();

        when(eventBridgeAsyncClient.listRules(any(ListRulesRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(listRulesResponse));

        CompletableFuture<EventBridgeRuleRecord> resultFuture = eventBridgeService.getRuleDetails(ruleName);

        CompletionException exception = assertThrows(CompletionException.class, resultFuture::join);
        assertTrue(exception.getCause() instanceof RuntimeException);
        assertTrue(exception.getCause().getMessage().contains("Rule not found"));
    }

    @Test
    void getRuleDetails_WhenNoTargets_ShouldReturnRecordWithNullTargetInfo() {
        String ruleName = "rule-without-targets";

        Rule rule = Rule.builder()
                .name(ruleName)
                .scheduleExpression("cron(0 0 * * ? *)")
                .state(RuleState.ENABLED)
                .description("No targets")
                .build();

        ListRulesResponse listRulesResponse = ListRulesResponse.builder()
                .rules(rule)
                .build();

        ListTargetsByRuleResponse targetsResponse = ListTargetsByRuleResponse.builder()
                .targets(List.of())
                .build();

        when(eventBridgeAsyncClient.listRules(any(ListRulesRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(listRulesResponse));

        when(eventBridgeAsyncClient.listTargetsByRule(any(ListTargetsByRuleRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(targetsResponse));

        CompletableFuture<EventBridgeRuleRecord> resultFuture = eventBridgeService.getRuleDetails(ruleName);
        EventBridgeRuleRecord ruleRecord = resultFuture.join();

        assertNull(ruleRecord.targetLambdaArn());
        assertNull(ruleRecord.targetInput());
    }

    @Test
    void createCronExpression_ShouldFormatDateTimeCorrectly() {
        LocalDateTime dateTime = LocalDateTime.of(2024, 12, 25, 15, 45);
        String ruleName = "test-rule";
        String targetArn = "arn:aws:lambda:test";
        Map<String, Object> inputData = Map.of();
        String description = "test";

        CreateRuleRequest request = new CreateRuleRequest(
                ruleName,
                dateTime,
                targetArn,
                inputData,
                description,
                parsed.intent());

        PutRuleResponse putRuleResponse = PutRuleResponse.builder().build();
        PutTargetsResponse putTargetsResponse = PutTargetsResponse.builder().build();

        when(eventBridgeAsyncClient.putRule(any(PutRuleRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(putRuleResponse));

        when(eventBridgeAsyncClient.putTargets(any(PutTargetsRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(putTargetsResponse));

        CompletableFuture<EventBridgeRuleRecord> resultFuture = eventBridgeService.createScheduleRule(request);
        EventBridgeRuleRecord ruleRecord = resultFuture.join();

        assertEquals("cron(45 15 25 12 ? 2024)", ruleRecord.scheduleExpression());
    }

    @Test
    void sendEvent_WhenPutEventsFails_ShouldThrowException() {
        SendEventRequest request = new SendEventRequest(
                "test.source",
                "test.detailType",
                "{}"
        );

        RuntimeException expectedException = new RuntimeException("Network error");
        when(eventBridgeAsyncClient.putEvents(any(PutEventsRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(expectedException));

        CompletableFuture<String> resultFuture = eventBridgeService.sendEvent(request);

        CompletionException exception = assertThrows(CompletionException.class, resultFuture::join);
        Throwable cause = exception.getCause();
        assertEquals("Failed to send event", cause.getMessage());
    }
}