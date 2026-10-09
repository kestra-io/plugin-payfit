package io.kestra.plugin.payfit.webhook;

import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.core.type.TypeReference;

import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.HttpResponse;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.triggers.TriggerOutput;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.plugin.core.trigger.AbstractWebhookTrigger;
import io.kestra.plugin.core.trigger.WebhookContext;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import reactor.core.publisher.Mono;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Receive PayFit webhook events",
    description = "Starts an execution when PayFit posts a Svix webhook. `secret` is the Svix signing secret (`whsec_...`) and is required. The request must include `svix-id`, `svix-timestamp`, and `svix-signature`, and the timestamp must be within five minutes. Events whose `type` does not match `eventType` return HTTP 204."
)
@Plugin(
    examples = {
        @Example(
            title = "Receive collaborator events",
            full = true,
            code = """
                id: payfit_webhook
                namespace: company.team

                tasks:
                  - id: log
                    type: io.kestra.plugin.core.log.Log
                    message: "PayFit event {{ trigger.eventType }}"

                triggers:
                  - id: payfit
                    type: io.kestra.plugin.payfit.webhook.Webhook
                    key: "{{ secret('PAYFIT_WEBHOOK_KEY') }}"
                    secret: "{{ secret('PAYFIT_SVIX_SECRET') }}"
                """
        )
    }
)
public class Webhook extends AbstractWebhookTrigger implements TriggerOutput<Webhook.Output> {
    @Schema(
        title = "Svix signing secret",
        description = "Required Svix signing secret (`whsec_...`). The request is verified with the signature over `svix-id.svix-timestamp.body`. Requests without a valid signature are rejected."
    )
    @NotNull
    @PluginProperty(secret = true, group = "connection")
    @ToString.Exclude
    private Property<String> secret;

    @Schema(
        title = "Event type to accept",
        description = "When set, only JSON bodies whose `event`, `eventType`, `type`, or `name` equals this value start an execution."
    )
    @PluginProperty(group = "processing")
    private Property<String> eventType;

    @Override
    public Mono<HttpResponse<?>> evaluate(WebhookContext context) throws Exception {
        if (context.path() != null || context.request().getUri().getPath().endsWith("/")) {
            return Mono.just(HttpResponse.of(HttpResponse.Status.NOT_FOUND));
        }
        RunContext runContext = context.webhookService().runContext(context.flow(), this);
        String expectedSecret = secret == null ? null : runContext.render(secret).as(String.class).orElse(null);
        String raw = rawBody(context.request());
        if (!WebhookRequests.svix(
            expectedSecret,
            header(context.request(), "svix-id"),
            header(context.request(), "svix-timestamp"),
            header(context.request(), "svix-signature"),
            raw == null ? "" : raw,
            java.time.Instant.now()
        )) {
            return Mono.just(HttpResponse.of(HttpResponse.Status.UNAUTHORIZED));
        }

        Object body = parse(raw);
        String expectedEvent = eventType == null ? null : runContext.render(eventType).as(String.class).orElse(null);
        if (!WebhookRequests.matches(body, expectedEvent)) {
            return Mono.just(HttpResponse.of(HttpResponse.Status.NO_CONTENT));
        }

        Output output = Output.builder()
            .body(body)
            .eventType(WebhookRequests.eventType(body))
            .headers(context.request().getHeaders() == null ? Map.of() : context.request().getHeaders().map())
            .parameters(context.webhookService().parseParameters(context))
            .build();
        Optional<Execution> maybeExecution = context.webhookService().newExecution(context, context.flow(), this, output);
        if (maybeExecution.isEmpty()) {
            return Mono.just(HttpResponse.of(HttpResponse.Status.CONFLICT));
        }
        Execution execution = maybeExecution.get();
        return context.webhookService().startExecution(execution)
            .then(Mono.fromCallable(() -> HttpResponse.of(context.webhookService().executionResponse(execution))));
    }

    private static String header(HttpRequest request, String name) {
        if (request.getHeaders() == null || name == null) {
            return null;
        }
        return request.getHeaders().firstValue(name).orElse(null);
    }

    private static String rawBody(HttpRequest request) {
        if (request.getBody() == null || request.getBody().getContent() == null) {
            return null;
        }
        Object content = request.getBody().getContent();
        return content instanceof String string ? string : content.toString();
    }

    private static Object parse(String body) {
        if (body == null || body.isBlank()) {
            return Map.of();
        }
        try {
            return JacksonMapper.ofJson().readValue(body, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception ignored) {
            return body;
        }
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "Parsed JSON body, or the raw text when the body is not a JSON object")
        private final Object body;

        @Schema(title = "Event type taken from `event`, `eventType`, `type`, or `name`")
        private final String eventType;

        @Schema(title = "Request headers")
        private final Map<String, java.util.List<String>> headers;

        @Schema(title = "Query parameters")
        private final Map<String, java.util.List<String>> parameters;
    }
}
