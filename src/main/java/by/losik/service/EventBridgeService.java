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
import software.amazon.awssdk.services.eventbridge.model.DeleteRuleRequest;
import software.amazon.awssdk.services.eventbridge.model.ListRulesRequest;
import software.amazon.awssdk.services.eventbridge.model.ListTargetsByRuleRequest;
import software.amazon.awssdk.services.eventbridge.model.ListTargetsByRuleResponse;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequest;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequestEntry;
import software.amazon.awssdk.services.eventbridge.model.PutRuleRequest;
import software.amazon.awssdk.services.eventbridge.model.PutTargetsRequest;
import software.amazon.awssdk.services.eventbridge.model.RemoveTargetsRequest;
import software.amazon.awssdk.services.eventbridge.model.Rule;
import software.amazon.awssdk.services.eventbridge.model.RuleState;
import software.amazon.awssdk.services.eventbridge.model.Target;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Сервис для работы с AWS EventBridge.
 * <p>
 * Предоставляет методы для:
 * <ul>
 *     <li>Создания правил планирования (cron, rate)</li>
 *     <li>Удаления правил и их target'ов</li>
 *     <li>Отправки событий в шины</li>
 * </ul>
 * <p>
 * Поддерживает несколько шин событий:
 * <ul>
 *     <li>Email шина — для email уведомлений</li>
 *     <li>Telegram шина — для Telegram уведомлений</li>
 * </ul>
 *
 * @see EventBridgeConfig
 * @see CronExpressionBuilder
 */
@Singleton
public class EventBridgeService {
    private static final Logger log = LoggerFactory.getLogger(EventBridgeService.class);

    private final EventBridgeAsyncClient eventBridgeAsyncClient;
    private final EventBridgeConfig config;

    /**
     * Создаёт сервис EventBridge.
     *
     * @param localStackConfig конфигурация LocalStack для клиента
     * @param eventBridgeConfig конфигурация имён шин
     */
    @Inject
    public EventBridgeService(LocalStackConfig localStackConfig,
                              EventBridgeConfig eventBridgeConfig) {
        this.eventBridgeAsyncClient = localStackConfig.getEventBridgeAsyncClient();
        this.config = eventBridgeConfig;
    }

    /**
     * Создаёт правило для email уведомлений.
     *
     * @param request параметры правила
     * @return созданное правило
     */
    public CompletableFuture<EventBridgeRuleRecord> createEmailRule(CreateRuleRequest request) {
        return createScheduleRule(request, config.getEmailEventBusName());
    }

    /**
     * Создаёт правило для Telegram уведомлений.
     *
     * @param request параметры правила
     * @return созданное правило
     */
    public CompletableFuture<EventBridgeRuleRecord> createTelegramRule(CreateRuleRequest request) {
        return createScheduleRule(request, config.getTelegramEventBusName());
    }

    /**
     * Создаёт правило планирования в указанной шине.
     *
     * @param request параметры правила
     * @param eventBusName имя шины событий
     * @return созданное правило
     */
    public CompletableFuture<EventBridgeRuleRecord> createScheduleRule(
            CreateRuleRequest request, String eventBusName) {
        String scheduleExpression = CronExpressionBuilder.fromLocalDateTime(request.scheduleTime());
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
                            .thenApply(targetsResponse -> {
                                log.info("Created EventBridge rule: {} with target: {} in bus: {}",
                                        ruleName, request.targetArn(), eventBusName);

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

    /**
     * Создаёт rate правило в шине email.
     *
     * @param ruleName имя правила
     * @param rateExpression rate выражение (например, "5 minutes")
     * @param targetArn ARN целевой Lambda функции
     * @param inputJson входные данные для Lambda
     * @return созданное правило
     */
    public CompletableFuture<EventBridgeRuleRecord> createRateRule(
            String ruleName, String rateExpression, String targetArn, String inputJson) {
        return createRateRule(ruleName, rateExpression, targetArn, inputJson, config.getEmailEventBusName());
    }

    /**
     * Создаёт rate правило в указанной шине.
     *
     * @param ruleName имя правила
     * @param rateExpression rate выражение (например, "5 minutes")
     * @param targetArn ARN целевой Lambda функции
     * @param inputJson входные данные для Lambda
     * @param eventBusName имя шины событий
     * @return созданное правило
     */
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

    /**
     * Удаляет правило из шины email.
     *
     * @param ruleName имя правила
     * @return true если правило удалено
     */
    public CompletableFuture<Boolean> deleteRule(String ruleName) {
        return deleteRule(ruleName, config.getEmailEventBusName());
    }

    /**
     * Удаляет правило из указанной шины.
     *
     * @param ruleName имя правила
     * @param eventBusName имя шины событий
     * @return true если правило удалено
     */
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

    /**
     * Отправляет событие в шину email.
     *
     * @param request параметры события
     * @return ID отправленного события
     */
    public CompletableFuture<String> sendEvent(SendEventRequest request) {
        return sendEvent(request, config.getEmailEventBusName());
    }

    /**
     * Отправляет событие в указанную шину.
     *
     * @param request параметры события
     * @param eventBusName имя шины событий
     * @return ID отправленного события
     */
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

    /**
     * Получает все правила из шины email.
     *
     * @return список правил
     */
    public CompletableFuture<List<EventBridgeRuleRecord>> getAllRules() {
        return getAllRules(config.getEmailEventBusName());
    }

    /**
     * Получает все правила из указанной шины.
     *
     * @param eventBusName имя шины событий
     * @return список правил
     */
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

    /**
     * Получает детали правила из шины email.
     *
     * @param ruleName имя правила
     * @return детали правила
     */
    public CompletableFuture<EventBridgeRuleRecord> getRuleDetails(String ruleName) {
        return getRuleDetails(ruleName, config.getEmailEventBusName());
    }

    /**
     * Получает детали правила из указанной шины.
     *
     * @param ruleName имя правила
     * @param eventBusName имя шины событий
     * @return детали правила
     */
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

    /**
     * Получает список target'ов для правила из шины email.
     *
     * @param ruleName имя правила
     * @return список target'ов
     */
    private CompletableFuture<List<Target>> listTargets(String ruleName) {
        return listTargets(ruleName, config.getEmailEventBusName());
    }

    /**
     * Получает список target'ов для правила из указанной шины.
     *
     * @param ruleName имя правила
     * @param eventBusName имя шины событий
     * @return список target'ов
     */
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

    /**
     * Удаляет правило из шины email.
     *
     * @param ruleName имя правила
     * @return CompletableFuture
     */
    private CompletableFuture<Void> deleteRuleInternal(String ruleName) {
        return deleteRuleInternal(ruleName, config.getEmailEventBusName());
    }

    /**
     * Удаляет правило из указанной шины.
     *
     * @param ruleName имя правила
     * @param eventBusName имя шины событий
     * @return CompletableFuture
     */
    private CompletableFuture<Void> deleteRuleInternal(String ruleName, String eventBusName) {
        DeleteRuleRequest request = DeleteRuleRequest.builder()
                .name(ruleName)
                .eventBusName(eventBusName)
                .force(true)
                .build();

        return eventBridgeAsyncClient.deleteRule(request)
                .thenApply(response -> null);
    }
}