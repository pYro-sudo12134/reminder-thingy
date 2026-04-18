package by.losik.service;

import by.losik.config.EventBridgeConfig;
import by.losik.config.LocalStackConfig;
import by.losik.dto.CreateRuleRequest;
import by.losik.dto.EventBridgeRuleRecord;
import by.losik.dto.SendEventRequest;
import by.losik.util.CronExpressionBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.eventbridge.EventBridgeAsyncClient;
import software.amazon.awssdk.services.eventbridge.model.*;
import software.amazon.awssdk.services.lambda.LambdaAsyncClient;
import software.amazon.awssdk.services.lambda.model.AddPermissionRequest;
import software.amazon.awssdk.services.lambda.model.AddPermissionResponse;
import software.amazon.awssdk.services.lambda.model.ResourceConflictException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Singleton
public class EventBridgeService {
    private static final Logger log = LoggerFactory.getLogger(EventBridgeService.class);

    private final EventBridgeAsyncClient eventBridgeAsyncClient;
    private final LambdaAsyncClient lambdaAsyncClient;
    private final EventBridgeConfig config;
    private final String defaultEventBusName = "default";

    @Inject
    public EventBridgeService(LocalStackConfig localStackConfig,
                              EventBridgeConfig eventBridgeConfig,
                              LambdaAsyncClient lambdaAsyncClient) {
        this.eventBridgeAsyncClient = localStackConfig.getEventBridgeAsyncClient();
        this.config = eventBridgeConfig;
        this.lambdaAsyncClient = lambdaAsyncClient;
    }

    public CompletableFuture<EventBridgeRuleRecord> createEmailRule(CreateRuleRequest request) {
        return createScheduleRule(request, config.getEmailEventBusName());
    }

    public CompletableFuture<EventBridgeRuleRecord> createTelegramRule(CreateRuleRequest request) {
        return createScheduleRule(request, config.getTelegramEventBusName());
    }

