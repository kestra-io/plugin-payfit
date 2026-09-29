package io.kestra.plugin.payfit.model;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class Absence extends PayfitPayload {
    private String id;
    private String contractId;
    private String type;
    private String status;
    private String startDate;
    private String endDate;
}
