package io.kestra.plugin.payfit.contracts;

import java.util.Map;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.payfit.AbstractPayfitTask;
import io.kestra.plugin.payfit.client.PayfitClient;
import io.kestra.plugin.payfit.client.PayfitConnections;
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
    title = "Get a PayFit contract",
    description = "Fetches `GET /companies/{companyId}/contracts/{contractId}`. Requires the `contracts:read` scope."
)
@Plugin(
    examples = {
        @Example(
            title = "Fetch one contract",
            full = true,
            code = """
                id: payfit_contract
                namespace: company.team

                tasks:
                  - id: contract
                    type: io.kestra.plugin.payfit.contracts.Get
                    apiKey: "{{ secret('PAYFIT_API_KEY') }}"
                    contractId: "contract_123"
                """
        )
    }
)
public class Get extends AbstractPayfitTask implements RunnableTask<Get.Output> {
    @Schema(title = "Contract id")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> contractId;

    @Override
    public Output run(RunContext runContext) throws Exception {
        String rContractId = PayfitConnections.required(runContext, this.contractId, "contractId");
        try (PayfitClient client = client(runContext)) {
            Map<String, Object> body = client.get(client.companyPath("/contracts/" + PayfitClient.pathSegment(rContractId)), Map.of());
            Object rawId = body.get("contractId") != null ? body.get("contractId") : body.get("id");
            return Output.builder()
                .id(rawId == null ? rContractId : rawId.toString())
                .contract(body)
                .build();
        }
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "Contract id")
        private final String id;

        @Schema(title = "Contract payload returned by PayFit")
        private final Map<String, Object> contract;
    }
}
