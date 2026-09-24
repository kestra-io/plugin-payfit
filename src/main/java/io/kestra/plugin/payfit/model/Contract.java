package io.kestra.plugin.payfit.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Contract {
    private String id;
    private String companyId;
    private String collaboratorId;
    private String status;
    private String jobName;
    private String startDate;
    private String endDate;
}
