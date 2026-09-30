package io.kestra.plugin.payfit;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import io.kestra.core.exceptions.InvalidTriggerConfigurationException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.plugin.payfit.absences.Cancel;
import io.kestra.plugin.payfit.absences.Create;
import io.kestra.plugin.payfit.accounting.Export;
import io.kestra.plugin.payfit.auth.AccessToken;
import io.kestra.plugin.payfit.auth.Introspect;
import io.kestra.plugin.payfit.client.PayfitException;
import io.kestra.plugin.payfit.collaborators.List;
import io.kestra.plugin.payfit.company.Get;
import io.kestra.plugin.payfit.payslips.Download;

import jakarta.inject.Inject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@KestraTest
class PayfitPluginTest {
    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void followsRootAndMetaPageTokens() throws Exception {
        try (PayfitMockServer server = new PayfitMockServer()) {
            server.handler(request -> {
                if (request.query() == null || !request.query().contains("nextPageToken")) {
                    return PayfitMockServer.Response.json(200, "{\"collaborators\":[{\"id\":\"a\"}],\"nextPageToken\":\"page-2\"}");
                }
                if (request.query().contains("page-2")) {
                    return PayfitMockServer.Response.json(200, "{\"collaborators\":[{\"id\":\"b\"}],\"meta\":{\"nextPageToken\":\"page-3\"}}");
                }
                return PayfitMockServer.Response.json(200, "{\"data\":{\"collaborators\":[{\"id\":\"c\"}]}}");
            });

            List task = listTask(server).build();
            List.Output output = task.run(runContext());

            assertEquals(3, output.getCount());
            assertEquals(3, output.getPages());
            assertEquals(null, output.getNextPageToken());
            assertEquals(null, output.getUri());
            assertEquals(3, output.getCollaborators().size());
            assertEquals("Bearer secret", server.requests.getFirst().authorization());
            assertTrue(server.requests.getFirst().path().equals("/companies/company-1/collaborators"));
        }
    }

    @Test
    void stopsOnRepeatedTokenMaxPagesAndClientErrors() throws Exception {
        try (PayfitMockServer server = new PayfitMockServer()) {
            server.handler(request -> PayfitMockServer.Response.json(200, "{\"collaborators\":[{\"id\":\"a\"}],\"nextPageToken\":\"again\"}"));
            PayfitException repeated = assertThrows(PayfitException.class, () -> listTask(server).build().run(runContext()));
            assertTrue(repeated.getMessage().contains("repeated"));

            server.requests.clear();
            server.handler(request -> PayfitMockServer.Response.json(200, "{\"collaborators\":[{\"id\":\"a\"}],\"nextPageToken\":\"next\"}"));
            List.Output partial = listTask(server).maxPages(Property.ofValue(1)).build().run(runContext());
            assertEquals("next", partial.getNextPageToken());
            assertEquals(1, partial.getPages());

            server.requests.clear();
            server.handler(request -> PayfitMockServer.Response.json(401, "{\"message\":\"unauthorized\",\"payslipNet\":\"SECRET-PAY\"}"));
            PayfitException unauthorized = assertThrows(PayfitException.class, () -> listTask(server).build().run(runContext()));
            assertEquals(401, unauthorized.getStatusCode());
            assertTrue(unauthorized.getMessage().contains("Check the API key"));
            assertTrue(unauthorized.getMessage().contains("unauthorized"));
            assertFalse(unauthorized.getMessage().contains("SECRET-PAY"));
            assertEquals(1, server.requests.size());

            server.requests.clear();
            server.handler(request -> PayfitMockServer.Response.json(403, "{\"message\":\"forbidden\",\"iban\":\"FR-SECRET\"}"));
            PayfitException forbidden = assertThrows(PayfitException.class, () -> listTask(server).build().run(runContext()));
            assertEquals(403, forbidden.getStatusCode());
            assertTrue(forbidden.getMessage().contains("required PayFit scope"));
            assertTrue(forbidden.getMessage().contains("forbidden"));
            assertFalse(forbidden.getMessage().contains("FR-SECRET"));
            assertEquals(1, server.requests.size());
        }
    }

