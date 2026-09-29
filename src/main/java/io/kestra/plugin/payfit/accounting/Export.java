package io.kestra.plugin.payfit.accounting;

import java.net.URI;
import java.util.List;
import java.util.Map;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.payfit.AbstractPayfitTask;
import io.kestra.plugin.payfit.client.PayfitClient;
import io.kestra.plugin.payfit.client.PayfitException;
import io.kestra.plugin.payfit.client.PayfitConnections;
import io.kestra.plugin.payfit.client.PayfitValidators;
import io.kestra.plugin.payfit.client.StoredDocuments;
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
    title = "Export a PayFit accounting journal",
    description = "Fetches `GET /companies/{companyId}/accounting-v2?date=YYYYMM`. Available for French companies only. `date` must match `^2\\d{3}(0[1-9]|1[0-2])$`, for example `202612`. Requires the `accounting:read` scope."
)
@Plugin(
    examples = {
        @Example(
            title = "Export the accounting journal for a payroll month",
            full = true,
            code = """
                id: payfit_accounting
                namespace: company.team

                tasks:
                  - id: journal
                    type: io.kestra.plugin.payfit.accounting.Export
                    apiKey: "{{ secret('PAYFIT_API_KEY') }}"
                    date: "202612"
                """
        )
    }
)
public class Export extends AbstractPayfitTask implements RunnableTask<Export.Output> {
    @Schema(title = "Payroll period in `YYYYMM` format")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> date;

    @Override
    public Output run(RunContext runContext) throws Exception {
        String rDate = PayfitValidators.accountingPeriod(PayfitConnections.required(runContext, this.date, "date"));
        try (PayfitClient client = client(runContext)) {
            client.requireCountry("FR");
            java.util.List<io.kestra.plugin.payfit.model.AccountingEntry> rEntries = client.readList(
                client.companyPath("/accounting-v2"),
                Map.of("date", rDate),
                io.kestra.plugin.payfit.model.AccountingEntry.class
            );
            URI uri = StoredDocuments.storeJson(runContext, rEntries, "payfit-accounting-" + rDate + ".json");
            return Output.builder().uri(uri).build();
        }
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "Internal storage URI of the journal JSON")
        private final URI uri;
    }
}
