package io.kestra.plugin.payfit.model;

import java.util.Map;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class Collaborator extends PayfitPayload {
    private String id;
    private String firstName;
    private String lastName;
    private String otherName;
    private String personalEmail;
    private String socialSecurityNumber;
    private String personalPhoneNumber;
    private String phone;
    private String gender;
    private Integer numberOfChildren;
    private String matricule;
    private Map<String, Object> personalAddress;
    private Map<String, Object> birthInformation;
    private Object emails;
    private Object contracts;
}
