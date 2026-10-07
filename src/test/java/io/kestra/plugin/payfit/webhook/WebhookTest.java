package io.kestra.plugin.payfit.webhook;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.HttpResponse;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.GenericFlow;
import io.kestra.core.models.property.Property;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.services.WebhookService;
import io.kestra.core.utils.Await;
import io.kestra.core.utils.IdUtils;
import io.kestra.plugin.core.log.Log;
import io.kestra.plugin.core.trigger.WebhookContext;
import io.kestra.plugin.core.trigger.WebhookResponse;

import jakarta.inject.Inject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@KestraTest(startRunner = true, startWorker = false, startWorkerController = false)
class WebhookTest {
    private static final String RAW_SECRET = "test-secret-key-for-svix-testing-1234567";
    private static final String SVIX_SECRET = "whsec_" + Base64.getEncoder().encodeToString(RAW_SECRET.getBytes(StandardCharsets.UTF_8));

    @Inject
    private WebhookService webhookService;

    @Inject
    private FlowRepositoryInterface flowRepository;

    @Inject
    private ExecutionRepositoryInterface executionRepository;

    private Flow flow;

    @BeforeEach
    void setUp() throws Exception {
        flow = Flow.builder()
            .id("payfit_webhook_flow")
            .namespace("io.kestra.plugin.payfit")
            .revision(1)
            .tasks(List.of(
                Log.builder()
                    .id("log")
                    .type(Log.class.getName())
                    .message(Property.ofValue("Received PayFit webhook"))
                    .build()
            ))
            .build();
        if (flowRepository.findById(null, flow.getNamespace(), flow.getId()).isEmpty()) {
            flowRepository.create(GenericFlow.of(flow));
        }
    }

    private HttpRequest createSvixRequest(String secret, String payload, Instant timestamp, String signatureOverride) throws Exception {
        String msgId = "msg_" + IdUtils.create();
        String ts = String.valueOf(timestamp.getEpochSecond());

        String signature;
        if (signatureOverride != null) {
            signature = signatureOverride;
        } else {
            String cleanSecret = secret.startsWith("whsec_") ? secret.substring("whsec_".length()) : secret;
            byte[] key = Base64.getDecoder().decode(cleanSecret);

            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            String toSign = msgId + "." + ts + "." + payload;
            signature = "v1," + Base64.getEncoder().encodeToString(mac.doFinal(toSign.getBytes(StandardCharsets.UTF_8)));
        }

        Map<String, List<String>> headers = Map.of(
            "svix-id", List.of(msgId),
            "svix-timestamp", List.of(ts),
            "svix-signature", List.of(signature),
            "content-type", List.of("application/json")
        );

        return HttpRequest.of(
            URI.create("/api/v1/executions/webhook/io.kestra.plugin.payfit/payfit_webhook_flow/payfit"),
            "POST",
            HttpRequest.StringRequestBody.of(payload),
            headers
        );
    }

    @Test
    void validSignedPayloadCreatesExecution() throws Exception {
        String payload = "{\"event\":\"collaborator.created\",\"collaboratorId\":\"collab_123\"}";
        HttpRequest request = createSvixRequest(SVIX_SECRET, payload, Instant.now(), null);

        Webhook trigger = Webhook.builder()
            .id("payfit")
            .type(Webhook.class.getName())
            .key("test-webhook-key")
            .secret(Property.ofValue(SVIX_SECRET))
            .build();

        WebhookContext context = WebhookContext.builder()
            .request(request)
            .flow(flow)
            .trigger(trigger)
            .webhookService(webhookService)
            .build();

        HttpResponse<?> response = trigger.evaluate(context).block();

        assertNotNull(response);
        assertEquals(HttpResponse.Status.OK, response.getStatus());
        assertNotNull(response.getBody());
        assertInstanceOf(WebhookResponse.class, response.getBody());

        WebhookResponse webhookResponse = (WebhookResponse) response.getBody();
        assertNotNull(webhookResponse.id());
        assertEquals(flow.getId(), webhookResponse.flowId());
        assertEquals(flow.getNamespace(), webhookResponse.namespace());

        Execution execution = Await.until(
            () -> executionRepository.findById(null, webhookResponse.id()).orElse(null),
            Duration.ofMillis(100),
            Duration.ofSeconds(10)
        );

        assertNotNull(execution);
        assertEquals(webhookResponse.id(), execution.getId());
        assertEquals(flow.getId(), execution.getFlowId());
        assertNotNull(execution.getTrigger());
        assertEquals("collaborator.created", execution.getTrigger().getVariables().get("eventType"));
    }

