package io.kestra.plugin.payfit.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Introspection {
    private Boolean active;
    private String scope;

    @JsonProperty("company_id")
    private String companyId;

    @JsonProperty("token_type")
    private String tokenType;

    @JsonProperty("client_id")
    private String clientId;
}
