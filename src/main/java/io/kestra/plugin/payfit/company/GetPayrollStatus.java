package io.kestra.plugin.payfit.company;

import java.time.Instant;
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
import io.kestra.plugin.payfit.client.PayfitValidators;
import io.kestra.plugin.payfit.model.PayrollStatus;
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
    title = "Get PayFit payroll status",
    description = "Fetches `GET /companies/{companyId}/payroll-status?date=YYYYMM`. Returns the payroll status ('completed' or 'not_completed') and execution end timestamp for a given pay period. No scope is required beyond a valid company token."
)
@Plugin(
    examples = {
        @Example(
            title = "Check whether a payroll period has completed",
            full = true,
            code = """
                id: payfit_payroll_status
                namespace: company.team

                tasks:
                  - id: status
                    type: io.kestra.plugin.payfit.company.GetPayrollStatus
                    apiKey: "{{ secret('PAYFIT_API_KEY') }}"
                    date: "202610"
                """
        )
    }
)
public class GetPayrollStatus extends AbstractPayfitTask implements RunnableTask<GetPayrollStatus.Output> {
    @Schema(title = "Payroll period in `YYYYMM` format")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> date;

    @Override
    public Output run(RunContext runContext) throws Exception {
        String rDate = PayfitValidators.accountingPeriod(PayfitConnections.required(runContext, this.date, "date"));
        try (PayfitClient client = client(runContext)) {
            PayrollStatus payrollStatus = client.read(
                client.companyPath("/payroll-status"),
                Map.of("date", rDate),
                PayrollStatus.class
            );

            Instant executionEnd = null;
            if (payrollStatus != null && payrollStatus.getExecutionEndDate() != null && !payrollStatus.getExecutionEndDate().isBlank()) {
                executionEnd = Instant.parse(payrollStatus.getExecutionEndDate());
            }

            boolean completed = payrollStatus != null && "completed".equalsIgnoreCase(payrollStatus.getStatus());

            return Output.builder()
                .status(payrollStatus == null ? null : payrollStatus.getStatus())
                .executionEndDate(executionEnd)
                .completed(completed)
                .payrollStatus(payrollStatus)
                .build();
        }
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "Payroll status ('completed' or 'not_completed')")
        private final String status;

        @Schema(title = "Execution end timestamp of the payroll run for the period, if available")
        private final Instant executionEndDate;

        @Schema(title = "Whether the payroll for the given period is completed")
        private final Boolean completed;

        @Schema(title = "Payroll status payload returned by PayFit")
        private final PayrollStatus payrollStatus;
    }
}
