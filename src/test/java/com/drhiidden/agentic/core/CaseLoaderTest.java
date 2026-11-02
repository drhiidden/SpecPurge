package com.drhiidden.agentic.core;

import com.drhiidden.agentic.model.TestRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaseLoaderTest {

    private static final String YAML = """
        vars:
          accountId: A1
          lineId: CHANGE_ME
        defaults:
          headers: {Accept-Language: es}
        cases:
          - name: ok
            path: /v1/accounts/{accountId}/items
            query: {region: "${region}"}
            expect:
              status: [200, 204]
              jsonPath:
                - {path: "$.items", minCount: 1}
                - {path: "$.locked", equals: true}
            ignore: [generatedAt]
          - name: sin-id
            path: /v1/lines/{lineId}
          - name: var-faltante
            path: /v1/accounts/{accountId}/x
            query: {a: "${noExiste}"}
        """;

    @Test
    void resolvesVarsSkipsUnresolvedAndParsesAssertions() throws Exception {
        var root = new ObjectMapper(new YAMLFactory()).readTree(YAML);
        List<String> skipped = new ArrayList<>();
        List<TestRequest> reqs = CaseLoader.parse(root, null, Map.of("region", "C9"), false, skipped);

        assertEquals(1, reqs.size());
        TestRequest r = reqs.get(0);
        assertEquals("/v1/accounts/A1/items", r.getPath());
        assertEquals("C9", r.getQueryParams().get("region"));
        assertEquals("es", r.getHeaders().get("Accept-Language"));
        assertEquals(List.of(200, 204), r.getExpectedStatuses());
        assertEquals(2, r.getAssertions().size());
        assertEquals(List.of("generatedAt"), r.getDiffIgnore());

        assertEquals(2, skipped.size());
        assertTrue(skipped.stream().anyMatch(s -> s.startsWith("sin-id")));
        assertTrue(skipped.stream().anyMatch(s -> s.contains("noExiste")));
    }
}
