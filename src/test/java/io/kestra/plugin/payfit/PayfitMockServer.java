package io.kestra.plugin.payfit;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.extension.ResponseDefinitionTransformer;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;

import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;

public final class PayfitMockServer implements AutoCloseable {
    public final List<RecordedRequest> requests = new CopyOnWriteArrayList<>();
    private final WireMockServer server;
    private volatile Function<RecordedRequest, Response> handler = request -> Response.json(404, "{\"message\":\"not found\"}");

    public PayfitMockServer() {
        server = new WireMockServer(WireMockConfiguration.options().dynamicPort().extensions(new HandlerTransformer()));
        server.start();
        server.stubFor(any(anyUrl()).willReturn(ok()));
    }

    public void handler(Function<RecordedRequest, Response> handler) {
        this.handler = handler;
    }

    public String baseUrl() {
        return server.baseUrl();
    }

    @Override
    public void close() {
        server.stop();
    }

    public record RecordedRequest(String method, String path, String query, String authorization, String accept, String body) {
    }

    public record Response(int status, String body, String contentType) {
        public static Response json(int status, String body) {
            return new Response(status, body, "application/json");
        }
    }

    private final class HandlerTransformer extends ResponseDefinitionTransformer {
        @Override
        public ResponseDefinition transform(Request request, ResponseDefinition responseDefinition, com.github.tomakehurst.wiremock.common.FileSource files, com.github.tomakehurst.wiremock.extension.Parameters parameters) {
            String rawQuery = request.getUrl().contains("?") ? request.getUrl().substring(request.getUrl().indexOf('?') + 1) : null;
            RecordedRequest recorded = new RecordedRequest(
                request.getMethod().getName(),
                request.getUrl().contains("?") ? request.getUrl().substring(0, request.getUrl().indexOf('?')) : request.getUrl(),
                rawQuery,
                request.getHeader("Authorization"),
                request.getHeader("Accept"),
                request.getBodyAsString()
            );
            requests.add(recorded);
            Response response = handler.apply(recorded);
            return new ResponseDefinition(response.status(), response.body() == null ? "" : response.body());
        }

        @Override
        public String getName() {
            return "payfit-handler";
        }

        @Override
        public boolean applyGlobally() {
            return true;
        }
    }
}
