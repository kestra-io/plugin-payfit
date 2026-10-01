# PayFit

Orchestrate PayFit HR and payroll data from Kestra. The plugin calls the [PayFit Partner API](https://developers.payfit.io/) at `https://partner-api.payfit.com` and the OAuth host at `https://oauth.payfit.com`.

## Authentication

Customer API keys and partner access tokens are both sent as `Authorization: Bearer <token>`.

- Create a customer key in the PayFit app at **Integrations → API Access** and store it in a Kestra secret. A key can only read the company that issued it.
- Partners exchange the OAuth authorization code with `auth.AccessToken` (`POST /token`). The access token is valid for the company that approved the integration. The task returns it as an encrypted output, so the bearer token is not stored in plain text on the execution. Kestra encrypts that output only when `kestra.encryption.secret-key` is configured. Without that key, the execution output contains the token in clear text.
- Leave `companyId` empty to resolve it from `POST /introspect`. `auth.Introspect` returns the company id, scopes, and whether the token is active.

Scopes follow the PayFit documentation. Collaborator reads need `collaborators:read`, collaborator and contract creation need `collaborators:write`, contract reads need `contracts:read`, payslips need `contracts:payslips:read`, accounting needs `accounting:read`, and absences need `time:read` or `time:write`.

## Tasks

| Task | API |
| --- | --- |
| `auth.Introspect` | `POST https://oauth.payfit.com/introspect` |
| `auth.AccessToken` | `POST https://oauth.payfit.com/token` |
| `company.Get` | `GET /companies/{companyId}` |
| `collaborators.List` | `GET /companies/{companyId}/collaborators` |
| `collaborators.Get` | `GET /companies/{companyId}/collaborators/{collaboratorId}` |
| `collaborators.Create` | `POST /companies/{companyId}/collaborators` |
| `contracts.List` | `GET /companies/{companyId}/contracts` |
| `contracts.Get` | `GET /companies/{companyId}/contracts/{contractId}` |
| `contracts.Create` | `POST /companies/{companyId}/collaborators/{collaboratorId}/contracts` |
| `absences.List` | `GET /companies/{companyId}/absences` |
| `absences.Create` | `POST /companies/{companyId}/absences` |
| `absences.Cancel` | `DELETE /companies/{companyId}/absences/{absenceId}` |
| `payslips.List` | `GET /companies/{companyId}/collaborators/{collaboratorId}/payslips` |
| `accounting.Export` | `GET /companies/{companyId}/accounting-v2?date=YYYYMM` |
| `payslips.Download` | `GET /companies/{companyId}/collaborators/{collaboratorId}/contracts/{contractId}/payslips/{payslipId}` |

List tasks follow `nextPageToken` until the API is exhausted. PayFit allows at most 50 items per page. `maxPages` defaults to 100 and, when reached, the output keeps the next token so a later task can resume. `fetchType` controls the result: `FETCH` returns the rows, `FETCH_ONE` returns the first row, `STORE` writes an ION file, and `NONE` returns only the count.

A collaborator created through the API has no contract yet and does not appear in the PayFit app until `contracts.Create` initializes one. Contract creation is available for French companies only. The task reads the company first and stops when `country` is not `FR`. PayFit company countries are `FR`, `ES`, and `GB`. The create-contract call returns HTTP 201 with an empty body, and the contract can take 2 to 5 minutes to become readable. An administrator still completes the profile in PayFit before payslips can be produced.

Absence creation sends `startDate` and `endDate` as `{date, moment}` objects. Cancellation is `DELETE /companies/{companyId}/absences/{absenceId}` and returns 204. Absences cannot be updated.

`accounting.Export` calls the French accounting v2 endpoint and also stops locally unless the company country is `FR`. The payroll period matches `^2\d{3}(0[1-9]|1[0-2])$`, for example `202612`. `payslips.Download` stores the PDF returned by PayFit. List payslips first when the payslip id is not already known. Absence types are the country codes published by PayFit (`fr_`, `es_`, and `uk_`). A British company is `GB` in the company payload and uses the `uk_` absence types.

Requests that return 408, 425, 429, or a retryable 5xx are retried with exponential backoff. A `Retry-After` header is honored for up to five seconds. Other 4xx responses fail immediately with the PayFit status and response body.

## Triggers

`collaborators.Trigger` and `absences.Trigger` poll the corresponding list endpoints. The interval must be at least `PT30S`. The first poll records the current resources and does not start an execution unless `fireOnInitial` is true. Later polls use `on` (`CREATE`, `UPDATE`, or `CREATE_OR_UPDATE`) and keep that snapshot in a namespace KV key.

`webhook.Webhook` receives PayFit's Svix webhook posts. Set `secret` to the `whsec_...` signing secret. The trigger checks `svix-id`, `svix-timestamp`, and `svix-signature`, and rejects timestamps older than five minutes. Set `eventType` to ignore payloads whose `type` does not match; those requests return HTTP 204 so PayFit does not retry them.

## Example

```yaml
id: payfit_onboarding
namespace: company.team

tasks:
  - id: collaborator
    type: io.kestra.plugin.payfit.collaborators.Create
    apiKey: "{{ secret('PAYFIT_API_KEY') }}"
    firstName: Ada
    lastName: Lovelace
    personalEmail: ada@example.com

  - id: contract
    type: io.kestra.plugin.payfit.contracts.Create
    apiKey: "{{ secret('PAYFIT_API_KEY') }}"
    collaboratorId: "{{ outputs.collaborator.id }}"
    jobTitle: Software Engineer
    startDate: "2026-01-06"
```
