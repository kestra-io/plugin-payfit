package io.kestra.plugin.payfit.model;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class Contract extends PayfitPayload {
    private String id;
    private String companyId;
    private String collaboratorId;
    private String status;
    private String jobName;
    private String startDate;
    private String endDate;
}
