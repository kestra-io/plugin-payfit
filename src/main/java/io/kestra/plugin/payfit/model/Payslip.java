package io.kestra.plugin.payfit.model;

import com.fasterxml.jackson.annotation.JsonAlias;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class Payslip extends PayfitPayload {
    @JsonAlias("payslipId")
    private String id;
    private String payslipUrl;
    private String contractId;
    private String year;
    private String month;
}
