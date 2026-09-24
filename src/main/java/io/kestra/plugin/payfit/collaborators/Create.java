package io.kestra.plugin.payfit.collaborators;

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
    title = "Create a PayFit collaborator",
    description = "Calls `POST /companies/{companyId}/collaborators`. The collaborator is created without a contract and does not appear in the PayFit app until a contract is initialized with `contracts.Create`. Requires the `collaborators:write` scope. A social security number, when sent, must match the length expected by the company country (FR 15, ES 14, GB 12)."
)
@Plugin(
    examples = {
        @Example(
            title = "Initialize a collaborator",
            full = true,
            code = """
                id: payfit_create_collaborator
                namespace: company.team

                tasks:
                  - id: create
                    type: io.kestra.plugin.payfit.collaborators.Create
                    apiKey: "{{ secret('PAYFIT_API_KEY') }}"
                    firstName: "Ada"
                    lastName: "Lovelace"
                    personalEmail: "ada@example.com"
                """
        )
    }
)
public class Create extends AbstractPayfitTask implements RunnableTask<Create.Output> {
    @Schema(title = "First name")
    @NotNull
    private Property<String> firstName;

    @Schema(title = "Last name")
    @NotNull
    private Property<String> lastName;

    @Schema(title = "Personal email address")
    @NotNull
    private Property<String> personalEmail;

    @Schema(title = "Additional name, such as a French nom d'usage, a Spanish segundo apellido, or a UK middle name")
    private Property<String> otherName;

    @Schema(title = "Social security number. Allowed lengths are 12 (GB), 14 (ES), and 15 (FR)")
    private Property<String> socialSecurityNumber;

    @Schema(title = "Personal phone number")
    private Property<String> personalPhoneNumber;

    @Schema(title = "Gender. Allowed values are `MALE` and `FEMALE`")
    private Property<String> gender;

    @Schema(title = "Number of children, from 0 to 20")
    private Property<Integer> numberOfChildren;

    @Schema(title = "Send a PayFit invitation email. Maps to `inviteCollaborator`. This beta flag defaults to false when omitted")
    private Property<Boolean> inviteCollaborator;

    @Schema(title = "Personal address object accepted by PayFit")
    private Property<Map<String, Object>> personalAddress;

    @Schema(title = "Birth information object accepted by PayFit")
    private Property<Map<String, Object>> birthInformation;

    @Schema(title = "Additional JSON fields merged into the request. Explicit task properties override keys in this map")
    private Property<Map<String, Object>> body;

    @Override
    public Output run(RunContext runContext) throws Exception {
        Map<String, Object> explicit = new LinkedHashMap<>();
        explicit.put("firstName", PayfitConnections.required(runContext, firstName, "firstName"));
        explicit.put("lastName", PayfitConnections.required(runContext, lastName, "lastName"));
        explicit.put("personalEmail", PayfitValidators.email(PayfitConnections.required(runContext, personalEmail, "personalEmail"), "personalEmail"));
        explicit.put("otherName", PayfitConnections.optional(runContext, otherName));
        String socialSecurityNumber = PayfitConnections.optional(runContext, this.socialSecurityNumber);
        if (socialSecurityNumber != null) {
            int length = socialSecurityNumber.length();
            if (length != 12 && length != 14 && length != 15) {
                throw new IllegalArgumentException("socialSecurityNumber length must be 12 (GB), 14 (ES), or 15 (FR)");
            }
        }
        explicit.put("socialSecurityNumber", socialSecurityNumber);
        explicit.put("personalPhoneNumber", PayfitConnections.optional(runContext, personalPhoneNumber));
        explicit.put("gender", PayfitValidators.gender(PayfitConnections.optional(runContext, gender)));
        explicit.put("numberOfChildren", PayfitValidators.children(PayfitConnections.optionalInt(runContext, numberOfChildren)));
        explicit.put("inviteCollaborator", PayfitConnections.optionalBoolean(runContext, inviteCollaborator));
        explicit.put("personalAddress", PayfitConnections.optionalMap(runContext, personalAddress));
        explicit.put("birthInformation", PayfitConnections.optionalMap(runContext, birthInformation));
        Map<String, Object> request = JsonBodies.merge(PayfitConnections.optionalMap(runContext, body), explicit);

        try (PayfitClient client = client(runContext)) {
            Map<String, Object> response = client.post(client.companyPath("/collaborators"), request);
            String id = JsonBodies.firstId(response);
            if (id == null) {
                throw new IllegalStateException("PayFit created a collaborator but did not return an id");
            }
            return Output.builder().id(id).body(response).build();
        }
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "Created collaborator id")
        private final String id;

        @Schema(title = "Raw PayFit response")
        private final Map<String, Object> body;
    }
}
