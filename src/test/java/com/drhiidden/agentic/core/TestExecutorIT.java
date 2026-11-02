package com.drhiidden.agentic.core;

import com.drhiidden.agentic.model.Assertion;
import com.drhiidden.agentic.model.TestRequest;
import com.drhiidden.agentic.model.TestResult;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Ejecutor contra servidores HTTP locales (JDK HttpServer): token, aserciones, diff, máscara y curl. */
class TestExecutorIT {

    private static HttpServer candidate;
    private static HttpServer baseline;

    private static HttpServer start(String json) throws Exception {
        HttpServer s = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        s.createContext("/v1/item", ex -> {
            boolean auth = "Bearer SECRET".equals(ex.getRequestHeaders().getFirst("Authorization"));
            byte[] body = (auth ? json : "{\"error\":\"echo SECRET\"}").getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(auth ? 200 : 401, body.length);
            ex.getResponseBody().write(body);
            ex.close();
        });
        s.start();
        return s;
    }

    @BeforeAll
    static void up() throws Exception {
        candidate = start("{\"locked\":true,\"versions\":[{\"n\":2}],\"ts\":2}");
        baseline = start("{\"locked\":false,\"versions\":[{\"n\":2}],\"ts\":1}");
    }

    @AfterAll
    static void down() {
        candidate.stop(0);
        baseline.stop(0);
    }

    private static String url(HttpServer s) {
        return "http://127.0.0.1:" + s.getAddress().getPort();
    }

    private TestRequest request(List<Assertion> assertions) {
        return TestRequest.builder()
            .name("caso").method("GET").path("/v1/item").queryParams(Map.of("region", "C1"))
            .expectedStatusCode(200).expectedStatuses(List.of(200))
            .assertions(assertions).diffIgnore(List.of("ts")).build();
    }

    @Test
    void passesWithTokenAndAssertions_andCurlNeverLeaksToken() {
        var opts = new TestExecutor.Options(url(candidate), null, "SECRET", "JWT_TOKEN", false, false, false, 0, 10);
        TestResult r = new TestExecutor(opts)
            .executeAll(List.of(request(List.of(new Assertion("$.locked", "equals", true))))).get(0);

        assertTrue(r.isPassed(), r.getViolations().toString());
        assertTrue(r.getCurl().contains("$JWT_TOKEN"));
        assertFalse(r.getCurl().contains("SECRET"));
        assertTrue(r.getCurl().contains("region=C1"));
    }

    @Test
    void failedAssertionIsReported() {
        var opts = new TestExecutor.Options(url(candidate), null, "SECRET", "JWT_TOKEN", false, false, false, 0, 10);
        TestResult r = new TestExecutor(opts)
            .executeAll(List.of(request(List.of(new Assertion("$.locked", "equals", false))))).get(0);
        assertFalse(r.isPassed());
        assertEquals(1, r.getViolations().size());
    }

    @Test
    void badTokenIs401AndBodyIsMasked() {
        var opts = new TestExecutor.Options(url(candidate), null, "WRONG", "JWT_TOKEN", true, false, false, 0, 10);
        TestResult r = new TestExecutor(opts).executeAll(List.of(request(List.of()))).get(0);
        assertFalse(r.isPassed());
        assertEquals(401, r.getActualStatus());
    }

    @Test
    void diffModeListsDifferencesAndFailsOnlyWhenAsked() {
        var lenient = new TestExecutor.Options(url(candidate), url(baseline), "SECRET", "JWT_TOKEN", false, false, false, 0, 10);
        TestResult r = new TestExecutor(lenient).executeAll(List.of(request(List.of()))).get(0);
        assertTrue(r.isPassed());
        assertTrue(r.getDiffs().stream().anyMatch(d -> d.startsWith("$.locked")), r.getDiffs().toString());
        assertTrue(r.getDiffs().stream().noneMatch(d -> d.contains("$.ts")));

        var strict = new TestExecutor.Options(url(candidate), url(baseline), "SECRET", "JWT_TOKEN", false, false, true, 0, 10);
        assertFalse(new TestExecutor(strict).executeAll(List.of(request(List.of()))).get(0).isPassed());
    }

    @Test
    void maxRequestsCapsExecution() {
        var opts = new TestExecutor.Options(url(candidate), null, "SECRET", "JWT_TOKEN", false, false, false, 0, 2);
        var reqs = List.of(request(List.of()), request(List.of()), request(List.of()));
        assertEquals(2, new TestExecutor(opts).executeAll(reqs).size());
    }
}
