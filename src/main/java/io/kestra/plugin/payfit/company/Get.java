package io.kestra.plugin.payfit.company;

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
    title = "Get a PayFit company",
    description = "Fetches `GET /companies/{companyId}`. No scope is required beyond a valid company token."
)
@Plugin(
    examples = {
        @Example(
            title = "Fetch the company profile",
            full = true,
            code = """
                id: payfit_company
                namespace: company.team

                tasks:
                  - id: company
                    type: io.kestra.plugin.payfit.company.Get
                    apiKey: "{{ secret('PAYFIT_API_KEY') }}"
                """
        )
    }
)
public class Get extends AbstractPayfitTask implements RunnableTask<Get.Output> {
    @Override
    public Output run(RunContext runContext) throws Exception {
        try (PayfitClient client = client(runContext)) {
            io.kestra.plugin.payfit.model.Company company = client.company();
            return Output.builder()
                .id(company.getId())
                .name(company.getName())
                .country(company.getCountry())
                .company(company)
                .build();
        }
    }

    private static String text(Map<String, Object> body, String key) {
        Object value = body.get(key);
        return value == null ? null : value.toString();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "Company id")
        private final String id;

        @Schema(title = "Company name")
        private final String name;

        @Schema(title = "Country code")
        private final String country;

        @Schema(title = "Company payload returned by PayFit")
        private final io.kestra.plugin.payfit.model.Company company;
    }
}
