package io.kestra.plugin.payfit.absences;

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.SerializationFeature;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
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
import io.kestra.plugin.payfit.client.StoredDocuments;
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
    title = "Poll PayFit absences",
    description = "Polls `GET /companies/{companyId}/absences` and starts an execution when absences are created or change status. The first poll records the current set unless `fireOnInitial` is true. Requires the `time:read` scope."
)
@Plugin(
    examples = {
        @Example(
            title = "Run a flow when an absence is approved",
            full = true,
            code = """
                id: payfit_new_absence
                namespace: company.team

                tasks:
                  - id: log
                    type: io.kestra.plugin.core.log.Log
                    message: "Absences {{ trigger.absences | length }}"

                triggers:
                  - id: absences
                    type: io.kestra.plugin.payfit.absences.Trigger
                    apiKey: "{{ secret('PAYFIT_API_KEY') }}"
                    interval: PT10M
                    status: approved
                    on: CREATE_OR_UPDATE
                """
        )
    }
)
public class Trigger extends AbstractPayfitTrigger implements TriggerOutput<Trigger.Output> {
    private static final com.fasterxml.jackson.databind.ObjectMapper VERSION_MAPPER = JacksonMapper.ofJson()
        .copy()
        .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    @Schema(title = "Restrict polling to one contract")
    private Property<String> contractId;

    @Schema(title = "Absence status filter. Omit it to use PayFit's default of approved absences")
    private Property<String> status;

    @Schema(title = "Include absences that end on or after this date (`YYYY-MM-DD`)")
    private Property<String> beginDate;

    @Schema(title = "Include absences that start on or before this date (`YYYY-MM-DD`)")
    private Property<String> endDate;

    @Schema(title = "Maximum pages to read on each poll. Defaults to 100")
    @Builder.Default
    private Property<Integer> maxPages = Property.ofValue(100);

    @Override
    public Optional<Execution> evaluate(ConditionContext conditionContext, TriggerContext context) throws Exception {
        RunContext runContext = conditionContext.getRunContext();
        Map<String, String> query = new LinkedHashMap<>();
        String contractId = PayfitConnections.optional(runContext, this.contractId);
        if (contractId != null) {
            query.put("contractId", contractId);
        }
        String status = PayfitValidators.absenceStatus(PayfitConnections.optional(runContext, this.status));
        if (status != null) {
            query.put("status", status);
        }
        String beginDate = PayfitConnections.optional(runContext, this.beginDate);
        if (beginDate != null) {
            query.put("beginDate", PayfitValidators.isoDate(beginDate, "beginDate"));
        }
        String endDate = PayfitConnections.optional(runContext, this.endDate);
        if (endDate != null) {
            query.put("endDate", PayfitValidators.isoDate(endDate, "endDate"));
        }

        java.util.List<Map<String, Object>> resources;
        try (PayfitClient client = client(runContext)) {
            PayfitClient.Page<io.kestra.plugin.payfit.model.Absence> page = client.list(
                client.companyPath("/absences"),
                "absences",
                query,
                true,
                PayfitValidators.MAX_PAGE_SIZE,
                PayfitConnections.integer(runContext, maxPages, 100),
                io.kestra.plugin.payfit.model.Absence.class
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
            Trigger::absenceId,
            Trigger::version
        );
        StatefulTriggerService.writeState(runContext, key, decision.state(), Optional.empty());
        if (decision.initialSnapshot() || decision.fired().isEmpty()) {
            return Optional.empty();
        }
        URI uri = runContext.storage() == null ? null : StoredDocuments.storeJson(runContext, decision.fired(), "payfit-absence-changes.json");
        Output output = Output.builder().count(decision.fired().size()).uri(uri).absences(decision.fired()).build();
        return Optional.of(TriggerService.generateExecution(this, conditionContext, context, output));
    }

    static String absenceId(Map<String, Object> item) {
        if (item == null) {
            return null;
        }
        Object id = item.get("id");
        if (id == null) {
            id = item.get("absenceId");
        }
        return id == null ? null : id.toString();
    }

    static String version(Map<String, Object> item) {
        try {
            return VERSION_MAPPER.writeValueAsString(item);
        } catch (Exception e) {
            return String.valueOf(item);
        }
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "Number of absences that matched the trigger")
        private final int count;

        @Schema(title = "Internal storage URI of the matching absences")
        private final URI uri;

        @Schema(title = "Absences that were created or updated")
        private final java.util.List<Map<String, Object>> absences;
    }
}
