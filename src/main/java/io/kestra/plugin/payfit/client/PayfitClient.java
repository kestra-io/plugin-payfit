package io.kestra.plugin.payfit.client;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;

import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.HttpResponse;
import io.kestra.core.http.client.HttpClient;
import io.kestra.core.http.client.HttpClientException;
import io.kestra.core.http.client.HttpClientResponseException;
import io.kestra.core.http.client.configurations.HttpConfiguration;
import io.kestra.core.models.tasks.retrys.Exponential;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.utils.RetryUtils;
import io.kestra.plugin.payfit.model.Company;

/**
 * HTTP client for the PayFit Partner API ({@code https://partner-api.payfit.com}) and the OAuth host.
 */
public final class PayfitClient implements AutoCloseable {
    public static final String DEFAULT_BASE_URL = "https://partner-api.payfit.com";
    public static final String DEFAULT_OAUTH_URL = "https://oauth.payfit.com";
    private static final int BODY_LIMIT = 2_000;

    private final RunContext runContext;
    private final String apiKey;
    private final String configuredCompanyId;
    private final String baseUrl;
    private final String oauthUrl;
    private final HttpConfiguration options;
    private final com.fasterxml.jackson.databind.ObjectMapper mapper = JacksonMapper.ofJson();
    private String resolvedCompanyId;
    private Map<String, Object> introspection;

