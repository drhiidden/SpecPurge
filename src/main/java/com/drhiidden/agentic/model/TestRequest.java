package com.drhiidden.agentic.model;

import lombok.Builder;
import lombok.Data;

import io.swagger.v3.oas.models.media.Schema;

import java.util.List;
import java.util.Map;

/**
 * Request generado para testing.
 */
@Data
@Builder
public class TestRequest {
    private String method;
    private String path;
    private String operationId;
    
    private Map<String, Object> queryParams;
    private Object body;
    
    private int expectedStatusCode;
    private boolean requiresAuth;

    /** Nombre legible del caso (modo --cases); null en modo generado. */
    private String name;
    private Map<String, String> headers;
    /** Estados aceptados (modo --cases). Si es null se usa la política flexible heredada de v1.0. */
    private List<Integer> expectedStatuses;
    private List<Assertion> assertions;
    /** Esquema de respuesta 2xx del OpenAPI, para --validate-schema. */
    private Schema<?> responseSchema;
    private boolean validateSchema;
    /** Nombres de campo ignorados en el modo diferencial. */
    private List<String> diffIgnore;
}
