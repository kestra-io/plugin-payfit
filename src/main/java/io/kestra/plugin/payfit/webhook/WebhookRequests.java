package io.kestra.plugin.payfit.webhook;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class WebhookRequests {
    private WebhookRequests() {
    }

    public static boolean svix(String secret, String id, String timestamp, String signature, String body, Instant now) {
        if (secret == null || secret.isBlank()) {
            return false;
        }
        if (id == null || timestamp == null || signature == null || body == null) {
            return false;
        }
        long sent;
        try {
            sent = Long.parseLong(timestamp);
        } catch (NumberFormatException e) {
            return false;
        }
        if (Math.abs(now.getEpochSecond() - sent) > 300) {
            return false;
        }
        byte[] key = decodeSecret(secret);
        if (key == null) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            String expected = "v1," + Base64.getEncoder().encodeToString(
                mac.doFinal((id + "." + timestamp + "." + body).getBytes(StandardCharsets.UTF_8))
            );
            for (String candidate : signature.split(" ")) {
                if (MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), candidate.getBytes(StandardCharsets.UTF_8))) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private static byte[] decodeSecret(String secret) {
        String encoded = secret.startsWith("whsec_") ? secret.substring("whsec_".length()) : secret;
        try {
            return Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static String eventType(Object body) {
        if (!(body instanceof Map<?, ?> map)) {
            return null;
        }
        for (String key : new String[]{"event", "eventType", "type", "name"}) {
            Object value = map.get(key);
            if (value != null && !value.toString().isBlank()) {
                return value.toString();
            }
        }
        return null;
    }

    public static boolean matches(Object body, String expectedType) {
        if (expectedType == null || expectedType.isBlank()) {
            return true;
        }
        return expectedType.equals(eventType(body));
    }
}
