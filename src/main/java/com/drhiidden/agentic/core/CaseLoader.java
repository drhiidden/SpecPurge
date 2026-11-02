package com.drhiidden.agentic.core;

import com.drhiidden.agentic.model.ApiSpec;
import com.drhiidden.agentic.model.Assertion;
import com.drhiidden.agentic.model.EndpointInfo;
import com.drhiidden.agentic.model.TestRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Carga casos de prueba declarativos desde YAML.
 *
 * <pre>
 * vars: { accountId: abc, token: "${env:SOME_ENV}" }
 * defaults: { schema: true, headers: { Accept-Language: es } }
 * cases:
 *   - name: item revisado
 *     path: /v1/accounts/{accountId}/items
 *     query: { region: "${region}" }
 *     expect:
 *       status: 200
 *       jsonPath:
 *         - { path: "$.items", minCount: 1 }
 *     ignore: [generatedAt]
 * </pre>
 *
 * Los casos con variables sin resolver (o con valores {@code CHANGE_ME...}) se omiten y se listan.
 */
public final class CaseLoader {

    private static final Pattern VAR = Pattern.compile("\\$\\{([^}]+)}");
    private static final List<String> OPERATORS = List.of(
        "exists", "notExists", "equals", "notEquals", "count", "minCount", "maxCount", "notEmpty", "matches", "contains");
    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    private CaseLoader() {}

    /** Variable sin valor: el caso se omite. */
    static final class Unresolved extends RuntimeException {
        Unresolved(String name) { super(name); }
    }

    public static List<TestRequest> load(Path file, ApiSpec spec, Map<String, String> cliParams,
                                         boolean schemaDefault, List<String> skipped) throws IOException {
        JsonNode root = YAML.readTree(Files.readString(file));
        return parse(root, spec, cliParams, schemaDefault, skipped);
    }

    public static List<TestRequest> parse(JsonNode root, ApiSpec spec, Map<String, String> cliParams,
                                          boolean schemaDefault, List<String> skipped) {
        Map<String, String> vars = new LinkedHashMap<>();
        if (root.has("vars")) {
            root.get("vars").fields().forEachRemaining(e -> {
                try {
                    vars.put(e.getKey(), interpolate(e.getValue().asText(), vars));
                } catch (Unresolved u) {
                    // variable global sin valor: los casos que la usen se omitirán
                }
            });
        }
        vars.putAll(cliParams); // --param tiene prioridad sobre el fichero

        JsonNode defaults = root.path("defaults");
        boolean schemaOn = defaults.has("schema") ? defaults.get("schema").asBoolean() : schemaDefault;
        Map<String, String> defaultHeaders = toStringMap(defaults.path("headers"));

        List<TestRequest> out = new ArrayList<>();
        int idx = 0;
        for (JsonNode c : root.path("cases")) {
            idx++;
            String name = c.path("name").asText("caso-" + idx);
            try {
                out.add(buildCase(c, name, vars, defaultHeaders, schemaOn, spec));
            } catch (Unresolved u) {
                skipped.add(name + " (falta " + u.getMessage() + ")");
            }
        }
        return out;
    }

