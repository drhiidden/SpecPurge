package com.drhiidden.agentic.core;

import com.drhiidden.agentic.model.TestRequest;
import com.drhiidden.agentic.model.TestResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import lombok.extern.slf4j.Slf4j;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Ejecutor de tests generados o declarados.
 * Envía requests a la API (y opcionalmente a un baseline) y captura resultados.
 */
@Slf4j
public class TestExecutor {

    /**
     * @param baseUrl        API candidata (la que se valida)
     * @param compareBaseUrl API baseline para el modo diferencial (null = desactivado)
     * @param bearerToken    token Bearer (null = sin autenticación)
     * @param bearerEnv      nombre de la variable de entorno del token (solo para el curl reproducible)
     * @param failOnAuth     401 cuenta como fallo
     * @param strict         en modo generado, 404 no cuenta como correcto
     * @param failOnDiff     cualquier diferencia baseline/candidato es un fallo
     * @param delayMs        pausa entre peticiones
     * @param maxRequests    tope de peticiones por ejecución
     */
    public record Options(String baseUrl, String compareBaseUrl, String bearerToken, String bearerEnv,
                          boolean failOnAuth, boolean strict, boolean failOnDiff, long delayMs, int maxRequests) {
        public static Options simple(String baseUrl) {
            return new Options(baseUrl, null, null, null, false, false, false, 0, 1000);
        }
    }

    private record Raw(int status, String body, Duration duration, String error) {}

    private final Options options;
    private final ObjectMapper objectMapper;

    public TestExecutor(String baseUrl) {
        this(Options.simple(baseUrl));
    }

    public TestExecutor(Options options) {
        this.options = options;
        this.objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        RestAssured.baseURI = options.baseUrl();
    }

