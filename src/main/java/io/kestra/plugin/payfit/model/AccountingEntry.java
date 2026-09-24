package io.kestra.plugin.payfit.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AccountingEntry {
    private String operationDate;
    private String accountNumber;
    private String accountName;
    private Double debit;
    private Double credit;
    private List<AnalyticCode> analyticCodes;

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AnalyticCode {
        private String type;
        private String code;
    }
}