    @Test
    void retries429And500ThenSucceeds() throws Exception {
        try (PayfitMockServer server = new PayfitMockServer()) {
            AtomicInteger calls = new AtomicInteger();
            server.handler(request -> calls.getAndIncrement() == 0
                ? PayfitMockServer.Response.json(429, "{\"message\":\"slow down\"}")
                : PayfitMockServer.Response.json(200, "{\"id\":\"company-1\",\"name\":\"Acme\",\"country\":\"FR\"}"));

            Get.Output output = Get.builder()
                .apiKey(Property.ofValue("secret"))
                .companyId(Property.ofValue("company-1"))
                .baseUrl(Property.ofValue(server.baseUrl()))
                .build()
                .run(runContext());

            assertEquals("Acme", output.getName());
            assertEquals(2, server.requests.size());

            calls.set(0);
            server.requests.clear();
            server.handler(request -> calls.getAndIncrement() == 0
                ? PayfitMockServer.Response.json(500, "{\"message\":\"unavailable\"}")
                : PayfitMockServer.Response.json(200, "{\"id\":\"company-1\",\"name\":\"Acme\"}"));
            assertEquals("Acme", Get.builder()
                .apiKey(Property.ofValue("secret"))
                .companyId(Property.ofValue("company-1"))
                .baseUrl(Property.ofValue(server.baseUrl()))
                .build()
                .run(runContext())
                .getName());
        }
    }

    @Test
    void postRetries429ButNotServerErrors() throws Exception {
        try (PayfitMockServer server = new PayfitMockServer()) {
            AtomicInteger calls = new AtomicInteger();
            server.handler(request -> {
                assertEquals("POST", request.method());
                return calls.getAndIncrement() == 0
                    ? PayfitMockServer.Response.json(500, "{\"message\":\"unavailable\"}")
                    : PayfitMockServer.Response.json(201, "{\"id\":\"absence-1\"}");
            });
            assertThrows(PayfitException.class, () -> absence(server).run(runContext()));
            assertEquals(1, calls.get());

            calls.set(0);
            server.requests.clear();
            server.handler(request -> calls.getAndIncrement() == 0
                ? PayfitMockServer.Response.json(429, "{\"message\":\"slow down\"}")
                : PayfitMockServer.Response.json(201, "{\"id\":\"absence-1\"}"));
            assertEquals("absence-1", absence(server).run(runContext()).getId());
            assertEquals(2, calls.get());
        }
    }

    @Test
    void deleteRetriesServerErrors() throws Exception {
        try (PayfitMockServer server = new PayfitMockServer()) {
            AtomicInteger calls = new AtomicInteger();
            server.handler(request -> {
                assertEquals("DELETE", request.method());
                return calls.getAndIncrement() == 0
                    ? PayfitMockServer.Response.json(500, "{\"message\":\"unavailable\"}")
                    : new PayfitMockServer.Response(204, "", "application/json");
            });
            assertEquals(null, Cancel.builder()
                .apiKey(Property.ofValue("secret"))
                .companyId(Property.ofValue("company-1"))
                .baseUrl(Property.ofValue(server.baseUrl()))
                .absenceId(Property.ofValue("absence-1"))
                .build()
                .run(runContext()));
            assertEquals(2, calls.get());
        }
    }

    @Test
    void resolvesCompanyIdFromIntrospectionAndRejectsInactiveTokens() throws Exception {
        try (PayfitMockServer server = new PayfitMockServer()) {
            server.handler(request -> {
                if ("/introspect".equals(request.path())) {
                    assertEquals("Bearer secret", request.authorization());
                    assertTrue(request.body().contains("\"token\":\"secret\""));
                    return PayfitMockServer.Response.json(200, "{\"active\":true,\"company_id\":\"from-token\",\"scope\":\"collaborators:read accounting:read\",\"token_type\":\"bearer\"}");
                }
                assertEquals("/companies/from-token", request.path());
                return PayfitMockServer.Response.json(200, "{\"id\":\"from-token\",\"name\":\"Acme\",\"country\":\"FR\"}");
            });

            Introspect.Output introspected = Introspect.builder()
                .apiKey(Property.ofValue("secret"))
                .oauthUrl(Property.ofValue(server.baseUrl()))
                .build()
                .run(runContext());
            assertEquals("from-token", introspected.getCompanyId());
            assertEquals(2, introspected.getScopes().size());

            Get.Output company = Get.builder()
                .apiKey(Property.ofValue("secret"))
                .baseUrl(Property.ofValue(server.baseUrl()))
                .oauthUrl(Property.ofValue(server.baseUrl()))
                .build()
                .run(runContext());
            assertEquals("from-token", company.getId());

            server.handler(request -> PayfitMockServer.Response.json(200, "{\"active\":false}"));
            assertThrows(PayfitException.class, () -> Introspect.builder()
                .apiKey(Property.ofValue("secret"))
                .oauthUrl(Property.ofValue(server.baseUrl()))
                .build()
                .run(runContext()));
        }
    }

