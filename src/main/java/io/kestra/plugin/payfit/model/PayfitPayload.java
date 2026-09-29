package io.kestra.plugin.payfit.model;

import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Keeps every PayFit field. Typed properties are the documented ones; anything else stays in the map
 * so list tasks and trigger versions do not drop fields the model does not name.
 */
@Getter
@NoArgsConstructor
public abstract class PayfitPayload {
    @Getter(AccessLevel.NONE)
    private final Map<String, Object> additionalProperties = new LinkedHashMap<>();

    @JsonAnySetter
    public void putAdditionalProperty(String name, Object value) {
        additionalProperties.put(name, value);
    }

    @JsonAnyGetter
    public Map<String, Object> getAdditionalProperties() {
        return additionalProperties;
    }
}
