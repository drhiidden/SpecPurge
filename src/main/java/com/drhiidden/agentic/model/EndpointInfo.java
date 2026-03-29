package com.drhiidden.agentic.model;

import io.swagger.v3.oas.models.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Información extraída de un endpoint OpenAPI.
 */
@Data
@Builder
public class EndpointInfo {
    private String method;
    private String path;
    private String operationId;
    private String summary;
    private List<String> tags;
    
    private Map<String, Object> pathParams;
    private Map<String, Object> queryParams;
    
    private Schema<?> requestSchema;
    private boolean requiresAuth;
    
    public String getFullPath() {
        String fullPath = path;
        
        // Replace path params con valores
        for (Map.Entry<String, Object> entry : pathParams.entrySet()) {
            fullPath = fullPath.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
        }
        
        return fullPath;
    }
}
