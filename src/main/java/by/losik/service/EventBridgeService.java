package by.losik.service;

import by.losik.config.LocalStackConfig;
import by.losik.dto.CreateRuleRequest;
import by.losik.dto.EventBridgeRuleRecord;
import by.losik.dto.SendEventRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.eventbridge.EventBridgeAsyncClient;
import software.amazon.awssdk.services.eventbridge.model.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Singleton
public class EventBridgeService {
    private static final Logger log = LoggerFactory.getLogger(EventBridgeService.class);

    private final EventBridgeAsyncClient eventBridgeAsyncClient;
    private final String defaultEventBusName = "default";

    @Inject
    public EventBridgeService(LocalStackConfig config) {
        this.eventBridgeAsyncClient = config.getEventBridgeAsyncClient();
    }

    public CompletableFuture<EventBridgeRuleRecord> createScheduleRule(CreateRuleRequest request) {
        String scheduleExpression = createCronExpression(request.scheduleTime());
        String ruleName = request.ruleName() != null ?
                request.ruleName() :
                "reminder-rule-" + System.currentTimeMillis();

        PutRuleRequest ruleRequest = PutRuleRequest.builder()
                .name(ruleName)
                .scheduleExpression(scheduleExpression)
                .state(RuleState.ENABLED)
                .description(request.description() != null ?
                        request.description() :
                        "Reminder for: " + request.scheduleTime())
                .eventBusName(defaultEventBusName)
                .build();

        return eventBridgeAsyncClient.putRule(ruleRequest)
                .thenCompose(ruleResponse -> {
                    String inputJson;
                    try {
                        ObjectMapper mapper = new ObjectMapper();
                        inputJson = mapper.writeValueAsString(request.inputData());
                    } catch (Exception e) {
                        log.error("Failed to serialize input data", e);
                        inputJson = "{}";
                    }

                    Target target = Target.builder()
                            .id("lambda-target-" + System.currentTimeMillis())
                            .arn(request.targetArn())
                            .input(inputJson)
                            .build();

                    PutTargetsRequest targetsRequest = PutTargetsRequest.builder()
                            .rule(ruleName)
                            .eventBusName(defaultEventBusName)
                            .targets(target)
                            .build();

                    String finalInputJson = inputJson;
                    return eventBridgeAsyncClient.putTargets(targetsRequest)
                            .thenApply(targetsResponse -> {
                                log.info("Created EventBridge rule: {} with target: {}",
                                        ruleName, request.targetArn());

                                return new EventBridgeRuleRecord(
                                        ruleName,
                                        scheduleExpression,
                                        request.targetArn(),
                                        true,
                                        request.description(),
                                        finalInputJson
                                );
                            });
                })
                .exceptionally(ex -> {
                    log.error("Failed to create EventBridge rule: {}", ruleName, ex);
                    throw new RuntimeException("Failed to create EventBridge rule", ex);
                });
    }

    public CompletableFuture<EventBridgeRuleRecord> createRateRule(
            String ruleName, String rateExpression, String targetArn, String inputJson) {

        PutRuleRequest ruleRequest = PutRuleRequest.builder()
                .name(ruleName)
                .scheduleExpression("rate(" + rateExpression + ")")
                .state(RuleState.ENABLED)
                .description("Rate rule: " + rateExpression)
                .eventBusName(defaultEventBusName)
                .build();

        return eventBridgeAsyncClient.putRule(ruleRequest)
                .thenCompose(ruleResponse -> {
                    Target target = Target.builder()
                            .id("rate-target")
                            .arn(targetArn)
                            .input(inputJson)
                            .build();

                    PutTargetsRequest targetsRequest = PutTargetsRequest.builder()
                            .rule(ruleName)
                            .eventBusName(defaultEventBusName)
                            .targets(target)
                            .build();

                    return eventBridgeAsyncClient.putTargets(targetsRequest)
                            .thenApply(targetsResponse -> new EventBridgeRuleRecord(
                                    ruleName,
                                    "rate(" + rateExpression + ")",
                                    targetArn,
                                    true,
                                    "Rate rule: " + rateExpression,
                                    inputJson
                            ));
                });
    }

    public CompletableFuture<Boolean> deleteRule(String ruleName) {
        return listTargets(ruleName)
                .thenCompose(targets -> {
                    CompletableFuture<Void> deleteTargetsFuture = CompletableFuture.completedFuture(null);

                    if (!targets.isEmpty()) {
                        List<String> targetIds = targets.stream()
                                .map(Target::id)
                                .collect(Collectors.toList());

                        RemoveTargetsRequest removeRequest = RemoveTargetsRequest.builder()
                                .rule(ruleName)
                                .eventBusName(defaultEventBusName)
                                .ids(targetIds)
                                .build();

                        deleteTargetsFuture = eventBridgeAsyncClient.removeTargets(removeRequest)
                                .thenApply(response -> null);
                    }

                    return deleteTargetsFuture.thenCompose(v -> deleteRuleInternal(ruleName));
                })
                .thenApply(response -> {
                    log.info("Successfully deleted rule: {}", ruleName);
                    return true;
                })
                .exceptionally(ex -> {
                    log.error("Failed to delete rule: {}", ruleName, ex);
                    return false;
                });
    }

