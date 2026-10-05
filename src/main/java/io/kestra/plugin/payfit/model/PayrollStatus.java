package io.kestra.plugin.payfit.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PayrollStatus extends PayfitPayload {
    @Schema(title = "Payroll status", description = "Either completed or not_completed.")
    private String status;

    @Schema(title = "Execution end date", description = "ISO-8601 timestamp, null while the payroll is not completed.")
    private String executionEndDate;
}
