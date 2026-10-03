package io.kestra.plugin.payfit.model;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PayrollStatus extends PayfitPayload {
    private String status;
    private String executionEndDate;
}
