package io.kestra.plugin.payfit.collaborators;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.SerializationFeature;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.triggers.StatefulTriggerService;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.models.triggers.TriggerOutput;
import io.kestra.core.models.triggers.TriggerService;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.plugin.payfit.AbstractPayfitTrigger;
import io.kestra.plugin.payfit.client.ChangeSet;
import io.kestra.plugin.payfit.client.PayfitClient;
import io.kestra.plugin.payfit.client.PayfitConnections;
import io.kestra.plugin.payfit.client.PayfitValidators;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Poll PayFit collaborators",
    description = "Polls `GET /companies/{companyId}/collaborators` and starts an execution when collaborators are created or updated. The first poll records the current set unless `fireOnInitial` is true. Requires the `collaborators:read` scope."
)
@Plugin(
    examples = {
        @Example(
            title = "Run a flow when a collaborator is created",
            full = true,
            code = """
                id: payfit_new_collaborator
                namespace: company.team

                tasks:
                  - id: log
                    type: io.kestra.plugin.core.log.Log
                    message: "New collaborators {{ trigger.collaborators | length }}"

                triggers:
                  - id: collaborators
                    type: io.kestra.plugin.payfit.collaborators.Trigger
                    apiKey: "{{ secret('PAYFIT_API_KEY') }}"
                    interval: PT5M
                    on: CREATE
                """
        )
    }
)
public class Trigger extends AbstractPayfitTrigger implements TriggerOutput<Trigger.Output> {
    private static final com.fasterxml.jackson.databind.ObjectMapper VERSION_MAPPER = JacksonMapper.ofJson()
        .copy()
        .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    @Schema(title = "Filter by a contract email address. Login emails are not searchable")
    @PluginProperty(group = "main")
    private Property<String> email;

    @Schema(title = "Maximum pages to read on each poll. Defaults to 100")
    @Builder.Default
    @PluginProperty(group = "processing")
    private Property<Integer> maxPages = Property.ofValue(100);

    @Override
    public Optional<Execution> evaluate(ConditionContext conditionContext, TriggerContext context) throws Exception {
        RunContext runContext = conditionContext.getRunContext();
        Map<String, String> query = new LinkedHashMap<>();
        String rEmail = PayfitValidators.email(PayfitConnections.optional(runContext, this.email), "email");
        if (rEmail != null) {
            query.put("email", rEmail);
        }
        java.util.List<Map<String, Object>> resources;
        try (PayfitClient client = client(runContext)) {
            PayfitClient.Page<io.kestra.plugin.payfit.model.Collaborator> page = client.list(
                client.companyPath("/collaborators"),
                "collaborators",
                query,
                true,
                PayfitValidators.MAX_PAGE_SIZE,
                PayfitConnections.integer(runContext, maxPages, 100),
                io.kestra.plugin.payfit.model.Collaborator.class
            );
            resources = page.items().stream()
                .map(item -> io.kestra.core.serializers.JacksonMapper.ofJson().convertValue(item, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {}))
                .toList();
        }

        String key = PayfitConnections.optional(runContext, getStateKey());
        if (key == null) {
            key = StatefulTriggerService.defaultKey(context.getNamespace(), context.getFlowId(), context.getTriggerId());
        }
        Duration ttl = getStateTtl() == null ? null : runContext.render(getStateTtl()).as(Duration.class).orElse(null);
        On on = getOn() == null ? On.CREATE : runContext.render(getOn()).as(On.class).orElse(On.CREATE);
        boolean fireOnInitial = PayfitConnections.bool(runContext, getFireOnInitial(), false);
        boolean initialized = io.kestra.plugin.payfit.client.TriggerState.initialized(runContext, key);
        var state = StatefulTriggerService.readState(runContext, key, Optional.ofNullable(ttl));
        ChangeSet.Decision decision = ChangeSet.evaluate(
            resources,
            state,
            on,
            initialized,
            fireOnInitial,
            item -> item.get("id") == null ? null : item.get("id").toString(),
            Trigger::version
        );
        StatefulTriggerService.writeState(runContext, key, decision.state(), Optional.empty());
        if (decision.initialSnapshot()) {
            runContext.logger().info("Recorded {} existing PayFit collaborators without starting an execution", decision.state().size());
            return Optional.empty();
        }
        if (decision.fired().isEmpty()) {
            return Optional.empty();
        }
        Output output = Output.builder().count(decision.fired().size()).collaborators(decision.fired()).build();
        return Optional.of(TriggerService.generateExecution(this, conditionContext, context, output));
    }

    static String version(Map<String, Object> item) {
        try {
            byte[] json = VERSION_MAPPER.writeValueAsBytes(item);
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(json));
        } catch (Exception e) {
            throw new io.kestra.plugin.payfit.client.PayfitException("Could not compute the PayFit collaborator version", e);
        }
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "Number of collaborators that matched `on`")
        private final int count;

        @Schema(title = "Collaborators that matched `on`, not the full company list")
        private final java.util.List<Map<String, Object>> collaborators;
    }
}
