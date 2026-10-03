# Kestra PayFit Plugin

## What

- Calls the PayFit Partner API from `io.kestra.plugin.payfit`.
- Includes `auth.Introspect`, `auth.AccessToken`, `company.Get`, `company.GetPayrollStatus`, collaborator and contract list/get/create tasks, absence list/create/cancel tasks, `accounting.Export`, `payslips.Download`, collaborator and absence polling triggers, and `webhook.Webhook`.

## Why

- HR and payroll actions need to run inside the same flow as hiring, leave approval, and month-end close.
- A customer API key or partner access token is enough to reach one company's PayFit data, including pagination and retries.
- Triggers start work when collaborators or absences change, or when PayFit posts a webhook.

## How

### Architecture

Single-module plugin. Source packages under `io.kestra.plugin.payfit`:

- `client` for the HTTP connection, pagination, and change detection
- `auth`, `company`, `collaborators`, `contracts`, `absences`, `accounting`, `payslips`, `webhook`

Infrastructure dependencies (Docker Compose services):

- `app`

### Key Plugin Classes

- `io.kestra.plugin.payfit.AbstractPayfitTask`
- `io.kestra.plugin.payfit.collaborators.List`
- `io.kestra.plugin.payfit.collaborators.Trigger`
- `io.kestra.plugin.payfit.absences.Trigger`
- `io.kestra.plugin.payfit.webhook.Webhook`

### Project Structure

```
plugin-payfit/
├── src/main/java/io/kestra/plugin/payfit/
├── src/test/java/io/kestra/plugin/payfit/
├── build.gradle
└── README.md
```

## Local rules

- Base the wording on the implemented packages and classes, not on template README text.

## References

- https://kestra.io/docs/plugin-developer-guide
- https://kestra.io/docs/plugin-developer-guide/contribution-guidelines
