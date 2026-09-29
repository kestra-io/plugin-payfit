package io.kestra.plugin.payfit.model;

import java.util.List;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class AccountingEntry extends PayfitPayload {
    private String operationDate;
    private String accountNumber;
    private String accountName;
    private Double debit;
    private Double credit;
    private List<AnalyticCode> analyticCodes;

    @Getter
    @NoArgsConstructor
    public static class AnalyticCode extends PayfitPayload {
        private String type;
        private String code;
    }
}
