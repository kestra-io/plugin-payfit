package io.kestra.plugin.payfit;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.http.client.configurations.HttpConfiguration;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.payfit.client.PayfitClient;
import io.kestra.plugin.payfit.client.PayfitConnections;
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
public abstract class AbstractPayfitTask extends Task {
    @Schema(
        title = "API key",
        description = "PayFit API key or OAuth access token, sent as a bearer token. Create a customer key in the PayFit app under Integrations > API Access, or use the access token returned by `auth.AccessToken`. Store this value in a Kestra secret."
    )
    @NotNull
    @PluginProperty(secret = true, group = "connection")
    @ToString.Exclude
    private Property<String> apiKey;

    @Schema(
        title = "Company ID",
        description = "PayFit company identifier used in `/companies/{companyId}` paths. When omitted, the task calls `POST https://oauth.payfit.com/introspect` and uses `company_id` from the token."
    )
    @PluginProperty(group = "connection")
    private Property<String> companyId;

    @Schema(
        title = "Partner API base URL",
        description = "Base URL of the PayFit Partner API. Defaults to `https://partner-api.payfit.com`."
    )
    @Builder.Default
    @PluginProperty(group = "connection")
    private Property<String> baseUrl = Property.ofValue(PayfitClient.DEFAULT_BASE_URL);

    @Schema(
        title = "OAuth base URL",
        description = "Base URL used for token introspection and authorization-code exchange. Defaults to `https://oauth.payfit.com`."
    )
    @Builder.Default
    @PluginProperty(group = "connection")
    private Property<String> oauthUrl = Property.ofValue(PayfitClient.DEFAULT_OAUTH_URL);

    @Schema(
        title = "HTTP client options",
        description = "Optional HTTP client configuration applied to PayFit requests."
    )
    private HttpConfiguration options;

    protected PayfitClient client(RunContext runContext) throws IllegalVariableEvaluationException {
        return PayfitConnections.open(runContext, apiKey, companyId, baseUrl, oauthUrl, options);
    }
}