    @Test
    void exchangesAuthorizationCodeWithoutBearerToken() throws Exception {
        try (PayfitMockServer server = new PayfitMockServer()) {
            server.handler(request -> {
                assertEquals("POST", request.method());
                assertEquals("/token", request.path());
                assertEquals(null, request.authorization());
                assertTrue(request.body().contains("grant_type=authorization_code"));
                assertTrue(request.body().contains("client_secret=super-secret"));
                assertFalse(request.body().contains("oauth-exchange"));
                assertFalse(request.body().contains("{"));
                return PayfitMockServer.Response.json(200, "{\"access_token\":\"access\",\"token_type\":\"bearer\",\"company_id\":\"co\",\"expires_in\":3600}");
            });

            AccessToken.Output output = AccessToken.builder()
                .clientId(Property.ofValue("client"))
                .clientSecret(Property.ofValue("super-secret"))
                .code(Property.ofValue("code"))
                .redirectUri(Property.ofValue("https://example.com/callback"))
                .oauthUrl(Property.ofValue(server.baseUrl()))
                .build()
                .run(runContext());

            assertEquals("access", output.getAccessToken());
            assertEquals(3600L, output.getExpiresIn());
        }
    }

    @Test
    void createsAndCancelsAbsencesAndExportsAccountingAndPayslips() throws Exception {
        try (PayfitMockServer server = new PayfitMockServer()) {
            server.handler(request -> switch (request.path()) {
                case "/companies/company-1" -> PayfitMockServer.Response.json(200, "{\"id\":\"company-1\",\"name\":\"Acme\",\"country\":\"FR\",\"unexpected\":true}");
                case "/companies/company-1/absences" -> {
                    assertEquals("POST", request.method());
                    assertTrue(request.body().contains("\"startDate\":{\"date\":\"2026-12-24\",\"moment\":\"beginning-of-day\"}"));
                    assertTrue(request.body().contains("\"endDate\":{\"date\":\"2026-12-26\",\"moment\":\"end-of-day\"}"));
                    assertTrue(request.body().contains("\"type\":\"fr_conges_payes\""));
                    yield PayfitMockServer.Response.json(201, "{\"id\":\"absence-1\"}");
                }
                case "/companies/company-1/absences/absence-1" -> {
                    assertEquals("DELETE", request.method());
                    assertTrue(request.body().contains("\"comment\":\"no longer needed\""));
                    yield new PayfitMockServer.Response(204, "", "application/json");
                }
                case "/companies/company-1/collaborators/col-1/contracts" -> {
                    assertEquals("POST", request.method());
                    assertFalse(request.body().contains("collaboratorId"));
                    yield new PayfitMockServer.Response(201, "", "application/json");
                }
                case "/companies/company-1/collaborators" -> {
                    assertTrue(request.body().contains("\"personalEmail\":\"ada@example.com\""));
                    yield PayfitMockServer.Response.json(201, "{\"collaboratorId\":\"col-9\"}");
                }
                case "/companies/company-1/collaborators/col-1/payslips" -> PayfitMockServer.Response.json(200, "{\"payslips\":[{\"id\":\"slip-1\"}]}");
                case "/companies/company-1/accounting-v2" -> {
                    assertTrue(request.query().contains("date=202612"));
                    yield PayfitMockServer.Response.json(200, "[{\"operationDate\":\"2026-12-31\"}]");
                }
                case "/companies/company-1/collaborators/col-1/contracts/contract-1/payslips/slip-1" -> {
                    assertEquals("application/pdf", request.accept());
                    yield new PayfitMockServer.Response(200, "%PDF-1.4", "application/pdf");
                }
                default -> PayfitMockServer.Response.json(404, "{\"message\":\"missing\"}");
            });

            assertThrows(IllegalArgumentException.class, () -> Create.builder()
                .apiKey(Property.ofValue("secret"))
                .companyId(Property.ofValue("company-1"))
                .baseUrl(Property.ofValue(server.baseUrl()))
                .contractId(Property.ofValue("contract-1"))
                .absenceType(Property.ofValue("fr_conges_payes"))
                .startDate(Property.ofValue("2026-12-26"))
                .endDate(Property.ofValue("2026-12-24"))
                .build()
                .run(runContext()));

            Create.Output created = Create.builder()
                .apiKey(Property.ofValue("secret"))
                .companyId(Property.ofValue("company-1"))
                .baseUrl(Property.ofValue(server.baseUrl()))
                .contractId(Property.ofValue("contract-1"))
                .absenceType(Property.ofValue("fr_conges_payes"))
                .startDate(Property.ofValue("2026-12-24"))
                .endDate(Property.ofValue("2026-12-26"))
                .build()
                .run(runContext());
            assertEquals("absence-1", created.getId());

            assertEquals(null, Cancel.builder()
                .apiKey(Property.ofValue("secret"))
                .companyId(Property.ofValue("company-1"))
                .baseUrl(Property.ofValue(server.baseUrl()))
                .absenceId(Property.ofValue("absence-1"))
                .comment(Property.ofValue("no longer needed"))
                .build()
                .run(runContext()));

            io.kestra.plugin.payfit.contracts.Create.Output contract = io.kestra.plugin.payfit.contracts.Create.builder()
                .apiKey(Property.ofValue("secret"))
                .companyId(Property.ofValue("company-1"))
                .baseUrl(Property.ofValue(server.baseUrl()))
                .collaboratorId(Property.ofValue("col-1"))
                .jobTitle(Property.ofValue("Software Engineer"))
                .startDate(Property.ofValue("2026-01-06"))
                .build()
                .run(runContext());
            assertEquals(null, contract.getId());

            io.kestra.plugin.payfit.collaborators.Create.Output collaborator = io.kestra.plugin.payfit.collaborators.Create.builder()
                .apiKey(Property.ofValue("secret"))
                .companyId(Property.ofValue("company-1"))
                .baseUrl(Property.ofValue(server.baseUrl()))
                .firstName(Property.ofValue("Ada"))
                .lastName(Property.ofValue("Lovelace"))
                .personalEmail(Property.ofValue("ada@example.com"))
                .build()
                .run(runContext());
            assertEquals("col-9", collaborator.getId());

            io.kestra.plugin.payfit.payslips.List.Output payslips = io.kestra.plugin.payfit.payslips.List.builder()
                .apiKey(Property.ofValue("secret"))
                .companyId(Property.ofValue("company-1"))
                .baseUrl(Property.ofValue(server.baseUrl()))
                .collaboratorId(Property.ofValue("col-1"))
                .build()
                .run(runContext());
            assertEquals(1, payslips.getCount());

            Export.Output journal = Export.builder()
                .apiKey(Property.ofValue("secret"))
                .companyId(Property.ofValue("company-1"))
                .baseUrl(Property.ofValue(server.baseUrl()))
                .date(Property.ofValue("202612"))
                .build()
                .run(runContext());
            assertTrue(journal.getUri() != null);

            Download.Output payslip = Download.builder()
                .apiKey(Property.ofValue("secret"))
                .companyId(Property.ofValue("company-1"))
                .baseUrl(Property.ofValue(server.baseUrl()))
                .collaboratorId(Property.ofValue("col-1"))
                .contractId(Property.ofValue("contract-1"))
                .payslipId(Property.ofValue("slip-1"))
                .build()
                .run(runContext());
            assertEquals(8, payslip.getSize());
            assertEquals("application/pdf", payslip.getContentType());
        }
    }

