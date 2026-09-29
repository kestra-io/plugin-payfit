package io.kestra.plugin.payfit.contracts;

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
    title = "List PayFit contracts",
    description = "Fetches `GET /companies/{companyId}/contracts`. Results are paginated with `nextPageToken` and a maximum page size of 50. Requires the `contracts:read` scope."
)
@Plugin(
    examples = {
        @Example(
            title = "List contracts, including ones still being created",
            full = true,
            code = """
                id: payfit_contracts
                namespace: company.team

                tasks:
                  - id: contracts
                    type: io.kestra.plugin.payfit.contracts.List
                    apiKey: "{{ secret('PAYFIT_API_KEY') }}"
                    includeInProgressContracts: true
                """
        )
    },
    metrics = {
        @Metric(name = "records", type = Counter.TYPE, description = "Number of contracts returned")
    }
)
public class List extends AbstractPayfitTask implements RunnableTask<List.Output> {
    @Schema(title = "Include contracts that are still being created")
    @PluginProperty(group = "main")
    private Property<Boolean> includeInProgressContracts;

    @Schema(title = "Page size, from 1 to 50")
    @PluginProperty(group = "processing")
    private Property<Integer> maxResults;

    @Schema(title = "Pagination token from a previous response")
    @PluginProperty(group = "processing")
    private Property<String> nextPageToken;

    @Schema(
        title = "How to return contracts",
        description = "`FETCH` returns every contract in `contracts`. `FETCH_ONE` returns the first contract in `contract`. `STORE` writes an ION file and returns `uri`. `NONE` returns only `count`."
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
        Boolean inProgress = PayfitConnections.optionalBoolean(runContext, includeInProgressContracts);
        if (inProgress != null) {
            query.put("includeInProgressContracts", Boolean.toString(inProgress));
        }
        String token = PayfitConnections.optional(runContext, nextPageToken);
        if (token != null) {
            query.put("nextPageToken", token);
        }
        var rFetchType = this.fetchType == null
            ? io.kestra.core.models.tasks.common.FetchType.FETCH
            : runContext.render(this.fetchType).as(io.kestra.core.models.tasks.common.FetchType.class).orElse(io.kestra.core.models.tasks.common.FetchType.FETCH);
        var plan = io.kestra.plugin.payfit.client.ListFetch.plan(rFetchType, PayfitConnections.optionalInt(runContext, maxResults));
        try (PayfitClient client = client(runContext)) {
            PayfitClient.Page<io.kestra.plugin.payfit.model.Contract> page = client.list(
                client.companyPath("/contracts"),
                "contracts",
                query,
                plan.fetchAll(),
                plan.pageSize(),
                PayfitConnections.integer(runContext, maxPages, 100),
                io.kestra.plugin.payfit.model.Contract.class
            );
            io.kestra.plugin.payfit.client.ListFetch.Result<io.kestra.plugin.payfit.model.Contract> result = io.kestra.plugin.payfit.client.ListFetch.shape(runContext, rFetchType, page.items(), "payfit-contracts.ion");
            runContext.metric(Counter.of("records", result.count()));
            return Output.builder()
                .count(result.count())
                .pages(page.pages())
                .nextPageToken(page.nextPageToken())
                .uri(result.uri())
                .contract(result.one())
                .contracts(result.many())
                .build();
        }
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "Number of contracts returned")
        private final int count;

        @Schema(title = "Number of pages read")
        private final int pages;

        @Schema(title = "Next page token when the result is incomplete")
        private final String nextPageToken;

        @Schema(title = "ION file URI. Set only when `fetchType` is `STORE`")
        private final URI uri;

        @Schema(title = "First contract. Set only when `fetchType` is `FETCH_ONE`")
        private final io.kestra.plugin.payfit.model.Contract contract;

        @Schema(title = "Contracts. Set only when `fetchType` is `FETCH`")
        private final java.util.List<io.kestra.plugin.payfit.model.Contract> contracts;
    }
}
