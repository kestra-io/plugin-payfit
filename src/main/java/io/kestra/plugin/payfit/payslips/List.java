package io.kestra.plugin.payfit.payslips;

import java.net.URI;
import java.util.Map;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Metric;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.executions.metrics.Counter;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.payfit.AbstractPayfitTask;
import io.kestra.plugin.payfit.client.PayfitClient;
import io.kestra.plugin.payfit.client.PayfitConnections;
import io.kestra.plugin.payfit.client.StoredDocuments;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
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
    title = "List a collaborator's PayFit payslips",
    description = "Fetches `GET /companies/{companyId}/collaborators/{collaboratorId}/payslips`. Each item includes the payslip id and the relative URL of the payslip file. Requires the `contracts:payslips:read` scope."
)
@Plugin(
    examples = {
        @Example(
            title = "List payslips before downloading one",
            full = true,
            code = """
                id: payfit_payslips
                namespace: company.team

                tasks:
                  - id: payslips
                    type: io.kestra.plugin.payfit.payslips.List
                    apiKey: "{{ secret('PAYFIT_API_KEY') }}"
                    collaboratorId: "collaborator_123"
                """
        )
    },
    metrics = {
        @Metric(name = "records", type = Counter.TYPE, description = "Number of payslips returned")
    }
)
public class List extends AbstractPayfitTask implements RunnableTask<List.Output> {
    @Schema(title = "Collaborator id")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> collaboratorId;

    @Schema(
        title = "How to return payslips",
        description = "`FETCH` returns every payslip in `payslips`. `FETCH_ONE` returns the first payslip in `payslip`. `STORE` writes an ION file and returns `uri`. `NONE` returns only `count`."
    )
    @io.kestra.core.models.annotations.PluginProperty(group = "processing")
    @Builder.Default
    private Property<io.kestra.core.models.tasks.common.FetchType> fetchType = Property.ofValue(io.kestra.core.models.tasks.common.FetchType.FETCH);

    @Override
    @SuppressWarnings("unchecked")
    public Output run(RunContext runContext) throws Exception {
        String rCollaboratorId = PayfitConnections.required(runContext, this.collaboratorId, "collaboratorId");
        var rFetchType = this.fetchType == null
            ? io.kestra.core.models.tasks.common.FetchType.FETCH
            : runContext.render(this.fetchType).as(io.kestra.core.models.tasks.common.FetchType.class).orElse(io.kestra.core.models.tasks.common.FetchType.FETCH);
        try (PayfitClient client = client(runContext)) {
            Map<String, Object> body = client.get(
                client.companyPath("/collaborators/" + PayfitClient.pathSegment(rCollaboratorId) + "/payslips"),
                Map.of()
            );
            Object raw = body.get("payslips");
            if (raw != null && !(raw instanceof java.util.List<?>)) {
                throw new IllegalStateException("PayFit payslip response field 'payslips' was not an array");
            }
            java.util.List<io.kestra.plugin.payfit.model.Payslip> payslips = raw == null
                ? java.util.List.of()
                : io.kestra.core.serializers.JacksonMapper.ofJson().convertValue(raw, new com.fasterxml.jackson.core.type.TypeReference<java.util.List<io.kestra.plugin.payfit.model.Payslip>>() {});
            var result = io.kestra.plugin.payfit.client.ListFetch.shape(runContext, rFetchType, payslips, "payfit-payslips.ion");
            runContext.metric(Counter.of("records", result.count()));
            return Output.builder()
                .count(result.count())
                .uri(result.uri())
                .payslip(result.one())
                .payslips(result.many())
                .build();
        }
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "Number of payslips returned")
        private final int count;

        @Schema(title = "ION file URI. Set only when `fetchType` is `STORE`")
        private final URI uri;

        @Schema(title = "First payslip. Set only when `fetchType` is `FETCH_ONE`")
        private final io.kestra.plugin.payfit.model.Payslip payslip;

        @Schema(title = "Payslips. Set only when `fetchType` is `FETCH`")
        private final java.util.List<io.kestra.plugin.payfit.model.Payslip> payslips;
    }
}
