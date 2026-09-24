package io.kestra.plugin.payfit.auth;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.payfit.AbstractPayfitTask;
import io.kestra.plugin.payfit.client.PayfitClient;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Inspect a PayFit token",
    description = "Calls `POST /introspect` on the PayFit OAuth host and returns the company id, scopes, and whether the API key or access token is active."
)
@Plugin(
    examples = {
        @Example(
            title = "Resolve the company id for an API key",
            full = true,
            code = """
                id: payfit_introspect
                namespace: company.team

                tasks:
                  - id: introspect
                    type: io.kestra.plugin.payfit.auth.Introspect
                    apiKey: "{{ secret('PAYFIT_API_KEY') }}"
                """
        )
    }
)
public class Introspect extends AbstractPayfitTask implements RunnableTask<Introspect.Output> {
    @Override
    public Output run(RunContext runContext) throws Exception {
        try (PayfitClient client = client(runContext)) {
            Map<String, Object> body = client.introspect();
            String scope = body.get("scope") == null ? null : body.get("scope").toString();
            List<String> scopes = scope == null || scope.isBlank()
                ? List.of()
                : Arrays.stream(scope.split(" ")).filter(value -> !value.isBlank()).toList();
            return Output.builder()
                .companyId(body.get("company_id") == null ? null : body.get("company_id").toString())
                .active(Boolean.TRUE.equals(body.get("active")) || "true".equalsIgnoreCase(String.valueOf(body.get("active"))))
                .scope(scope)
                .scopes(scopes)
                .tokenType(body.get("token_type") == null ? null : body.get("token_type").toString())
                .clientId(body.get("client_id") == null ? null : body.get("client_id").toString())
                .body(body)
                .build();
        }
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "Company id bound to the token")
        private final String companyId;

        @Schema(title = "Whether the token is active")
        private final boolean active;

        @Schema(title = "Space-separated scope string returned by PayFit")
        private final String scope;

        @Schema(title = "Scopes split from the introspection response")
        private final List<String> scopes;

        @Schema(title = "Token type, usually `bearer`")
        private final String tokenType;

        @Schema(title = "OAuth client id, when the token is a partner token")
        private final String clientId;

        @Schema(title = "Raw introspection payload")
        private final Map<String, Object> body;
    }
}
