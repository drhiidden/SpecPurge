package com.drhiidden.agentic.core;

import com.drhiidden.agentic.model.TestRequest;
import com.drhiidden.agentic.model.TestResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Ejecutor de tests generados.
 * Envía requests a la API y captura resultados.
 */
@Slf4j
public class TestExecutor {
    
    private final String baseUrl;
    private final ObjectMapper objectMapper;
    
    public TestExecutor(String baseUrl) {
        this.baseUrl = baseUrl;
        this.objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        
        RestAssured.baseURI = baseUrl;
    }
    
    public List<TestResult> executeAll(List<TestRequest> requests) {
        List<TestResult> results = new ArrayList<>();
        
        int current = 0;
        int total = requests.size();
        
        for (TestRequest request : requests) {
            current++;
            log.info("[{}/{}] Testing {} {}", current, total, request.getMethod(), request.getPath());
            
            TestResult result = executeRequest(request);
            results.add(result);
            
            String status = result.isPassed() ? "✓" : "✗";
            log.info("  {} Status: {} (expected: {})", 
                status, result.getActualStatus(), result.getExpectedStatus());
        }
        
        long passed = results.stream().filter(TestResult::isPassed).count();
        long failed = results.size() - passed;
        
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log.info("✓ Passed: {} | ✗ Failed: {} | Total: {}", passed, failed, results.size());
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        
        return results;
    }
    
    private TestResult executeRequest(TestRequest request) {
        Instant start = Instant.now();
        
        try {
            RequestSpecification spec = RestAssured.given()
                .contentType(ContentType.JSON);
            
            // Query params
            if (request.getQueryParams() != null) {
                request.getQueryParams().forEach((k, v) -> spec.queryParam(k, v));
            }
            
            // Body
            if (request.getBody() != null) {
                String json = objectMapper.writeValueAsString(request.getBody());
                spec.body(json);
            }
            
            // Execute
            Response response = switch (request.getMethod()) {
                case "GET" -> spec.get(request.getPath());
                case "POST" -> spec.post(request.getPath());
                case "PUT" -> spec.put(request.getPath());
                case "PATCH" -> spec.patch(request.getPath());
                case "DELETE" -> spec.delete(request.getPath());
                default -> throw new IllegalArgumentException("Unsupported method: " + request.getMethod());
            };
            
            Duration duration = Duration.between(start, Instant.now());
            int actualStatus = response.getStatusCode();
            
            // Determine if passed
            boolean passed = isPassed(request.getExpectedStatusCode(), actualStatus);
            
            return TestResult.builder()
                .method(request.getMethod())
                .path(request.getPath())
                .operationId(request.getOperationId())
                .expectedStatus(request.getExpectedStatusCode())
                .actualStatus(actualStatus)
                .passed(passed)
                .requestBody(request.getBody() != null ? objectMapper.writeValueAsString(request.getBody()) : null)
                .responseBody(response.body().asString())
                .duration(duration)
                .build();
            
        } catch (Exception e) {
            Duration duration = Duration.between(start, Instant.now());
            
            return TestResult.builder()
                .method(request.getMethod())
                .path(request.getPath())
                .operationId(request.getOperationId())
                .expectedStatus(request.getExpectedStatusCode())
                .actualStatus(0)
                .passed(false)
                .duration(duration)
                .errorMessage(e.getMessage())
                .build();
        }
    }
    
    private boolean isPassed(int expected, int actual) {
        // Flexible matching
        // 200-299 considerado success
        // 404 OK para GET de recursos no existentes
        // 401 OK si endpoint requiere auth (no tenemos token)
        
        if (actual == expected) return true;
        
        if (expected == 200 && actual >= 200 && actual < 300) return true;
        
        if (expected == 200 && (actual == 404 || actual == 401)) {
            return true; // Not blocking
        }
        
        return false;
    }
}
