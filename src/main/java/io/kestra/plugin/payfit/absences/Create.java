package io.kestra.plugin.payfit.absences;

import java.util.LinkedHashMap;
import java.util.Map;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
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
    title = "Create a PayFit absence",
    description = "Calls `POST /companies/{companyId}/absences` and creates an approved absence. `startDate` and `endDate` are sent as `{date, moment}` objects. Allowed moments are `beginning-of-day`, `middle-of-day`, and `end-of-day`. The absence type must be one of the country codes published by PayFit, such as `fr_conges_payes`, `es_vacaciones`, or `uk_annual_leave`. PayFit does not support updating an absence. Requires the `time:write` scope."
)
@Plugin(
    examples = {
        @Example(
            title = "Create an approved absence",
            full = true,
            code = """
                id: payfit_create_absence
                namespace: company.team

                tasks:
                  - id: absence
                    type: io.kestra.plugin.payfit.absences.Create
                    apiKey: "{{ secret('PAYFIT_API_KEY') }}"
                    contractId: "contract_123"
                    absenceType: "fr_conges_payes"
                    startDate: "2026-12-24"
                    startMoment: "beginning-of-day"
                    endDate: "2026-12-26"
                    endMoment: "end-of-day"
                """
        )
    }
)
public class Create extends AbstractPayfitTask implements RunnableTask<Create.Output> {
    @Schema(title = "Contract id the absence applies to")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> contractId;

    @Schema(title = "PayFit absence type code")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> absenceType;

    @Schema(title = "Absence start date, as `YYYY-MM-DD`")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> startDate;

    @Schema(title = "Absence end date, as `YYYY-MM-DD`")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> endDate;

    @Schema(title = "Moment when the absence starts. Defaults to `beginning-of-day`")
    @Builder.Default
    @PluginProperty(group = "main")
    private Property<String> startMoment = Property.ofValue("beginning-of-day");

    @Schema(title = "Moment when the absence ends. Defaults to `end-of-day`")
    @Builder.Default
    @PluginProperty(group = "main")
    private Property<String> endMoment = Property.ofValue("end-of-day");

    @Override
    public Output run(RunContext runContext) throws Exception {
        String rStartDate = PayfitValidators.isoDate(PayfitConnections.required(runContext, this.startDate, "startDate"), "startDate");
        String rEndDate = PayfitValidators.isoDate(PayfitConnections.required(runContext, this.endDate, "endDate"), "endDate");
        String rStartMoment = PayfitValidators.absenceMoment(PayfitConnections.optional(runContext, this.startMoment), "startMoment", "beginning-of-day");
        String rEndMoment = PayfitValidators.absenceMoment(PayfitConnections.optional(runContext, this.endMoment), "endMoment", "end-of-day");
        PayfitValidators.absenceRange(rStartDate, rStartMoment, rEndDate, rEndMoment);
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("contractId", PayfitConnections.required(runContext, contractId, "contractId"));
        request.put("type", PayfitValidators.absenceType(PayfitConnections.required(runContext, absenceType, "absenceType")));
        Map<String, Object> start = new LinkedHashMap<>();
        start.put("date", rStartDate);
        start.put("moment", rStartMoment);
        Map<String, Object> end = new LinkedHashMap<>();
        end.put("date", rEndDate);
        end.put("moment", rEndMoment);
        request.put("startDate", start);
        request.put("endDate", end);

        try (PayfitClient client = client(runContext)) {
            Map<String, Object> response = client.post(client.companyPath("/absences"), request);
            return Output.builder()
                .id(JsonBodies.firstId(response))
                .body(response)
                .build();
        }
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "Created absence id, when PayFit returns one")
        private final String id;

        @Schema(title = "Raw PayFit response")
        private final Map<String, Object> body;
    }
}
