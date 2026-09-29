package io.kestra.plugin.payfit.collaborators;

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
    title = "Get a PayFit collaborator",
    description = "Fetches `GET /companies/{companyId}/collaborators/{collaboratorId}`. Requires the `collaborators:read` scope."
)
@Plugin(
    examples = {
        @Example(
            title = "Fetch one collaborator",
            full = true,
            code = """
                id: payfit_collaborator
                namespace: company.team

                tasks:
                  - id: collaborator
                    type: io.kestra.plugin.payfit.collaborators.Get
                    apiKey: "{{ secret('PAYFIT_API_KEY') }}"
                    collaboratorId: "collaborator_123"
                """
        )
    }
)
public class Get extends AbstractPayfitTask implements RunnableTask<Get.Output> {
    @Schema(title = "Collaborator id")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> collaboratorId;

    @Override
    public Output run(RunContext runContext) throws Exception {
        String rCollaboratorId = PayfitConnections.required(runContext, this.collaboratorId, "collaboratorId");
        try (PayfitClient client = client(runContext)) {
            Map<String, Object> body = client.get(client.companyPath("/collaborators/" + PayfitClient.pathSegment(rCollaboratorId)), Map.of());
            return Output.builder()
                .id(body.get("id") == null ? rCollaboratorId : body.get("id").toString())
                .collaborator(body)
                .build();
        }
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "Collaborator id")
        private final String id;

        @Schema(title = "Collaborator payload returned by PayFit")
        private final Map<String, Object> collaborator;
    }
}