    @Test
    void collaboratorTriggerRecordsTheInitialSnapshot() throws Exception {
        try (PayfitMockServer server = new PayfitMockServer()) {
            server.handler(request -> {
                assertEquals("/companies/company-1/collaborators", request.path());
                assertEquals("Bearer secret", request.authorization());
                return PayfitMockServer.Response.json(200, "{\"collaborators\":[{\"id\":\"a\"}]}");
            });
            assertThrows(InvalidTriggerConfigurationException.class, () -> io.kestra.plugin.payfit.collaborators.Trigger.builder()
                .interval(Duration.ofSeconds(1))
                .build()
                .nextEvaluationDate());

            io.kestra.plugin.payfit.collaborators.Trigger trigger = io.kestra.plugin.payfit.collaborators.Trigger.builder()
                .id("collaborators")
                .type(io.kestra.plugin.payfit.collaborators.Trigger.class.getName())
                .apiKey(Property.ofValue("secret"))
                .companyId(Property.ofValue("company-1"))
                .baseUrl(Property.ofValue(server.baseUrl()))
                .interval(Duration.ofMinutes(5))
                .fireOnInitial(Property.ofValue(false))
                .build();
            Flow flow = Flow.builder().id("payfit").namespace("company.team").tenantId("main").revision(1).build();
            ConditionContext conditionContext = ConditionContext.builder().flow(flow).runContext(runContextFactory.of(flow, trigger)).build();
            TriggerContext triggerContext = TriggerContext.builder()
                .namespace("company.team")
                .flowId("payfit")
                .triggerId("collaborators")
                .date(ZonedDateTime.now())
                .build();

            assertTrue(trigger.evaluate(conditionContext, triggerContext).isEmpty());
            assertEquals(1, server.requests.size());
        }
    }