    public PayfitClient(
        RunContext runContext,
        String apiKey,
        String companyId,
        String baseUrl,
        String oauthUrl,
        HttpConfiguration options
    ) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("apiKey is required");
        }
        this.runContext = runContext;
        this.apiKey = apiKey;
        this.configuredCompanyId = blankToNull(companyId);
        this.baseUrl = stripTrailingSlash(baseUrl == null || baseUrl.isBlank() ? DEFAULT_BASE_URL : baseUrl);
        this.oauthUrl = stripTrailingSlash(oauthUrl == null || oauthUrl.isBlank() ? DEFAULT_OAUTH_URL : oauthUrl);
        this.options = options;
    }

    public String companyId() {
        if (configuredCompanyId != null) {
            return configuredCompanyId;
        }
        if (resolvedCompanyId != null) {
            return resolvedCompanyId;
        }
        Object id = introspect().get("company_id");
        if (id == null || id.toString().isBlank()) {
            throw new PayfitException("PayFit token introspection did not return company_id");
        }
        resolvedCompanyId = id.toString();
        return resolvedCompanyId;
    }

    public Map<String, Object> introspect() {
        if (introspection != null) {
            return introspection;
        }
        Map<String, Object> body = postAbsolute(oauthUrl + "/introspect", Map.of("token", apiKey), true);
        Object active = body.get("active");
        if (Boolean.FALSE.equals(active) || "false".equalsIgnoreCase(Objects.toString(active, ""))) {
            throw new PayfitException(401, "PayFit token is not active", String.valueOf(body));
        }
        introspection = body;
        return body;
    }

    public Map<String, Object> accessToken(Map<String, Object> body) {
        return postAbsolute(oauthUrl + "/token", body, false);
    }

    public Map<String, Object> get(String path, Map<String, String> query) {
        return asMap(send("GET", partnerUri(path, query), null, Map.of()));
    }

    public Object getJson(String path, Map<String, String> query) {
        JsonNode node = send("GET", partnerUri(path, query), null, Map.of());
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        return mapper.convertValue(node, Object.class);
    }

    public Map<String, Object> post(String path, Object body) {
        return asMap(send("POST", partnerUri(path, Map.of()), body, Map.of()));
    }

    public Map<String, Object> delete(String path, Object body) {
        return asMap(send("DELETE", partnerUri(path, Map.of()), body, Map.of()));
    }

    public byte[] getBytes(String path, Map<String, String> query, Map<String, String> headers) {
        return sendBytes("GET", partnerUri(path, query), headers);
    }

    public Company company() {
        return read(companyPath(""), Map.of(), Company.class);
    }

    public void requireCountry(String country) {
        Company company = company();
        if (company.getCountry() == null || !country.equals(company.getCountry())) {
            throw new PayfitException(
                "PayFit endpoint is available for " + country + " companies only. This company is " + company.getCountry() + ". PayFit country codes are FR, ES, and GB"
            );
        }
    }

    public <T> T read(String path, Map<String, String> query, Class<T> type) {
        return mapper.convertValue(send("GET", partnerUri(path, query), null, Map.of()), type);
    }

    public <T> T readValue(JsonNode node, Class<T> type) {
        return mapper.convertValue(node, type);
    }

    public <T> List<T> readList(String path, Map<String, String> query, Class<T> type) {
        JsonNode node = send("GET", partnerUri(path, query), null, Map.of());
        if (node == null || node.isNull()) {
            return List.of();
        }
        if (!node.isArray()) {
            throw new PayfitException("Expected a JSON array from PayFit");
        }
        List<T> items = new ArrayList<>();
        node.forEach(item -> items.add(mapper.convertValue(item, type)));
        return List.copyOf(items);
    }

    public <T> Page<T> list(String path, String collection, Map<String, String> query, boolean fetchAll, Integer pageSize, int maxPages, Class<T> type) {
        PayfitValidators.pageSize(pageSize);
        PayfitValidators.maxPages(maxPages);
        Map<String, String> params = new LinkedHashMap<>();
        if (query != null) {
            query.forEach((key, value) -> {
                if (value != null && !value.isBlank()) {
                    params.put(key, value);
                }
            });
        }
        if (pageSize != null) {
            params.put("maxResults", Integer.toString(pageSize));
        } else if (fetchAll) {
            params.put("maxResults", Integer.toString(PayfitValidators.MAX_PAGE_SIZE));
        }

        List<T> items = new ArrayList<>();
        Set<String> seenTokens = new LinkedHashSet<>();
        String token = params.get("nextPageToken");
        String next = null;
        int pages = 0;
        while (true) {
            if (token != null) {
                if (!seenTokens.add(token)) {
                    throw new PayfitException("PayFit returned a repeated pagination token; aborting to avoid a loop");
                }
                params.put("nextPageToken", token);
            }
            JsonNode body = send("GET", partnerUri(path, params), null, Map.of());
            pages++;
            items.addAll(collection(body, collection, type));
            next = nextToken(body);
            if (!fetchAll || next == null) {
                break;
            }
            if (pages >= maxPages) {
                runContext.logger().warn("Stopped PayFit pagination after {} pages because maxPages was reached", pages);
                break;
            }
            token = next;
        }
        boolean truncated = fetchAll && pages >= maxPages && next != null;
        return new Page(List.copyOf(items), !fetchAll || truncated ? next : null, pages);
    }

    public String companyPath(String suffix) {
        String path = "/companies/" + encode(companyId());
        if (suffix == null || suffix.isBlank()) {
            return path;
        }
        return path + (suffix.startsWith("/") ? suffix : "/" + suffix);
    }

    @Override
    public void close() throws IOException {
        // A client is opened per request so connection pools are not retained on the task.
    }

    private Map<String, Object> postAbsolute(String url, Object body, boolean bearer) {
        return asMap(send("POST", URI.create(url), body, bearer ? Map.of() : Map.of("Authorization", "omit")));
    }

    private JsonNode send(String method, URI uri, Object body, Map<String, String> headers) {
        try {
            String raw = this.<String>retry().runRetryIf(this::retryable, () -> execute(method, uri, body, headers));
            if (raw == null || raw.isBlank()) {
                return mapper.createObjectNode();
            }
            return mapper.readTree(raw);
        } catch (PayfitException e) {
            throw e;
        } catch (RuntimeException e) {
            HttpClientResponseException response = find(e, HttpClientResponseException.class);
            if (response != null) {
                throw toException(response);
            }
            throw e;
        } catch (Throwable e) {
            HttpClientResponseException response = find(e, HttpClientResponseException.class);
            if (response != null) {
                throw toException(response);
            }
            throw new PayfitException("PayFit request failed for " + method + " " + uri.getPath(), e);
        }
    }

    private byte[] sendBytes(String method, URI uri, Map<String, String> headers) {
        try {
            return this.<byte[]>retry().runRetryIf(this::retryable, () -> executeBytes(method, uri, headers));
        } catch (PayfitException e) {
            throw e;
        } catch (RuntimeException e) {
            HttpClientResponseException response = find(e, HttpClientResponseException.class);
            if (response != null) {
                throw toException(response);
            }
            throw e;
        } catch (Throwable e) {
            HttpClientResponseException response = find(e, HttpClientResponseException.class);
            if (response != null) {
                throw toException(response);
            }
            throw new PayfitException("PayFit request failed for " + method + " " + uri.getPath(), e);
        }
    }

    private String execute(String method, URI uri, Object body, Map<String, String> headers) throws Exception {
        HttpRequest request = request(method, uri, body, headers);
        try (HttpClient client = new HttpClient(runContext, options)) {
            HttpResponse<String> response = client.request(request, String.class);
            return response.getBody();
        } catch (HttpClientResponseException e) {
            honorRetryAfter(e);
            throw e;
        } catch (Exception e) {
            if (e instanceof HttpClientException || e instanceof IOException || e instanceof RuntimeException) {
                HttpClientResponseException response = find(e, HttpClientResponseException.class);
                if (response != null) {
                    honorRetryAfter(response);
                }
                if (e instanceof HttpClientException httpClientException) {
                    throw httpClientException;
                }
                if (e instanceof IOException ioException) {
                    throw ioException;
                }
                throw (RuntimeException) e;
            }
            throw new IOException(e);
        }
    }

    private byte[] executeBytes(String method, URI uri, Map<String, String> headers) throws Exception {
        HttpRequest request = request(method, uri, null, headers);
        try (HttpClient client = new HttpClient(runContext, options)) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            client.request(request, response -> {
                if (response.getBody() != null) {
                    try {
                        response.getBody().transferTo(output);
                    } catch (IOException e) {
                        throw new java.io.UncheckedIOException(e);
                    }
                }
            });
            return output.toByteArray();
        } catch (HttpClientResponseException e) {
            honorRetryAfter(e);
            throw e;
        } catch (Exception e) {
            HttpClientResponseException response = find(e, HttpClientResponseException.class);
            if (response != null) {
                honorRetryAfter(response);
            }
            if (e instanceof HttpClientException httpClientException) {
                throw httpClientException;
            }
            if (e instanceof IOException ioException) {
                throw ioException;
            }
            if (e instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IOException(e);
        }
    }

    private HttpRequest request(String method, URI uri, Object body, Map<String, String> headers) {
        HttpRequest.HttpRequestBuilder builder = HttpRequest.builder()
            .method(method)
            .uri(uri);
        boolean omitBearer = headers != null && "omit".equals(headers.get("Authorization"));
        if (!omitBearer) {
            builder.addHeader("Authorization", "Bearer " + apiKey);
        }
        String accept = headers != null && headers.get("Accept") != null ? headers.get("Accept") : "application/json";
        builder.addHeader("Accept", accept);
        if (headers != null) {
            headers.forEach((key, value) -> {
                if (value != null && !"Authorization".equalsIgnoreCase(key) && !"Accept".equalsIgnoreCase(key)) {
                    builder.addHeader(key, value);
                }
            });
        }
        if (body != null) {
            builder.addHeader("Content-Type", "application/json");
            builder.body(HttpRequest.JsonRequestBody.builder().content(body).build());
        }
        return builder.build();
    }

    private <T> RetryUtils.Instance<T, Exception> retry() {
        return RetryUtils.of(
            Exponential.builder()
                .delayFactor(2.0)
                .interval(Duration.ofMillis(200))
                .maxInterval(Duration.ofSeconds(2))
                .maxAttempts(5)
                .maxDuration(Duration.ofSeconds(30))
                .build(),
            runContext.logger()
        );
    }

    private boolean retryable(Throwable throwable) {
        HttpClientResponseException response = find(throwable, HttpClientResponseException.class);
        if (response != null) {
            int code = response.getResponse() == null || response.getResponse().getStatus() == null
                ? 0
                : response.getResponse().getStatus().getCode();
            return code == 408 || code == 425 || code == 429 || (code >= 500 && code != 501);
        }
        if (throwable instanceof SocketTimeoutException) {
            return true;
        }
        return throwable.getCause() != null && throwable.getCause() != throwable && retryable(throwable.getCause());
    }

    private void honorRetryAfter(HttpClientResponseException exception) {
        if (!retryable(exception) || exception.getResponse() == null || exception.getResponse().getHeaders() == null) {
            return;
        }
        List<String> values = exception.getResponse().getHeaders().allValues("Retry-After");
        if (values == null || values.isEmpty()) {
            return;
        }
        try {
            long seconds = Long.parseLong(values.getFirst().trim());
            if (seconds > 0) {
                Thread.sleep(Math.min(seconds, 5) * 1000L);
            }
        } catch (NumberFormatException ignored) {
            // HTTP-date Retry-After is left to the exponential backoff.
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new PayfitException("Interrupted while waiting for PayFit Retry-After", interrupted);
        }
    }

    private PayfitException toException(HttpClientResponseException exception) {
        int code = exception.getResponse() == null || exception.getResponse().getStatus() == null
            ? 0
            : exception.getResponse().getStatus().getCode();
        String body = truncate(bodyOf(exception));
        String message = "PayFit API request failed with status " + code;
        if (!body.isBlank()) {
            message = message + ": " + body;
        }
        return new PayfitException(code, message, body, exception);
    }

    private static String bodyOf(HttpClientResponseException exception) {
        if (exception.getResponse() == null) {
            return "";
        }
        Object body = exception.getResponse().getBody();
        if (body == null) {
            return Objects.toString(exception.getMessage(), "");
        }
        if (body instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return body.toString();
    }

    private URI partnerUri(String path, Map<String, String> query) {
        String suffix = path.startsWith("/") ? path : "/" + path;
        StringBuilder builder = new StringBuilder(baseUrl).append(suffix);
        if (query != null && !query.isEmpty()) {
            String rendered = query.entrySet().stream()
                .filter(entry -> entry.getValue() != null && !entry.getValue().isBlank())
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .reduce((left, right) -> left + "&" + right)
                .orElse("");
            if (!rendered.isEmpty()) {
                builder.append('?').append(rendered);
            }
        }
        return URI.create(builder.toString());
    }

    private <T> List<T> collection(JsonNode body, String name, Class<T> type) {
        JsonNode node = locate(body, name);
        if (node == null || node.isNull() || node.isMissingNode()) {
            return List.of();
        }
        if (!node.isArray()) {
            throw new PayfitException("Expected PayFit response field '" + name + "' to be an array");
        }
        List<T> items = new ArrayList<>();
        node.forEach(item -> items.add(mapper.convertValue(item, type)));
        return items;
    }

    private JsonNode locate(JsonNode body, String name) {
        if (body == null) {
            return null;
        }
        if (body.has(name)) {
            return body.get(name);
        }
        if (body.has("data") && body.get("data").isObject() && body.get("data").has(name)) {
            return body.get("data").get(name);
        }
        return null;
    }

    private String nextToken(JsonNode body) {
        if (body == null) {
            return null;
        }
        String direct = text(body.get("nextPageToken"));
        if (direct != null) {
            return direct;
        }
        if (body.has("meta")) {
            String meta = text(body.get("meta").get("nextPageToken"));
            if (meta != null) {
                return meta;
            }
        }
        if (body.has("pagination")) {
            return text(body.get("pagination").get("nextPageToken"));
        }
        return null;
    }

    private Map<String, Object> asMap(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return Map.of();
        }
        if (node.isObject()) {
            return mapper.convertValue(node, new TypeReference<>() {});
        }
        return Map.of("value", mapper.convertValue(node, Object.class));
    }

    private static String text(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        String value = node.asText(null);
        return value == null || value.isBlank() ? null : value;
    }

    public static String pathSegment(String value) {
        PayfitValidators.requiredText(value, "path segment");
        return encode(value);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String stripTrailingSlash(String value) {
        String stripped = value;
        while (stripped.endsWith("/")) {
            stripped = stripped.substring(0, stripped.length() - 1);
        }
        return stripped;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String truncate(String value) {
        if (value == null) {
            return "";
        }
        String compact = value.replaceAll("\\s+", " ").trim();
        return compact.length() <= BODY_LIMIT ? compact : compact.substring(0, BODY_LIMIT) + "...";
    }

    private static <T> T find(Throwable throwable, Class<T> type) {
        Throwable current = throwable;
        while (current != null) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            if (current.getCause() == current) {
                return null;
            }
            current = current.getCause();
        }
        return null;
    }

    public record Page<T>(List<T> items, String nextPageToken, int pages) {
    }
}
