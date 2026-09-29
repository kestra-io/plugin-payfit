package io.kestra.plugin.payfit.absences;

import java.net.URI;
import java.util.LinkedHashMap;
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
import io.kestra.plugin.payfit.client.PayfitValidators;
import io.kestra.plugin.payfit.client.StoredDocuments;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
    title = "List PayFit absences",
    description = "Fetches `GET /companies/{companyId}/absences`. PayFit returns approved absences unless `status` is set. Use `all` to return every status, or a comma-separated list such as `approved,pending_approval`. Allowed statuses are `approved`, `pending_approval`, `declined`, `cancelled`, and `pending_cancellation`. Requires the `time:read` scope."
)
@Plugin(
    examples = {
        @Example(
            title = "List approved absences for one contract",
            full = true,
            code = """
                id: payfit_absences
                namespace: company.team

                tasks:
                  - id: absences
                    type: io.kestra.plugin.payfit.absences.List
                    apiKey: "{{ secret('PAYFIT_API_KEY') }}"
                    contractId: "contract_123"
                    status: approved
                """
        )
    },
    metrics = {
        @Metric(name = "records", type = Counter.TYPE, description = "Number of absences returned")
    }
)
public class List extends AbstractPayfitTask implements RunnableTask<List.Output> {
    @Schema(title = "Restrict the list to one contract")
    @PluginProperty(group = "main")
    private Property<String> contractId;

    @Schema(title = "Absence status filter. Defaults to the API default of `approved` when omitted")
    @PluginProperty(group = "main")
    private Property<String> status;

    @Schema(title = "Include absences that end on or after this date (`YYYY-MM-DD`)")
    @PluginProperty(group = "main")
    private Property<String> beginDate;

    @Schema(title = "Include absences that start on or before this date (`YYYY-MM-DD`)")
    @PluginProperty(group = "main")
    private Property<String> endDate;

    @Schema(title = "Page size, from 1 to 50")
    @Min(1)
    @Max(50)
    @PluginProperty(group = "processing")
    private Property<Integer> maxResults;

    @Schema(title = "Pagination token from a previous response")
    @PluginProperty(group = "processing")
    private Property<String> nextPageToken;

    @Schema(
        title = "How to return absences",
        description = "`FETCH` returns every absence in `absences`. `FETCH_ONE` returns the first absence in `absence`. `STORE` writes an ION file and returns `uri`. `NONE` returns only `count`."
    )
    @io.kestra.core.models.annotations.PluginProperty(group = "processing")
    @Builder.Default
    private Property<io.kestra.core.models.tasks.common.FetchType> fetchType = Property.ofValue(io.kestra.core.models.tasks.common.FetchType.FETCH);

    @Schema(title = "Maximum pages to read. Defaults to 100")
    @Builder.Default
    @PluginProperty(group = "processing")
    private Property<Integer> maxPages = Property.ofValue(100);

    @Override
    public Output run(RunContext runContext) throws Exception {
        Map<String, String> query = new LinkedHashMap<>();
        String rContractId = PayfitConnections.optional(runContext, this.contractId);
        if (rContractId != null) {
            query.put("contractId", rContractId);
        }
        String rStatus = PayfitValidators.absenceStatus(PayfitConnections.optional(runContext, this.status));
        if (rStatus != null) {
            query.put("status", rStatus);
        }
        String rBeginDate = PayfitConnections.optional(runContext, this.beginDate);
        if (rBeginDate != null) {
            query.put("beginDate", PayfitValidators.isoDate(rBeginDate, "beginDate"));
        }
        String rEndDate = PayfitConnections.optional(runContext, this.endDate);
        if (rEndDate != null) {
            query.put("endDate", PayfitValidators.isoDate(rEndDate, "endDate"));
        }
        String token = PayfitConnections.optional(runContext, nextPageToken);
        if (token != null) {
            query.put("nextPageToken", token);
        }
        try (PayfitClient client = client(runContext)) {
            var rFetchType = this.fetchType == null
                ? io.kestra.core.models.tasks.common.FetchType.FETCH
                : runContext.render(this.fetchType).as(io.kestra.core.models.tasks.common.FetchType.class).orElse(io.kestra.core.models.tasks.common.FetchType.FETCH);
            var plan = io.kestra.plugin.payfit.client.ListFetch.plan(rFetchType, PayfitConnections.optionalInt(runContext, maxResults));
            PayfitClient.Page<io.kestra.plugin.payfit.model.Absence> page = client.list(
                client.companyPath("/absences"),
                "absences",
                query,
                plan.fetchAll(),
                plan.pageSize(),
                PayfitConnections.integer(runContext, maxPages, 100),
                io.kestra.plugin.payfit.model.Absence.class
            );
            io.kestra.plugin.payfit.client.ListFetch.Result<io.kestra.plugin.payfit.model.Absence> result = io.kestra.plugin.payfit.client.ListFetch.shape(runContext, rFetchType, page.items(), "payfit-absences.ion");
            runContext.metric(Counter.of("records", result.count()));
            return Output.builder()
                .count(result.count())
                .pages(page.pages())
                .nextPageToken(page.nextPageToken())
                .uri(result.uri())
                .absence(result.one())
                .absences(result.many())
                .build();
        }
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "Number of absences returned")
        private final int count;

        @Schema(title = "Number of pages read")
        private final int pages;

        @Schema(title = "Next page token when the result is incomplete")
        private final String nextPageToken;

        @Schema(title = "ION file URI. Set only when `fetchType` is `STORE`")
        private final URI uri;

        @Schema(title = "First absence. Set only when `fetchType` is `FETCH_ONE`")
        private final io.kestra.plugin.payfit.model.Absence absence;

        @Schema(title = "Absences. Set only when `fetchType` is `FETCH`")
        private final java.util.List<io.kestra.plugin.payfit.model.Absence> absences;
    }
}