    @Test
    void fetchTypeReturnsRowsOneRowAFileOrOnlyTheCount() throws Exception {
        try (PayfitMockServer server = new PayfitMockServer()) {
            server.handler(request -> PayfitMockServer.Response.json(200, "{\"collaborators\":[{\"id\":\"a\",\"extra\":1},{\"id\":\"b\"}],\"meta\":{\"nextPageToken\":null}}"));
            List.Output fetched = listTask(server).build().run(runContext());
            assertEquals(2, fetched.getCount());
            assertEquals(null, fetched.getUri());
            assertEquals("a", fetched.getCollaborators().getFirst().getId());
            assertEquals(1, fetched.getCollaborators().getFirst().getAdditionalProperties().get("extra"));

            server.requests.clear();
            List.Output one = listTask(server).fetchType(Property.ofValue(io.kestra.core.models.tasks.common.FetchType.FETCH_ONE)).build().run(runContext());
            assertEquals(1, one.getCount());
            assertEquals("a", one.getCollaborator().getId());
            assertEquals(null, one.getCollaborators());
            assertTrue(server.requests.getFirst().query().contains("maxResults=1"));

            List.Output stored = listTask(server).fetchType(Property.ofValue(io.kestra.core.models.tasks.common.FetchType.STORE)).build().run(runContext());
            assertEquals(null, stored.getCollaborators());
            assertTrue(stored.getUri() != null);

            List.Output none = listTask(server).fetchType(Property.ofValue(io.kestra.core.models.tasks.common.FetchType.NONE)).build().run(runContext());
            assertEquals(2, none.getCount());
            assertEquals(null, none.getUri());
            assertEquals(null, none.getCollaborators());
        }
    }

    @Test
    void frenchEndpointsRejectSpanishAndBritishCompanies() throws Exception {
        try (PayfitMockServer server = new PayfitMockServer()) {
            server.handler(request -> PayfitMockServer.Response.json(200, "{\"id\":\"company-1\",\"name\":\"Acme\",\"country\":\"ES\"}"));
            PayfitException rejected = assertThrows(PayfitException.class, () -> Export.builder()
                .apiKey(Property.ofValue("secret"))
                .companyId(Property.ofValue("company-1"))
                .baseUrl(Property.ofValue(server.baseUrl()))
                .date(Property.ofValue("202612"))
                .build()
                .run(runContext()));
            assertTrue(rejected.getMessage().contains("FR"));
            assertTrue(server.requests.stream().noneMatch(request -> request.path().contains("accounting-v2")));
        }
    }

