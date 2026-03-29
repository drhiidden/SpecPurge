package com.drhiidden.agentic.core;

import com.drhiidden.agentic.model.ApiSpec;
import com.drhiidden.agentic.model.EndpointInfo;
import com.drhiidden.agentic.model.TestRequest;
import io.swagger.v3.oas.models.media.Schema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Genera requests de prueba automáticamente desde schemas OpenAPI.
 * 
 * Estrategias:
 * 1. Usar examples del schema si existen
 * 2. Usar defaults si están definidos
 * 3. Generar valores sensibles según tipo (string, integer, enum, etc.)
 * 4. Para schemas complejos: usar AI para generar payloads válidos
 */
@Slf4j
@RequiredArgsConstructor
public class RequestGenerator {
    
    private final ApiSpec spec;
    private final Random random = new Random();
    
    public List<TestRequest> generateAllRequests() {
        List<TestRequest> requests = new ArrayList<>();
        
        // Solo endpoints públicos para v1.0
        for (EndpointInfo endpoint : spec.getPublicEndpoints()) {
            try {
                TestRequest request = generateRequest(endpoint);
                requests.add(request);
                log.debug("Generated request: {} {}", request.getMethod(), request.getPath());
            } catch (Exception e) {
                log.warn("Failed to generate request for {} {}: {}", 
                    endpoint.getMethod(), endpoint.getPath(), e.getMessage());
            }
        }
        
        return requests;
    }
    
    private TestRequest generateRequest(EndpointInfo endpoint) {
        Object body = null;
        
        // Generate body for POST/PUT/PATCH
        if (List.of("POST", "PUT", "PATCH").contains(endpoint.getMethod())) {
            if (endpoint.getRequestSchema() != null) {
                body = generateFromSchema(endpoint.getRequestSchema());
            }
        }
        
        // Expected status: 200 for GET, 201 for POST, 200/204 for PUT/PATCH/DELETE
        int expectedStatus = switch (endpoint.getMethod()) {
            case "POST" -> 201;
            case "DELETE" -> 204;
            default -> 200;
        };
        
        return TestRequest.builder()
            .method(endpoint.getMethod())
            .path(endpoint.getFullPath())
            .operationId(endpoint.getOperationId())
            .queryParams(endpoint.getQueryParams())
            .body(body)
            .expectedStatusCode(expectedStatus)
            .requiresAuth(endpoint.isRequiresAuth())
            .build();
    }
    
    /**
     * Genera payload desde JSON Schema.
     * Implementa generación inteligente según tipo.
     */
    @SuppressWarnings("unchecked")
    private Object generateFromSchema(Schema<?> schema) {
        if (schema == null) return null;
        
        // Use example if available
        if (schema.getExample() != null) {
            return schema.getExample();
        }
        
        String type = schema.getType();
        
        return switch (type) {
            case "object" -> generateObject(schema);
            case "array" -> generateArray(schema);
            case "string" -> generateString(schema);
            case "integer" -> generateInteger(schema);
            case "number" -> generateNumber(schema);
            case "boolean" -> generateBoolean(schema);
            default -> null;
        };
    }
    
    private Map<String, Object> generateObject(Schema<?> schema) {
        Map<String, Object> obj = new HashMap<>();
        
        if (schema.getProperties() != null) {
            schema.getProperties().forEach((name, propSchema) -> {
                Object value = generateFromSchema((Schema<?>) propSchema);
                obj.put(name, value);
            });
        }
        
        return obj;
    }
    
    private List<Object> generateArray(Schema<?> schema) {
        if (schema.getItems() != null) {
            Object item = generateFromSchema(schema.getItems());
            return List.of(item);
        }
        return List.of();
    }
    
    private String generateString(Schema<?> schema) {
        // Check for enum
        if (schema.getEnum() != null && !schema.getEnum().isEmpty()) {
            return String.valueOf(schema.getEnum().get(0));
        }
        
        // Check for format
        String format = schema.getFormat();
        if (format != null) {
            return switch (format) {
                case "date-time" -> LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                case "date" -> LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
                case "email" -> "test@example.com";
                case "uri", "url" -> "https://example.com";
                case "uuid" -> UUID.randomUUID().toString();
                default -> "test-string-" + random.nextInt(1000);
            };
        }
        
        // Pattern matching para nombres comunes
        String name = schema.getName() != null ? schema.getName().toLowerCase() : "";
        
        if (name.contains("email")) return "test@example.com";
        if (name.contains("url") || name.contains("link")) return "https://example.com";
        if (name.contains("date")) return LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        if (name.contains("title")) return "Test Title " + random.nextInt(1000);
        if (name.contains("content") || name.contains("description")) return "Test content";
        if (name.contains("category")) return "GENERAL";
        
        return "test-value-" + random.nextInt(1000);
    }
    
    private Integer generateInteger(Schema<?> schema) {
        if (schema.getMinimum() != null) {
            return schema.getMinimum().intValue();
        }
        if (schema.getMaximum() != null) {
            return schema.getMaximum().intValue() / 2;
        }
        return 1;
    }
    
    private Double generateNumber(Schema<?> schema) {
        if (schema.getMinimum() != null) {
            return schema.getMinimum().doubleValue();
        }
        return 1.0;
    }
    
    private Boolean generateBoolean(Schema<?> schema) {
        if (schema.getDefault() != null) {
            return (Boolean) schema.getDefault();
        }
        return false;
    }
}
