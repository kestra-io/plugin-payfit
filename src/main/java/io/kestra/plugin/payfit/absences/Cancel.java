package io.kestra.plugin.payfit.absences;

import java.util.Map;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
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
    title = "Cancel a PayFit absence",
    description = "Calls `DELETE /companies/{companyId}/absences/{absenceId}`. PayFit returns 204 and does not accept updates to an existing absence. Requires the `time:write` scope."
)
@Plugin(
    examples = {
        @Example(
            title = "Cancel an absence",
            full = true,
            code = """
                id: payfit_cancel_absence
                namespace: company.team

                tasks:
                  - id: cancel
                    type: io.kestra.plugin.payfit.absences.Cancel
                    apiKey: "{{ secret('PAYFIT_API_KEY') }}"
                    absenceId: "absence_123"
                """
        )
    }
)
public class Cancel extends AbstractPayfitTask implements RunnableTask<Cancel.Output> {
    @Schema(title = "Absence id to cancel")
    @NotNull
    private Property<String> absenceId;

    @Schema(title = "Optional comment recorded on the cancellation")
    private Property<String> comment;

    @Override
    public Output run(RunContext runContext) throws Exception {
        String absenceId = PayfitConnections.required(runContext, this.absenceId, "absenceId");
        String comment = PayfitConnections.optional(runContext, this.comment);
        try (PayfitClient client = client(runContext)) {
            String path = client.companyPath("/absences/" + PayfitClient.pathSegment(absenceId));
            Map<String, Object> response = client.delete(path, comment == null ? null : Map.of("comment", comment));
            return Output.builder().absenceId(absenceId).body(response).build();
        }
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "Cancelled absence id")
        private final String absenceId;

        @Schema(title = "Raw PayFit response. Empty when PayFit returns no body")
        private final Map<String, Object> body;
    }
}