    @Test
    void collaboratorWatermarkPersistsAcrossPolls() throws Exception {
        try (PayfitMockServer server = new PayfitMockServer()) {
            java.util.concurrent.atomic.AtomicInteger generation = new java.util.concurrent.atomic.AtomicInteger();
            server.handler(request -> generation.get() == 0
                ? PayfitMockServer.Response.json(200, "{\"collaborators\":[{\"id\":\"a\"}]}")
                : PayfitMockServer.Response.json(200, "{\"collaborators\":[{\"id\":\"a\"},{\"id\":\"b\"}]}"));
            io.kestra.plugin.payfit.collaborators.Trigger trigger = io.kestra.plugin.payfit.collaborators.Trigger.builder()
                .id("collaborators")
                .type(io.kestra.plugin.payfit.collaborators.Trigger.class.getName())
                .apiKey(Property.ofValue("secret"))
                .companyId(Property.ofValue("company-1"))
                .baseUrl(Property.ofValue(server.baseUrl()))
                .interval(java.time.Duration.ofMinutes(5))
                .fireOnInitial(Property.ofValue(false))
                .build();
            Flow flow = Flow.builder().id("payfit").namespace("company.team").tenantId("main").revision(1).build();
            RunContext runContext = runContextFactory.of(flow, trigger);
            ConditionContext conditionContext = ConditionContext.builder().flow(flow).runContext(runContext).build();
            TriggerContext triggerContext = TriggerContext.builder()
                .namespace("company.team")
                .flowId("payfit")
                .triggerId("collaborators")
                .date(java.time.ZonedDateTime.now())
                .build();
            String key = io.kestra.core.models.triggers.StatefulTriggerService.defaultKey("company.team", "payfit", "collaborators");
            assertTrue(trigger.evaluate(conditionContext, triggerContext).isEmpty());
            assertTrue(trigger.evaluate(conditionContext, triggerContext).isEmpty());
            assertTrue(String.valueOf(runContext.namespaceKv("company.team").getValue(key).orElseThrow()).contains("a"));
            generation.incrementAndGet();
            assertThrows(IllegalStateException.class, () -> trigger.evaluate(conditionContext, triggerContext));
            String persisted = String.valueOf(runContext.namespaceKv("company.team").getValue(key).orElseThrow());
            assertTrue(persisted.contains("a"));
            assertTrue(persisted.contains("b"));
        }
    }

    @Test
    void listsGetsAndPollsTheResourcesTheReviewCalledOut() throws Exception {
        try (PayfitMockServer server = new PayfitMockServer()) {
            server.handler(request -> switch (request.path()) {
                case "/companies/company-1/absences" -> PayfitMockServer.Response.json(200, "{\"absences\":[{\"id\":\"absence-1\",\"contractId\":\"contract-1\",\"status\":\"approved\",\"note\":\"doctor\"}]}");
                case "/companies/company-1/collaborators/col-1" -> PayfitMockServer.Response.json(200, "{\"id\":\"col-1\",\"firstName\":\"Ada\",\"personalEmail\":\"ada@example.com\"}");
                case "/companies/company-1/contracts/contract-1" -> PayfitMockServer.Response.json(200, "{\"id\":\"contract-1\",\"jobName\":\"Engineer\"}");
                case "/companies/company-1/contracts" -> PayfitMockServer.Response.json(200, "{\"contracts\":[{\"id\":\"contract-1\",\"status\":\"ACTIVE\"}]}");
                default -> PayfitMockServer.Response.json(404, "{\"message\":\"missing\"}");
            });

            io.kestra.plugin.payfit.absences.List.Output absences = io.kestra.plugin.payfit.absences.List.builder()
                .apiKey(Property.ofValue("secret"))
                .companyId(Property.ofValue("company-1"))
                .baseUrl(Property.ofValue(server.baseUrl()))
                .build()
                .run(runContext());
            assertEquals(1, absences.getCount());
            assertEquals("absence-1", absences.getAbsences().getFirst().getId());
            assertEquals("doctor", absences.getAbsences().getFirst().getAdditionalProperties().get("note"));

            io.kestra.plugin.payfit.collaborators.Get.Output collaborator = io.kestra.plugin.payfit.collaborators.Get.builder()
                .apiKey(Property.ofValue("secret"))
                .companyId(Property.ofValue("company-1"))
                .baseUrl(Property.ofValue(server.baseUrl()))
                .collaboratorId(Property.ofValue("col-1"))
                .build()
                .run(runContext());
            assertEquals("col-1", collaborator.getId());
            assertEquals("Ada", collaborator.getCollaborator().get("firstName"));

            io.kestra.plugin.payfit.contracts.Get.Output contract = io.kestra.plugin.payfit.contracts.Get.builder()
                .apiKey(Property.ofValue("secret"))
                .companyId(Property.ofValue("company-1"))
                .baseUrl(Property.ofValue(server.baseUrl()))
                .contractId(Property.ofValue("contract-1"))
                .build()
                .run(runContext());
            assertEquals("contract-1", contract.getId());
            assertEquals("Engineer", contract.getContract().get("jobName"));

            io.kestra.plugin.payfit.contracts.List.Output contracts = io.kestra.plugin.payfit.contracts.List.builder()
                .apiKey(Property.ofValue("secret"))
                .companyId(Property.ofValue("company-1"))
                .baseUrl(Property.ofValue(server.baseUrl()))
                .build()
                .run(runContext());
            assertEquals(1, contracts.getCount());
            assertEquals("contract-1", contracts.getContracts().getFirst().getId());
        }
    }