    public CompletableFuture<String> sendEvent(SendEventRequest request) {
        String eventBusName = request.eventBusName() != null ?
                request.eventBusName() : defaultEventBusName;

        PutEventsRequestEntry event = PutEventsRequestEntry.builder()
                .source(request.source())
                .detailType(request.detailType())
                .detail(request.detailJson())
                .eventBusName(eventBusName)
                .build();

        PutEventsRequest eventsRequest = PutEventsRequest.builder()
                .entries(event)
                .build();

        return eventBridgeAsyncClient.putEvents(eventsRequest)
                .thenApply(response -> {
                    if (response.entries().isEmpty()) {
                        throw new RuntimeException("No entries in response");
                    }

                    String eventId = response.entries().get(0).eventId();
                    if (eventId == null) {
                        throw new RuntimeException("Failed to send event");
                    }

                    log.info("Event sent successfully: {} - {}", request.source(), request.detailType());
                    return eventId;
                })
                .exceptionally(ex -> {
                    log.error("Failed to send event: {} - {}", request.source(), request.detailType(), ex);
                    throw new RuntimeException("Failed to send event", ex);
                });
    }

    public CompletableFuture<List<EventBridgeRuleRecord>> getAllRules() {
        ListRulesRequest request = ListRulesRequest.builder()
                .eventBusName(defaultEventBusName)
                .limit(100)
                .build();

        return eventBridgeAsyncClient.listRules(request)
                .thenCompose(rulesResponse -> {
                    List<CompletableFuture<EventBridgeRuleRecord>> ruleFutures =
                            rulesResponse.rules().stream()
                                    .map(rule -> getRuleDetails(rule.name())).toList();

                    return CompletableFuture.allOf(ruleFutures.toArray(new CompletableFuture[0]))
                            .thenApply(v -> ruleFutures.stream()
                                    .map(CompletableFuture::join)
                                    .collect(Collectors.toList()));
                })
                .exceptionally(ex -> {
                    log.error("Failed to list EventBridge rules", ex);
                    throw new RuntimeException("Failed to list rules", ex);
                });
    }

    public CompletableFuture<EventBridgeRuleRecord> getRuleDetails(String ruleName) {
        return eventBridgeAsyncClient.listRules(
                ListRulesRequest.builder()
                        .eventBusName(defaultEventBusName)
                        .namePrefix(ruleName)
                        .build()
        ).thenCompose(rulesResponse -> {
            Rule rule = rulesResponse.rules().stream()
                    .filter(r -> r.name().equals(ruleName))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Rule not found: " + ruleName));

            CompletableFuture<List<Target>> targetsFuture = listTargets(ruleName);

            return targetsFuture.thenApply(targets -> {
                String targetArn = null;
                String targetInput = null;

                if (!targets.isEmpty()) {
                    Target firstTarget = targets.get(0);
                    targetArn = firstTarget.arn();
                    targetInput = firstTarget.input();
                }

                return new EventBridgeRuleRecord(
                        ruleName,
                        rule.scheduleExpression(),
                        targetArn,
                        rule.state() == RuleState.ENABLED,
                        rule.description(),
                        targetInput
                );
            });
        });
    }

    private CompletableFuture<List<Target>> listTargets(String ruleName) {
        ListTargetsByRuleRequest request = ListTargetsByRuleRequest.builder()
                .rule(ruleName)
                .eventBusName(defaultEventBusName)
                .build();

        return eventBridgeAsyncClient.listTargetsByRule(request)
                .thenApply(ListTargetsByRuleResponse::targets)
                .exceptionally(ex -> {
                    log.warn("Failed to list targets for rule: {}", ruleName, ex);
                    return List.of();
                });
    }

    private CompletableFuture<Void> deleteRuleInternal(String ruleName) {
        DeleteRuleRequest request = DeleteRuleRequest.builder()
                .name(ruleName)
                .eventBusName(defaultEventBusName)
                .force(true)
                .build();

        return eventBridgeAsyncClient.deleteRule(request)
                .thenApply(response -> null);
    }

    private String createCronExpression(LocalDateTime dateTime) {
        return String.format("cron(%d %d %d %d ? %d)",
                dateTime.getMinute(),
                dateTime.getHour(),
                dateTime.getDayOfMonth(),
                dateTime.getMonthValue(),
                dateTime.getYear()
        );
    }
}