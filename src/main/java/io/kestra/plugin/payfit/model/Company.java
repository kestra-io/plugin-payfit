package io.kestra.plugin.payfit.model;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class Company extends PayfitPayload {
    private String id;
    private String name;
    private String country;
    private String identificationNumber;
    private String address;
    private String city;
    private String postalCode;
    private Integer nbActiveContracts;
}
