package io.kestra.plugin.payfit.collaborators;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Metric;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.executions.metrics.Counter;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.common.FetchType;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.payfit.AbstractPayfitTask;
import io.kestra.plugin.payfit.client.ListFetch;
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
    title = "List PayFit collaborators",
    description = "Fetches `GET /companies/{companyId}/collaborators`. The API pages with `nextPageToken` and accepts at most 50 items per page. Email filtering matches contract emails, not the collaborator login email. Requires the `collaborators:read` scope."
)
@Plugin(
    examples = {
        @Example(
            title = "List every collaborator and keep the JSON in internal storage",
            full = true,
            code = """
                id: payfit_collaborators
                namespace: company.team

                tasks:
                  - id: collaborators
                    type: io.kestra.plugin.payfit.collaborators.List
                    apiKey: "{{ secret('PAYFIT_API_KEY') }}"
                    fetchType: STORE
                """
        )
    },
    metrics = {
        @Metric(name = "records", type = Counter.TYPE, description = "Number of collaborators returned")
    }
)
public class List extends AbstractPayfitTask implements RunnableTask<List.Output> {
    @Schema(title = "Filter by a contract email address. Login emails are not searchable")
    private Property<String> email;

    @Schema(title = "Page size. PayFit allows 1 to 50. When omitted, each request asks for 50 except `FETCH_ONE`, which asks for 1")
    private Property<Integer> maxResults;

    @Schema(title = "Pagination token from a previous response. Listing resumes from this token")
    private Property<String> nextPageToken;

    @Schema(
        title = "How to return collaborators",
        description = "`FETCH` (default) returns every collaborator in `collaborators`. `FETCH_ONE` returns the first collaborator in `collaborator`. `STORE` writes an ION file and returns `uri`. `NONE` returns only `count`."
    )
    @PluginProperty(group = "processing")
    @Builder.Default
    private Property<FetchType> fetchType = Property.ofValue(FetchType.FETCH);

    @Schema(title = "Maximum number of pages to read. Defaults to 100. Ignored for `FETCH_ONE`")
    @Builder.Default
    private Property<Integer> maxPages = Property.ofValue(100);

    @Override
    public Output run(RunContext runContext) throws Exception {
        FetchType fetchType = this.fetchType == null
            ? FetchType.FETCH
            : runContext.render(this.fetchType).as(FetchType.class).orElse(FetchType.FETCH);
        ListFetch.Plan plan = ListFetch.plan(fetchType, PayfitConnections.optionalInt(runContext, maxResults));
        int maxPages = PayfitConnections.integer(runContext, this.maxPages, 100);
        Map<String, String> query = new LinkedHashMap<>();
        String email = PayfitValidators.email(PayfitConnections.optional(runContext, this.email), "email");
        if (email != null) {
            query.put("email", email);
        }
        String token = PayfitConnections.optional(runContext, nextPageToken);
        if (token != null) {
            query.put("nextPageToken", token);
        }

        try (PayfitClient client = client(runContext)) {
            PayfitClient.Page<io.kestra.plugin.payfit.model.Collaborator> page = client.list(
                client.companyPath("/collaborators"),
                "collaborators",
                query,
                plan.fetchAll(),
                plan.pageSize(),
                maxPages,
                io.kestra.plugin.payfit.model.Collaborator.class
            );
            ListFetch.Result<io.kestra.plugin.payfit.model.Collaborator> result = ListFetch.shape(runContext, fetchType, page.items(), "payfit-collaborators.ion");
            runContext.metric(Counter.of("records", result.count()));
            return Output.builder()
                .count(result.count())
                .pages(page.pages())
                .nextPageToken(page.nextPageToken())
                .uri(result.uri())
                .collaborator(result.one())
                .collaborators(result.many())
                .build();
        }
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "Number of collaborators returned")
        private final int count;

        @Schema(title = "Number of PayFit pages read")
        private final int pages;

        @Schema(title = "Token for the next page when the result is incomplete")
        private final String nextPageToken;

        @Schema(title = "ION file URI. Set only when `fetchType` is `STORE`")
        private final URI uri;

        @Schema(title = "First collaborator. Set only when `fetchType` is `FETCH_ONE`")
        private final io.kestra.plugin.payfit.model.Collaborator collaborator;

        @Schema(title = "Collaborators. Set only when `fetchType` is `FETCH`")
        private final java.util.List<io.kestra.plugin.payfit.model.Collaborator> collaborators;
    }
}
