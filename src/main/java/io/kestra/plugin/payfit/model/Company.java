package io.kestra.plugin.payfit.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Company {
    private String id;
    private String name;
    private String country;
    private String identificationNumber;
    private String address;
    private String city;
    private String postalCode;
    private Integer nbActiveContracts;
}
