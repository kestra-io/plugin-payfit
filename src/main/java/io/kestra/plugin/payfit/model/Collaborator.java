package io.kestra.plugin.payfit.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Collaborator {
    private String id;
    private String firstName;
    private String lastName;
}