    @Test
    void absenceTriggerFiresOnlyTheAbsencesThatMatchOn() throws Exception {
        try (PayfitMockServer server = new PayfitMockServer()) {
            java.util.concurrent.atomic.AtomicInteger generation = new java.util.concurrent.atomic.AtomicInteger();
            server.handler(request -> generation.get() == 0
                ? PayfitMockServer.Response.json(200, "{\"absences\":[{\"id\":\"absence-1\",\"status\":\"approved\"}]}")
                : PayfitMockServer.Response.json(200, "{\"absences\":[{\"id\":\"absence-1\",\"status\":\"approved\"},{\"id\":\"absence-2\",\"status\":\"approved\",\"comment\":\"new\"}]}"));
            io.kestra.plugin.payfit.absences.Trigger trigger = io.kestra.plugin.payfit.absences.Trigger.builder()
                .id("absences")
                .type(io.kestra.plugin.payfit.absences.Trigger.class.getName())
                .apiKey(Property.ofValue("secret"))
                .companyId(Property.ofValue("company-1"))
                .baseUrl(Property.ofValue(server.baseUrl()))
                .interval(Duration.ofMinutes(5))
                .fireOnInitial(Property.ofValue(false))
                .build();
            Flow flow = Flow.builder().id("payfit").namespace("company.team").tenantId("main").revision(1).build();
            RunContext runContext = runContextFactory.of(flow, trigger);
            ConditionContext conditionContext = ConditionContext.builder().flow(flow).runContext(runContext).build();
            TriggerContext triggerContext = TriggerContext.builder()
                .namespace("company.team")
                .flowId("payfit")
                .triggerId("absences")
                .date(ZonedDateTime.now())
                .build();

            assertTrue(trigger.evaluate(conditionContext, triggerContext).isEmpty());
            generation.incrementAndGet();
            java.lang.reflect.Field executionId = io.kestra.core.runners.DefaultRunContext.class.getDeclaredField("triggerExecutionId");
            executionId.setAccessible(true);
            executionId.set(runContext, "absence-exec");
            var execution = trigger.evaluate(conditionContext, triggerContext).orElseThrow();
            assertEquals(1, execution.getTrigger().getVariables().get("count"));
            assertEquals(null, execution.getTrigger().getVariables().get("uri"));
            assertTrue(execution.getTrigger().getVariables().get("absences").toString().contains("absence-2"));
            assertTrue(execution.getTrigger().getVariables().get("absences").toString().contains("new"));
            assertFalse(execution.getTrigger().getVariables().get("absences").toString().contains("absence-1"));
        }
    }

    private Create absence(PayfitMockServer server) {
        return Create.builder()
            .apiKey(Property.ofValue("secret"))
            .companyId(Property.ofValue("company-1"))
            .baseUrl(Property.ofValue(server.baseUrl()))
            .contractId(Property.ofValue("contract-1"))
            .absenceType(Property.ofValue("fr_conges_payes"))
            .startDate(Property.ofValue("2026-12-24"))
            .endDate(Property.ofValue("2026-12-26"))
            .build();
    }

    private List.ListBuilder<?, ?> listTask(PayfitMockServer server) {
        return List.builder()
            .apiKey(Property.ofValue("secret"))
            .companyId(Property.ofValue("company-1"))
            .baseUrl(Property.ofValue(server.baseUrl()))
            .fetchType(Property.ofValue(io.kestra.core.models.tasks.common.FetchType.FETCH));
    }

    private RunContext runContext() {
        return runContextFactory.of(Map.of());
    }
}