    public List<TestResult> executeAll(List<TestRequest> requests) {
        List<TestResult> results = new ArrayList<>();

        int total = Math.min(requests.size(), options.maxRequests());
        if (requests.size() > total) {
            log.warn("Se limitan las peticiones a {} (--max-requests); {} omitidas", total, requests.size() - total);
        }

        for (int i = 0; i < total; i++) {
            TestRequest request = requests.get(i);
            log.info("[{}/{}] Testing {} {}", i + 1, total, request.getMethod(), request.getPath());

            TestResult result = executeRequest(request);
            results.add(result);

            log.info("  {} Status: {} (expected: {}){}",
                result.isPassed() ? "✓" : "✗", result.getActualStatus(), result.getExpectedStatus(),
                result.getViolations().isEmpty() ? "" : " · " + result.getViolations().size() + " violación(es)");

            if (options.delayMs() > 0 && i < total - 1) {
                try { Thread.sleep(options.delayMs()); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
        }

        long passed = results.stream().filter(TestResult::isPassed).count();
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log.info("✓ Passed: {} | ✗ Failed: {} | Total: {}", passed, results.size() - passed, results.size());
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        return results;
    }

    private TestResult executeRequest(TestRequest request) {
        String requestBody = null;
        try {
            requestBody = request.getBody() != null ? objectMapper.writeValueAsString(request.getBody()) : null;
        } catch (Exception ignored) {
            // se informa más abajo al ejecutar
        }

        Raw candidate = call(options.baseUrl(), request, requestBody);

        List<Integer> accepted = request.getExpectedStatuses();
        boolean statusOk = candidate.error() == null && (accepted != null
            ? StatusPolicy.passes(accepted, candidate.status())
            : StatusPolicy.passes(request.getExpectedStatusCode(), candidate.status(), options.failOnAuth(), options.strict()));

        List<String> violations = new ArrayList<>();
        boolean is2xx = candidate.status() >= 200 && candidate.status() < 300;
        if (candidate.error() == null && is2xx) {
            if (request.isValidateSchema() && request.getResponseSchema() != null && candidate.body() != null && !candidate.body().isBlank()) {
                try {
                    JsonNode node = objectMapper.readTree(candidate.body());
                    SchemaValidator.validate(node, request.getResponseSchema()).forEach(v -> violations.add("esquema: " + v));
                } catch (Exception e) {
                    violations.add("esquema: la respuesta no es JSON válido");
                }
            }
            violations.addAll(AssertionEvaluator.evaluate(candidate.body(), request.getAssertions()));
        } else if (candidate.error() == null && request.getAssertions() != null && !request.getAssertions().isEmpty()) {
            violations.add("aserciones no evaluadas: estado " + candidate.status() + " (no 2xx)");
        }

        List<String> diffs = new ArrayList<>();
        int baselineStatus = 0;
        if (options.compareBaseUrl() != null) {
            Raw baseline = call(options.compareBaseUrl(), request, requestBody);
            baselineStatus = baseline.status();
            if (baseline.error() != null) {
                diffs.add("baseline no disponible: " + mask(baseline.error()));
            } else {
                if (baseline.status() != candidate.status()) {
                    diffs.add("estado: baseline=" + baseline.status() + " candidato=" + candidate.status());
                }
                diffs.addAll(DiffEngine.diff(baseline.body(), candidate.body(), request.getDiffIgnore()));
            }
        }

        boolean passed = statusOk && violations.isEmpty() && !(options.failOnDiff() && !diffs.isEmpty());

        return TestResult.builder()
            .name(request.getName())
            .method(request.getMethod())
            .path(request.getPath())
            .operationId(request.getOperationId())
            .expectedStatus(accepted != null && !accepted.isEmpty() ? accepted.get(0) : request.getExpectedStatusCode())
            .actualStatus(candidate.status())
            .passed(passed)
            .requestBody(requestBody)
            .responseBody(mask(candidate.body()))
            .duration(candidate.duration())
            .errorMessage(mask(candidate.error()))
            .curl(buildCurl(options.baseUrl(), request, requestBody))
            .violations(violations)
            .diffs(diffs)
            .baselineStatus(baselineStatus)
            .build();
    }

    private Raw call(String baseUrl, TestRequest request, String requestBody) {
        Instant start = Instant.now();
        try {
            RequestSpecification spec = RestAssured.given().baseUri(baseUrl).contentType(ContentType.JSON);

            if (options.bearerToken() != null) {
                spec.header("Authorization", "Bearer " + options.bearerToken());
            }
            if (request.getHeaders() != null) {
                request.getHeaders().forEach(spec::header);
            }
            if (request.getQueryParams() != null) {
                request.getQueryParams().forEach((k, v) -> { if (v != null) spec.queryParam(k, v); });
            }
            if (requestBody != null) {
                spec.body(requestBody);
            }

            Response response = switch (request.getMethod()) {
                case "GET" -> spec.get(request.getPath());
                case "HEAD" -> spec.head(request.getPath());
                case "POST" -> spec.post(request.getPath());
                case "PUT" -> spec.put(request.getPath());
                case "PATCH" -> spec.patch(request.getPath());
                case "DELETE" -> spec.delete(request.getPath());
                default -> throw new IllegalArgumentException("Unsupported method: " + request.getMethod());
            };
            return new Raw(response.getStatusCode(), response.body().asString(), Duration.between(start, Instant.now()), null);
        } catch (Exception e) {
            return new Raw(0, null, Duration.between(start, Instant.now()), e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /** curl reproducible: el token aparece como variable de entorno, nunca en claro. */
    String buildCurl(String baseUrl, TestRequest request, String requestBody) {
        StringBuilder url = new StringBuilder(baseUrl).append(request.getPath());
        if (request.getQueryParams() != null) {
            StringJoiner qs = new StringJoiner("&");
            request.getQueryParams().forEach((k, v) -> {
                if (v != null) qs.add(URLEncoder.encode(k, StandardCharsets.UTF_8) + "=" + URLEncoder.encode(String.valueOf(v), StandardCharsets.UTF_8));
            });
            if (qs.length() > 0) url.append('?').append(qs);
        }
        StringBuilder sb = new StringBuilder("curl -sS -X ").append(request.getMethod()).append(" '").append(url).append("'");
        if (options.bearerToken() != null) {
            sb.append(" -H \"Authorization: Bearer $").append(options.bearerEnv() != null ? options.bearerEnv() : "TOKEN").append("\"");
        }
        if (request.getHeaders() != null) {
            for (Map.Entry<String, String> h : request.getHeaders().entrySet()) {
                sb.append(" -H '").append(h.getKey()).append(": ").append(h.getValue()).append("'");
            }
        }
        if (requestBody != null) {
            sb.append(" -H 'Content-Type: application/json' -d '").append(requestBody.replace("'", "'\\''")).append("'");
        }
        return sb.toString();
    }

    private String mask(String text) {
        if (text == null || options.bearerToken() == null || options.bearerToken().isEmpty()) return text;
        return text.replace(options.bearerToken(), "***");
    }
}
