package io.kestra.plugin.payfit.webhook;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebhookRequestsTest {
    @Test
    void blankSecretRejectsEveryRequest() {
        assertFalse(WebhookRequests.svix(null, "msg_1", "1700000000", "v1,anything", "{}", Instant.ofEpochSecond(1700000000)));
        assertFalse(WebhookRequests.svix("  ", "msg_1", "1700000000", "v1,anything", "{}", Instant.ofEpochSecond(1700000000)));
    }

    @Test
    void svixSignatureAcceptsTheMatchingHeaderAndRejectsStaleTimestamps() throws Exception {
        String body = "{\"type\":\"collaborator.created\"}";
        String secret = "whsec_" + Base64.getEncoder().encodeToString("top-secret".getBytes(StandardCharsets.UTF_8));
        String timestamp = "1700000000";
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("top-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = "v1," + Base64.getEncoder().encodeToString(
            mac.doFinal(("msg_1." + timestamp + "." + body).getBytes(StandardCharsets.UTF_8))
        );

        assertTrue(WebhookRequests.svix(secret, "msg_1", timestamp, signature, body, Instant.ofEpochSecond(1700000010)));
        assertFalse(WebhookRequests.svix(secret, "msg_1", timestamp, "v1,wrong", body, Instant.ofEpochSecond(1700000010)));
        assertFalse(WebhookRequests.svix(secret, "msg_1", timestamp, signature, body, Instant.ofEpochSecond(1700000401)));
    }

    @Test
    void eventTypeMatchesKnownFields() {
        assertEquals("collaborator.created", WebhookRequests.eventType(Map.of("event", "collaborator.created")));
        assertEquals("payroll.closed", WebhookRequests.eventType(Map.of("type", "payroll.closed")));
        assertTrue(WebhookRequests.matches(Map.of("eventType", "absence.approved"), "absence.approved"));
        assertFalse(WebhookRequests.matches(Map.of("event", "other"), "absence.approved"));
        assertTrue(WebhookRequests.matches("raw", null));
        assertFalse(WebhookRequests.matches("raw", "absence.approved"));
    }
}