    private static TestRequest buildCase(JsonNode c, String name, Map<String, String> vars,
                                         Map<String, String> defaultHeaders, boolean schemaOn, ApiSpec spec) {
        String method = c.path("method").asText("GET").toUpperCase();
        String template = c.path("path").asText();
        if (template.isBlank()) throw new IllegalArgumentException("El caso '" + name + "' no define 'path'");

        // Plantilla del OpenAPI antes de sustituir (para enlazar el esquema de respuesta)
        EndpointInfo endpoint = findEndpoint(spec, c.path("operationId").asText(null), method, template);

        Map<String, String> pathParams = new LinkedHashMap<>();
        toStringMap(c.path("pathParams")).forEach((k, v) -> pathParams.put(k, resolve(v, vars)));
        String path = interpolate(template, vars);
        for (Map.Entry<String, String> p : pathParams.entrySet()) {
            path = path.replace("{" + p.getKey() + "}", p.getValue());
        }
        for (Map.Entry<String, String> v : vars.entrySet()) {
            path = path.replace("{" + v.getKey() + "}", v.getValue());
        }
        if (path.contains("{")) {
            Matcher m = Pattern.compile("\\{([^}]+)}").matcher(path);
            throw new Unresolved(m.find() ? m.group(1) : "parámetro de ruta");
        }
        if (path.contains("CHANGE_ME")) throw new Unresolved("CHANGE_ME");

        Map<String, Object> query = new LinkedHashMap<>();
        toStringMap(c.path("query")).forEach((k, v) -> query.put(k, resolve(v, vars)));

        Map<String, String> headers = new LinkedHashMap<>(defaultHeaders);
        toStringMap(c.path("headers")).forEach((k, v) -> headers.put(k, resolve(v, vars)));

        Object body = null;
        if (c.has("body")) {
            body = YAML.convertValue(c.get("body"), Object.class);
        }

        JsonNode expect = c.path("expect");
        List<Integer> statuses = new ArrayList<>();
        JsonNode st = expect.path("status");
        if (st.isArray()) st.forEach(n -> statuses.add(n.asInt()));
        else statuses.add(st.isMissingNode() ? 200 : st.asInt());

        boolean schema = expect.has("schema") ? expect.get("schema").asBoolean() : schemaOn;

        List<Assertion> assertions = new ArrayList<>();
        for (JsonNode a : expect.path("jsonPath")) {
            String p = a.path("path").asText();
            boolean found = false;
            for (String op : OPERATORS) {
                if (a.has(op)) {
                    JsonNode v = a.get(op);
                    Object expected = v.isTextual() ? interpolate(v.asText(), vars)
                        : v.isNumber() ? v.numberValue() : v.isBoolean() ? (Object) v.asBoolean() : v.isNull() ? null : v.toString();
                    assertions.add(new Assertion(p, op, expected));
                    found = true;
                }
            }
            if (!found) throw new IllegalArgumentException("Aserción sin operador en el caso '" + name + "': " + a);
        }

        List<String> ignore = new ArrayList<>();
        c.path("ignore").forEach(n -> ignore.add(n.asText()));

        return TestRequest.builder()
            .name(name)
            .method(method)
            .path(path)
            .operationId(endpoint != null ? endpoint.getOperationId() : c.path("operationId").asText(null))
            .queryParams(query)
            .headers(headers)
            .body(body)
            .expectedStatusCode(statuses.get(0))
            .expectedStatuses(statuses)
            .assertions(assertions)
            .responseSchema(endpoint != null ? endpoint.getResponseSchema() : null)
            .validateSchema(schema && endpoint != null)
            .diffIgnore(ignore)
            .build();
    }

    private static EndpointInfo findEndpoint(ApiSpec spec, String operationId, String method, String template) {
        if (spec == null) return null;
        return spec.getEndpoints().stream()
            .filter(e -> operationId != null ? operationId.equals(e.getOperationId())
                : e.getMethod().equals(method) && e.getPath().equals(template))
            .findFirst()
            .orElse(null);
    }

    /** Interpola y rechaza valores 'CHANGE_ME…' (marcadores de ID pendiente). */
    static String resolve(String text, Map<String, String> vars) {
        String out = interpolate(text, vars);
        if (out.contains("CHANGE_ME")) throw new Unresolved("CHANGE_ME");
        return out;
    }

    static String interpolate(String text, Map<String, String> vars) {
        Matcher m = VAR.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String key = m.group(1).trim();
            String value = key.startsWith("env:") ? System.getenv(key.substring(4)) : vars.get(key);
            if (value == null || value.isBlank() || value.contains("CHANGE_ME")) throw new Unresolved(key);
            m.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static Map<String, String> toStringMap(JsonNode node) {
        Map<String, String> out = new LinkedHashMap<>();
        if (node == null || !node.isObject()) return out;
        for (Iterator<Map.Entry<String, JsonNode>> it = node.fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> e = it.next();
            out.put(e.getKey(), e.getValue().asText());
        }
        return out;
    }
}