    @Test
    void validSignedPayloadMatchesConfiguredEventType() throws Exception {
        String payload = "{\"type\":\"collaborator.created\",\"collaboratorId\":\"collab_456\"}";
        HttpRequest request = createSvixRequest(SVIX_SECRET, payload, Instant.now(), null);

        Webhook trigger = Webhook.builder()
            .id("payfit")
            .type(Webhook.class.getName())
            .key("test-webhook-key")
            .secret(Property.ofValue(SVIX_SECRET))
            .eventType(Property.ofValue("collaborator.created"))
            .build();

        WebhookContext context = WebhookContext.builder()
            .request(request)
            .flow(flow)
            .trigger(trigger)
            .webhookService(webhookService)
            .build();

        HttpResponse<?> response = trigger.evaluate(context).block();

        assertNotNull(response);
        assertEquals(HttpResponse.Status.OK, response.getStatus());
        assertInstanceOf(WebhookResponse.class, response.getBody());
        WebhookResponse webhookResponse = (WebhookResponse) response.getBody();

        Execution execution = Await.until(
            () -> executionRepository.findById(null, webhookResponse.id()).orElse(null),
            Duration.ofMillis(100),
            Duration.ofSeconds(10)
        );
        assertNotNull(execution);
        assertEquals("collaborator.created", execution.getTrigger().getVariables().get("eventType"));
    }

    @Test
    void validSignedPayloadWithUnmatchedEventTypeReturnsNoContent() throws Exception {
        String payload = "{\"event\":\"absence.created\",\"absenceId\":\"abs_789\"}";
        HttpRequest request = createSvixRequest(SVIX_SECRET, payload, Instant.now(), null);

        Webhook trigger = Webhook.builder()
            .id("payfit")
            .type(Webhook.class.getName())
            .key("test-webhook-key")
            .secret(Property.ofValue(SVIX_SECRET))
            .eventType(Property.ofValue("collaborator.created"))
            .build();

        WebhookContext context = WebhookContext.builder()
            .request(request)
            .flow(flow)
            .trigger(trigger)
            .webhookService(webhookService)
            .build();

        HttpResponse<?> response = trigger.evaluate(context).block();

        assertNotNull(response);
        assertEquals(HttpResponse.Status.NO_CONTENT, response.getStatus());
    }

    @Test
    void invalidSignatureReturnsUnauthorized() throws Exception {
        String payload = "{\"event\":\"collaborator.created\"}";
        HttpRequest request = createSvixRequest(SVIX_SECRET, payload, Instant.now(), "v1,invalid_signature_hex");

        Webhook trigger = Webhook.builder()
            .id("payfit")
            .type(Webhook.class.getName())
            .key("test-webhook-key")
            .secret(Property.ofValue(SVIX_SECRET))
            .build();

        WebhookContext context = WebhookContext.builder()
            .request(request)
            .flow(flow)
            .trigger(trigger)
            .webhookService(webhookService)
            .build();

        HttpResponse<?> response = trigger.evaluate(context).block();

        assertNotNull(response);
        assertEquals(HttpResponse.Status.UNAUTHORIZED, response.getStatus());
    }

    @Test
    void staleTimestampReturnsUnauthorized() throws Exception {
        String payload = "{\"event\":\"collaborator.created\"}";
        Instant pastTimestamp = Instant.now().minusSeconds(400);
        HttpRequest request = createSvixRequest(SVIX_SECRET, payload, pastTimestamp, null);

        Webhook trigger = Webhook.builder()
            .id("payfit")
            .type(Webhook.class.getName())
            .key("test-webhook-key")
            .secret(Property.ofValue(SVIX_SECRET))
            .build();

        WebhookContext context = WebhookContext.builder()
            .request(request)
            .flow(flow)
            .trigger(trigger)
            .webhookService(webhookService)
            .build();

        HttpResponse<?> response = trigger.evaluate(context).block();

        assertNotNull(response);
        assertEquals(HttpResponse.Status.UNAUTHORIZED, response.getStatus());
    }
}
