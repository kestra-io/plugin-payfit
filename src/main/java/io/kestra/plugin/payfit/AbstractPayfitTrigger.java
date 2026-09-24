package io.kestra.plugin.payfit;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Optional;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.exceptions.InvalidTriggerConfigurationException;
import io.kestra.core.http.client.configurations.HttpConfiguration;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.PollingTriggerInterface;
import io.kestra.core.models.triggers.StatefulTriggerInterface;
import io.kestra.core.models.triggers.TriggerContext;
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
public abstract class AbstractPayfitTrigger extends AbstractTrigger implements PollingTriggerInterface, StatefulTriggerInterface {
    static final Duration MIN_INTERVAL = Duration.ofSeconds(30);

    @Schema(
        title = "API key",
        description = "PayFit API key or OAuth access token, sent as a bearer token. Store this value in a Kestra secret."
    )
    @NotNull
    @PluginProperty(secret = true, group = "connection")
    private Property<String> apiKey;

    @Schema(
        title = "Company ID",
        description = "PayFit company identifier. When omitted, the trigger resolves it through token introspection."
    )
    private Property<String> companyId;

    @Schema(
        title = "Partner API base URL",
        description = "Base URL of the PayFit Partner API. Defaults to `https://partner-api.payfit.com`."
    )
    @Builder.Default
    private Property<String> baseUrl = Property.ofValue(PayfitClient.DEFAULT_BASE_URL);

    @Schema(
        title = "OAuth base URL",
        description = "Base URL used for token introspection. Defaults to `https://oauth.payfit.com`."
    )
    @Builder.Default
    private Property<String> oauthUrl = Property.ofValue(PayfitClient.DEFAULT_OAUTH_URL);

    @Schema(
        title = "HTTP client options",
        description = "Optional HTTP client configuration applied to PayFit requests."
    )
    private HttpConfiguration options;

    @Schema(
        title = "Polling interval",
        description = "Interval between PayFit polls. Minimum `PT30S`. Defaults to `PT5M`."
    )
    @NotNull
    @Builder.Default
    private final Duration interval = Duration.ofMinutes(5);

    @Schema(
        title = "Trigger event type",
        description = "When to fire. `CREATE` starts new executions for resources that were not in the previous snapshot. Defaults to `CREATE`."
    )
    @Builder.Default
    private Property<On> on = Property.ofValue(On.CREATE);

    @Schema(
        title = "State key",
        description = "KV key used to store the resources already seen. Defaults to `<namespace>_<flowId>_<triggerId>`."
    )
    private Property<String> stateKey;

    @Schema(
        title = "State TTL",
        description = "Age of an individual resource version after which it is treated as new again. The snapshot key itself is kept, so an expired watermark still fires"
    )
    private Property<Duration> stateTtl;

    @Schema(
        title = "Fire on the initial snapshot",
        description = "When `false` (the default), the first poll records the current PayFit resources and does not start an execution. Later polls fire only for resources that match `on`."
    )
    @Builder.Default
    private Property<Boolean> fireOnInitial = Property.ofValue(false);

    protected PayfitClient client(RunContext runContext) throws IllegalVariableEvaluationException {
        return PayfitConnections.open(runContext, apiKey, companyId, baseUrl, oauthUrl, options);
    }

    @Override
    public ZonedDateTime nextEvaluationDate() throws InvalidTriggerConfigurationException {
        validateInterval();
        return PollingTriggerInterface.super.nextEvaluationDate();
    }

    @Override
    public ZonedDateTime nextEvaluationDate(ConditionContext conditionContext, Optional<? extends TriggerContext> last) throws InvalidTriggerConfigurationException {
        validateInterval();
        return PollingTriggerInterface.super.nextEvaluationDate(conditionContext, last);
    }

    private void validateInterval() throws InvalidTriggerConfigurationException {
        if (interval == null || interval.compareTo(MIN_INTERVAL) < 0) {
            throw new InvalidTriggerConfigurationException("PayFit polling interval must be at least PT30S");
        }
    }
}
