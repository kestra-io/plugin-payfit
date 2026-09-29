package io.kestra.plugin.payfit.absences;

import java.util.Map;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.payfit.AbstractPayfitTask;
import io.kestra.plugin.payfit.client.PayfitClient;
import io.kestra.plugin.payfit.client.PayfitConnections;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
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
public class Cancel extends AbstractPayfitTask implements RunnableTask<VoidOutput> {
    @Schema(title = "Absence id to cancel")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> absenceId;

    @Schema(title = "Optional comment recorded on the cancellation")
    @PluginProperty(group = "main")
    private Property<String> comment;

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        String rAbsenceId = PayfitConnections.required(runContext, this.absenceId, "absenceId");
        String rComment = PayfitConnections.optional(runContext, this.comment);
        try (PayfitClient client = client(runContext)) {
            String path = client.companyPath("/absences/" + PayfitClient.pathSegment(rAbsenceId));
            client.delete(path, rComment == null ? null : Map.of("comment", rComment));
            return null;
        }
    }
}
