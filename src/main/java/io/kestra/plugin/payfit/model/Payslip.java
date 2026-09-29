package io.kestra.plugin.payfit.model;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class Payslip extends PayfitPayload {
    private String id;
    private String payslipUrl;
}
