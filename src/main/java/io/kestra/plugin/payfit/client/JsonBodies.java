package io.kestra.plugin.payfit.client;

import java.util.LinkedHashMap;
import java.util.Map;

public final class JsonBodies {
    private JsonBodies() {
    }

    /**
     * Copies {@code extra} and then overlays non-null entries from {@code explicit}.
     */
    public static Map<String, Object> merge(Map<String, Object> extra, Map<String, Object> explicit) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (extra != null) {
            extra.forEach((key, value) -> {
                if (value != null) {
                    body.put(key, value);
                }
            });
        }
        if (explicit != null) {
            explicit.forEach((key, value) -> {
                if (value != null) {
                    body.put(key, value);
                }
            });
        }
        return body;
    }

    public static String firstId(Map<String, Object> body) {
        if (body == null) {
            return null;
        }
        for (String key : new String[]{"id", "collaboratorId", "contractId", "absenceId", "payslipId"}) {
            Object value = body.get(key);
            if (value != null && !value.toString().isBlank()) {
                return value.toString();
            }
        }
        return null;
    }
}
