package io.kestra.plugin.payfit.client;

import java.util.Map;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.http.client.configurations.HttpConfiguration;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;

public final class PayfitConnections {
    private PayfitConnections() {
    }

    public static PayfitClient open(
        RunContext runContext,
        Property<String> apiKey,
        Property<String> companyId,
        Property<String> baseUrl,
        Property<String> oauthUrl,
        HttpConfiguration options
    ) throws IllegalVariableEvaluationException {
        return new PayfitClient(
            runContext,
            required(runContext, apiKey, "apiKey"),
            optional(runContext, companyId),
            optional(runContext, baseUrl),
            optional(runContext, oauthUrl),
            options
        );
    }

    public static String required(RunContext runContext, Property<String> property, String name) throws IllegalVariableEvaluationException {
        String value = optional(runContext, property);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }

    public static String optional(RunContext runContext, Property<String> property) throws IllegalVariableEvaluationException {
        if (property == null) {
            return null;
        }
        return runContext.render(property).as(String.class).filter(value -> !value.isBlank()).orElse(null);
    }

    public static Integer optionalInt(RunContext runContext, Property<Integer> property) throws IllegalVariableEvaluationException {
        if (property == null) {
            return null;
        }
        return runContext.render(property).as(Integer.class).orElse(null);
    }

    public static Boolean optionalBoolean(RunContext runContext, Property<Boolean> property) throws IllegalVariableEvaluationException {
        if (property == null) {
            return null;
        }
        return runContext.render(property).as(Boolean.class).orElse(null);
    }

    public static boolean bool(RunContext runContext, Property<Boolean> property, boolean defaultValue) throws IllegalVariableEvaluationException {
        Boolean value = optionalBoolean(runContext, property);
        return value == null ? defaultValue : value;
    }

    public static int integer(RunContext runContext, Property<Integer> property, int defaultValue) throws IllegalVariableEvaluationException {
        Integer value = optionalInt(runContext, property);
        return value == null ? defaultValue : value;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> optionalMap(RunContext runContext, Property<Map<String, Object>> property) throws IllegalVariableEvaluationException {
        if (property == null) {
            return null;
        }
        return runContext.render(property).asMap(String.class, Object.class);
    }
}
