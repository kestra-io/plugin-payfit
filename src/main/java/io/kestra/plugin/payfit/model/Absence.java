package io.kestra.plugin.payfit.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class Absence extends PayfitPayload {
    private String id;
    private String contractId;
    private String type;
    private String status;
    private DateAndMoment startDate;
    private DateAndMoment endDate;

    /**
     * PayFit represents an absence boundary as `{date, moment}`, not as a plain date string.
     */
    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DateAndMoment {
        private String date;
        private String moment;
    }
}
