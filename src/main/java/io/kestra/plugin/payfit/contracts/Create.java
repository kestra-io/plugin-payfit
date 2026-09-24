package io.kestra.plugin.payfit.contracts;

import java.util.LinkedHashMap;
import java.util.Map;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.payfit.AbstractPayfitTask;
import io.kestra.plugin.payfit.client.JsonBodies;
import io.kestra.plugin.payfit.client.PayfitClient;
import io.kestra.plugin.payfit.client.PayfitConnections;
import io.kestra.plugin.payfit.client.PayfitValidators;
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
    title = "Initialize a PayFit contract",
    description = "Calls `POST /companies/{companyId}/collaborators/{collaboratorId}/contracts`. Currently available for French companies only. PayFit returns 201 with an empty body, and the contract can take 2 to 5 minutes before it can be read. The contract is not finalized until an administrator completes it in PayFit. Requires the `collaborators:contracts:write` scope."
)
@Plugin(
    examples = {
        @Example(
            title = "Initialize a contract for a new collaborator",
            full = true,
            code = """
                id: payfit_create_contract
                namespace: company.team

                tasks:
                  - id: contract
                    type: io.kestra.plugin.payfit.contracts.Create
                    apiKey: "{{ secret('PAYFIT_API_KEY') }}"
                    collaboratorId: "{{ outputs.create.id }}"
                    jobTitle: "Software Engineer"
                    startDate: "2026-01-06"
                """
        )
    }
)
public class Create extends AbstractPayfitTask implements RunnableTask<Create.Output> {
    @Schema(title = "Collaborator id returned by `collaborators.Create`")
    @NotNull
    private Property<String> collaboratorId;

    @Schema(title = "Job title")
    @NotNull
    private Property<String> jobTitle;

    @Schema(title = "Contract start date, as `YYYY-MM-DD`")
    @NotNull
    private Property<String> startDate;

    @Override
    public Output run(RunContext runContext) throws Exception {
        String collaboratorId = PayfitConnections.required(runContext, this.collaboratorId, "collaboratorId");
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("jobTitle", PayfitConnections.required(runContext, jobTitle, "jobTitle"));
        request.put("startDate", PayfitValidators.isoDate(PayfitConnections.required(runContext, startDate, "startDate"), "startDate"));

        try (PayfitClient client = client(runContext)) {
            client.requireCountry("FR");
            String path = client.companyPath("/collaborators/" + PayfitClient.pathSegment(collaboratorId) + "/contracts");
            Map<String, Object> response = client.post(path, request);
            return Output.builder()
                .id(JsonBodies.firstId(response))
                .collaboratorId(collaboratorId)
                .body(response)
                .build();
        }
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "Created contract id when PayFit returns one. The current API returns an empty 201 body")
        private final String id;

        @Schema(title = "Collaborator id the contract was attached to")
        private final String collaboratorId;

        @Schema(title = "Raw PayFit response")
        private final Map<String, Object> body;
    }
}