    public CompletableFuture<EventBridgeRuleRecord> createScheduleRule(
            CreateRuleRequest request, String eventBusName) {
        String scheduleExpression = CronExpressionBuilder.fromLocalDateTime(request.scheduleTime());
        String ruleName = request.ruleName() != null ?
                request.ruleName() :
                "reminder-rule-" + System.currentTimeMillis();

        log.info("Creating rule with schedule expression: '{}' for time: {}",
                scheduleExpression, request.scheduleTime());
        PutRuleRequest ruleRequest = PutRuleRequest.builder()
                .name(ruleName)
                .scheduleExpression(scheduleExpression)
                .state(RuleState.ENABLED)
                .description(request.description() != null ?
                        request.description() :
                        "Reminder for: " + request.scheduleTime())
                .eventBusName(eventBusName)
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
                            .eventBusName(eventBusName)
                            .targets(target)
                            .build();

                    String finalInputJson = inputJson;
                    return eventBridgeAsyncClient.putTargets(targetsRequest)
                            .thenCompose(targetsResponse -> {
                                log.info("Created EventBridge rule: {} with target: {} in bus: {}",
                                        ruleName, request.targetArn(), eventBusName);

                                if (request.targetArn() != null && request.targetArn().contains("lambda")) {
                                    return addPermissionForEventBridge(ruleName, request.targetArn())
                                            .thenApply(permissionResponse -> {
                                                log.info("Added permission for EventBridge to invoke Lambda");
                                                return new EventBridgeRuleRecord(
                                                        ruleName,
                                                        scheduleExpression,
                                                        request.targetArn(),
                                                        true,
                                                        request.description(),
                                                        finalInputJson
                                                );
                                            });
                                } else {
                                    return CompletableFuture.completedFuture(new EventBridgeRuleRecord(
                                            ruleName,
                                            scheduleExpression,
                                            request.targetArn(),
                                            true,
                                            request.description(),
                                            finalInputJson
                                    ));
                                }
                            });
                })
                .exceptionally(ex -> {
                    log.error("Failed to create EventBridge rule: {}", ruleName, ex);
                    throw new RuntimeException("Failed to create EventBridge rule", ex);
                });
    }

    public CompletableFuture<EventBridgeRuleRecord> createRateRule(
            String ruleName, String rateExpression, String targetArn, String inputJson) {
        return createRateRule(ruleName, rateExpression, targetArn, inputJson, config.getEmailEventBusName());
    }

    public CompletableFuture<EventBridgeRuleRecord> createRateRule(
            String ruleName, String rateExpression, String targetArn, String inputJson, String eventBusName) {

        PutRuleRequest ruleRequest = PutRuleRequest.builder()
                .name(ruleName)
                .scheduleExpression("rate(" + rateExpression + ")")
                .state(RuleState.ENABLED)
                .description("Rate rule: " + rateExpression)
                .eventBusName(eventBusName)
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
                            .eventBusName(eventBusName)
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
        return deleteRule(ruleName, config.getEmailEventBusName());
    }

    public CompletableFuture<Boolean> deleteRule(String ruleName, String eventBusName) {
        return listTargets(ruleName, eventBusName)
                .thenCompose(targets -> {
                    CompletableFuture<Void> deleteTargetsFuture = CompletableFuture.completedFuture(null);

                    if (!targets.isEmpty()) {
                        List<String> targetIds = targets.stream()
                                .map(Target::id)
                                .collect(Collectors.toList());

                        RemoveTargetsRequest removeRequest = RemoveTargetsRequest.builder()
                                .rule(ruleName)
                                .eventBusName(eventBusName)
                                .ids(targetIds)
                                .build();

                        deleteTargetsFuture = eventBridgeAsyncClient.removeTargets(removeRequest)
                                .thenApply(response -> null);
                    }

                    return deleteTargetsFuture.thenCompose(v -> deleteRuleInternal(ruleName, eventBusName));
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
        return sendEvent(request, config.getEmailEventBusName());
    }

    public CompletableFuture<String> sendEvent(SendEventRequest request, String eventBusName) {
        String actualEventBusName = request.eventBusName() != null ?
                request.eventBusName() : eventBusName;

        PutEventsRequestEntry event = PutEventsRequestEntry.builder()
                .source(request.source())
                .detailType(request.detailType())
                .detail(request.detailJson())
                .eventBusName(actualEventBusName)
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
        return getAllRules(config.getEmailEventBusName());
    }

    public CompletableFuture<List<EventBridgeRuleRecord>> getAllRules(String eventBusName) {
        ListRulesRequest request = ListRulesRequest.builder()
                .eventBusName(eventBusName)
                .limit(100)
                .build();

        return eventBridgeAsyncClient.listRules(request)
                .thenCompose(rulesResponse -> {
                    List<CompletableFuture<EventBridgeRuleRecord>> ruleFutures =
                            rulesResponse.rules().stream()
                                    .map(rule -> getRuleDetails(rule.name(), eventBusName)).toList();

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
        return getRuleDetails(ruleName, config.getEmailEventBusName());
    }

    public CompletableFuture<EventBridgeRuleRecord> getRuleDetails(String ruleName, String eventBusName) {
        return eventBridgeAsyncClient.listRules(
                ListRulesRequest.builder()
                        .eventBusName(eventBusName)
                        .namePrefix(ruleName)
                        .build()
        ).thenCompose(rulesResponse -> {
            Rule rule = rulesResponse.rules().stream()
                    .filter(r -> r.name().equals(ruleName))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Rule not found: " + ruleName));

            return listTargets(ruleName, eventBusName).thenApply(targets -> {
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
        return listTargets(ruleName, config.getEmailEventBusName());
    }

    private CompletableFuture<List<Target>> listTargets(String ruleName, String eventBusName) {
        ListTargetsByRuleRequest request = ListTargetsByRuleRequest.builder()
                .rule(ruleName)
                .eventBusName(eventBusName)
                .build();

        return eventBridgeAsyncClient.listTargetsByRule(request)
                .thenApply(ListTargetsByRuleResponse::targets)
                .exceptionally(ex -> {
                    log.warn("Failed to list targets for rule: {}", ruleName, ex);
                    return List.of();
                });
    }

    private CompletableFuture<Void> deleteRuleInternal(String ruleName) {
        return deleteRuleInternal(ruleName, config.getEmailEventBusName());
    }

    private CompletableFuture<Void> deleteRuleInternal(String ruleName, String eventBusName) {
        DeleteRuleRequest request = DeleteRuleRequest.builder()
                .name(ruleName)
                .eventBusName(eventBusName)
                .force(true)
                .build();

        return eventBridgeAsyncClient.deleteRule(request)
                .thenApply(response -> null);
    }

    private CompletableFuture<AddPermissionResponse> addPermissionForEventBridge(
            String ruleName,
            String targetArn) {

        String functionName = extractFunctionNameFromArn(targetArn);
        String statementId = "eventbridge-" + ruleName;

        return eventBridgeAsyncClient.describeRule(
                    DescribeRuleRequest.builder()
                            .name(ruleName)
                            .eventBusName(defaultEventBusName)
                            .build())
                .thenCompose(ruleResponse -> {
                    String ruleArn = ruleResponse.arn();

                    AddPermissionRequest permissionRequest = AddPermissionRequest.builder()
                            .functionName(functionName)
                            .action("lambda:InvokeFunction")
                            .principal("events.amazonaws.com")
                            .sourceArn(ruleArn)
                            .statementId(statementId)
                            .build();

                    log.info("Adding permission for EventBridge rule '{}' to invoke Lambda '{}'",
                            ruleName, functionName);

                    return lambdaAsyncClient.addPermission(permissionRequest);
                })
                .exceptionally(ex -> {
                    if (ex.getCause() instanceof ResourceConflictException) {
                        log.warn("Permission already exists for statementId: {}", statementId);
                        return null;
                    }
                    log.error("Failed to add permission for EventBridge: {}", ex.getMessage());
                    throw new RuntimeException("Failed to add permission", ex);
                });
    }

    private String extractFunctionNameFromArn(String arn) {
        String[] parts = arn.split(":");
        if (parts.length >= 7 && parts[5].startsWith("function")) {
            return parts[parts.length - 1];
        }
        return arn;
    }
}