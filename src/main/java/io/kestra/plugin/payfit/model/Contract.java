package io.kestra.plugin.payfit.model;

import com.fasterxml.jackson.annotation.JsonAlias;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class Contract extends PayfitPayload {
    @JsonAlias("contractId")
    private String id;
    private String companyId;
    private String collaboratorId;
    private String status;
    private String jobName;
    private String startDate;
    private String endDate;
}
