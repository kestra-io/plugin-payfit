package io.kestra.plugin.payfit.payslips;

import java.net.URI;
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
    title = "Download a PayFit payslip",
    description = "Fetches `GET /companies/{companyId}/collaborators/{collaboratorId}/contracts/{contractId}/payslips/{payslipId}` with `Accept: application/pdf` and stores the PDF in Kestra internal storage. The documented success body is PDF only. Requires the `contracts:payslips:read` scope."
)
@Plugin(
    examples = {
        @Example(
            title = "Download a payslip PDF",
            full = true,
            code = """
                id: payfit_payslip
                namespace: company.team

                tasks:
                  - id: payslip
                    type: io.kestra.plugin.payfit.payslips.Download
                    apiKey: "{{ secret('PAYFIT_API_KEY') }}"
                    collaboratorId: "collaborator_123"
                    contractId: "contract_123"
                    payslipId: "payslip_123"
                """
        )
    }
)
public class Download extends AbstractPayfitTask implements RunnableTask<Download.Output> {
    @Schema(title = "Collaborator id")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> collaboratorId;

    @Schema(title = "Contract id")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> contractId;

    @Schema(title = "Payslip id")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> payslipId;

    @Override
    public Output run(RunContext runContext) throws Exception {
        String rCollaboratorId = PayfitConnections.required(runContext, this.collaboratorId, "collaboratorId");
        String rContractId = PayfitConnections.required(runContext, this.contractId, "contractId");
        String rPayslipId = PayfitConnections.required(runContext, this.payslipId, "payslipId");
        String path = "/collaborators/" + PayfitClient.pathSegment(rCollaboratorId)
            + "/contracts/" + PayfitClient.pathSegment(rContractId)
            + "/payslips/" + PayfitClient.pathSegment(rPayslipId);
        try (PayfitClient client = client(runContext)) {
            byte[] bytes = client.getBytes(client.companyPath(path), Map.of(), Map.of("Accept", "application/pdf"));
            if (bytes.length == 0) {
                throw new IllegalStateException("PayFit returned an empty payslip");
            }
            URI uri = StoredDocuments.storeBytes(runContext, bytes, "payfit-payslip-" + rPayslipId + ".pdf");
            return Output.builder().uri(uri).contentType("application/pdf").size(bytes.length).build();
        }
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "Internal storage URI of the payslip")
        private final URI uri;

        @Schema(title = "Content type that was requested")
        private final String contentType;

        @Schema(title = "Size in bytes of the downloaded PDF")
        private final int size;
    }
}
