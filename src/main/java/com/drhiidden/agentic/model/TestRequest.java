package com.drhiidden.agentic.model;

import lombok.Builder;
import lombok.Data;

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
}
