package io.kestra.plugin.payfit.auth;

import java.util.LinkedHashMap;
import java.util.Map;

import io.kestra.core.http.client.configurations.HttpConfiguration;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.payfit.client.JsonBodies;
import io.kestra.plugin.payfit.client.PayfitClient;
import io.kestra.plugin.payfit.client.PayfitConnections;
import io.kestra.plugin.payfit.client.PayfitValidators;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
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
    title = "Exchange a PayFit authorization code",
    description = "Exchanges an OAuth 2.0 authorization code at `POST https://oauth.payfit.com/token` using `application/x-www-form-urlencoded`. No API key is sent. The access token is valid for the company that approved the integration."
)
@Plugin(
    examples = {
        @Example(
            title = "Exchange a partner authorization code",
            full = true,
            code = """
                id: payfit_access_token
                namespace: company.team

                tasks:
                  - id: token
                    type: io.kestra.plugin.payfit.auth.AccessToken
                    clientId: "{{ secret('PAYFIT_CLIENT_ID') }}"
                    clientSecret: "{{ secret('PAYFIT_CLIENT_SECRET') }}"
                    code: "{{ inputs.code }}"
                    redirectUri: "https://example.com/payfit/callback"
                """
        )
    }
)
public class AccessToken extends Task implements RunnableTask<AccessToken.Output> {
    @Schema(title = "OAuth client id")
    @NotNull
    @PluginProperty(group = "connection")
    private Property<String> clientId;

    @Schema(title = "OAuth client secret")
    @NotNull
    @PluginProperty(secret = true, group = "connection")
    @ToString.Exclude
    private Property<String> clientSecret;

    @Schema(title = "Authorization code returned to the redirect URI")
    @NotNull
    @PluginProperty(secret = true, group = "connection")
    @ToString.Exclude
    private Property<String> code;

    @Schema(title = "Redirect URI used when the authorization code was issued")
    @NotNull
    @PluginProperty(group = "connection")
    private Property<String> redirectUri;

    @Schema(title = "OAuth grant type. Defaults to `authorization_code`")
    @Builder.Default
    @PluginProperty(group = "connection")
    private Property<String> grantType = Property.ofValue("authorization_code");

    @Schema(title = "OAuth base URL. Defaults to `https://oauth.payfit.com`")
    @Builder.Default
    @PluginProperty(group = "connection")
    private Property<String> oauthUrl = Property.ofValue(PayfitClient.DEFAULT_OAUTH_URL);

    @Schema(title = "HTTP client options")
    private HttpConfiguration options;

    @Override
    public Output run(RunContext runContext) throws Exception {
        String rClientId = PayfitConnections.required(runContext, this.clientId, "clientId");
        String rClientSecret = PayfitConnections.required(runContext, this.clientSecret, "clientSecret");
        String rCode = PayfitConnections.required(runContext, this.code, "code");
        String rGrantType = PayfitConnections.optional(runContext, this.grantType);
        PayfitValidators.requiredText(rGrantType, "grantType");
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("client_id", rClientId);
        request.put("client_secret", rClientSecret);
        request.put("code", rCode);
        request.put("grant_type", rGrantType);
        request.put("redirect_uri", PayfitConnections.required(runContext, this.redirectUri, "redirectUri"));

        try (PayfitClient client = new PayfitClient(
            runContext,
            null,
            null,
            PayfitClient.DEFAULT_BASE_URL,
            PayfitConnections.optional(runContext, this.oauthUrl),
            options,
            false
        )) {
            Map<String, Object> body = client.accessToken(request);
            String accessToken = body.get("access_token") == null ? null : body.get("access_token").toString();
            PayfitValidators.requiredText(accessToken, "access_token");
            return Output.builder()
                .accessToken(accessToken)
                .tokenType(body.get("token_type") == null ? null : body.get("token_type").toString())
                .scope(body.get("scope") == null ? null : body.get("scope").toString())
                .companyId(body.get("company_id") == null ? JsonBodies.firstId(body) : body.get("company_id").toString())
                .expiresIn(body.get("expires_in") instanceof Number number ? number.longValue() : null)
                .build();
        }
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "Access token to send as a bearer token on Partner API requests")
        private final String accessToken;

        @Schema(title = "Token type")
        private final String tokenType;

        @Schema(title = "Granted scopes")
        private final String scope;

        @Schema(title = "Company id, when PayFit returns one with the token")
        private final String companyId;

        @Schema(title = "Lifetime in seconds, when PayFit returns `expires_in`")
        private final Long expiresIn;
    }
}
