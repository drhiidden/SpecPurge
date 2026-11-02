package com.drhiidden.agentic.core;

import com.drhiidden.agentic.model.ApiSpec;
import com.drhiidden.agentic.model.EndpointInfo;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parser de OpenAPI 3.0 que extrae metadata de endpoints.
 * 
 * Extrae:
 * - Path + HTTP method
 * - Request body schema
 * - Response schemas
 * - Parameters (path, query, header)
 * - Security requirements
 */
@Slf4j
public class OpenApiParser {
    
    public ApiSpec parseFromUrl(String specUrl) {
        log.debug("Parsing OpenAPI from URL: {}", specUrl);
        
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        options.setResolveFully(true);
        
        OpenAPI openApi = new OpenAPIV3Parser().readLocation(specUrl, null, options).getOpenAPI();
        
        if (openApi == null) {
            throw new RuntimeException("Failed to parse OpenAPI spec from " + specUrl);
        }
        
        return extractSpec(openApi);
    }
    
    private ApiSpec extractSpec(OpenAPI openApi) {
        String title = openApi.getInfo().getTitle();
        String version = openApi.getInfo().getVersion();
        
        log.info("Parsing API: {} v{}", title, version);
        
        List<EndpointInfo> endpoints = new ArrayList<>();
        
        boolean globalSecurity = openApi.getSecurity() != null && !openApi.getSecurity().isEmpty();
        openApi.getPaths().forEach((path, pathItem) -> {
            endpoints.addAll(extractOperations(path, pathItem, globalSecurity));
        });
        
        return new ApiSpec(title, version, endpoints);
    }
    
    private List<EndpointInfo> extractOperations(String path, PathItem pathItem, boolean globalSecurity) {
        List<EndpointInfo> ops = new ArrayList<>();
        
        if (pathItem.getGet() != null) {
            ops.add(extractOperation("GET", path, pathItem.getGet(), globalSecurity));
        }
        if (pathItem.getPost() != null) {
            ops.add(extractOperation("POST", path, pathItem.getPost(), globalSecurity));
        }
        if (pathItem.getPut() != null) {
            ops.add(extractOperation("PUT", path, pathItem.getPut(), globalSecurity));
        }
        if (pathItem.getPatch() != null) {
            ops.add(extractOperation("PATCH", path, pathItem.getPatch(), globalSecurity));
        }
        if (pathItem.getDelete() != null) {
            ops.add(extractOperation("DELETE", path, pathItem.getDelete(), globalSecurity));
        }
        
        return ops;
    }
    
    private EndpointInfo extractOperation(String method, String path, Operation operation, boolean globalSecurity) {
        String operationId = operation.getOperationId();
        String summary = operation.getSummary();
        List<String> tags = operation.getTags();
        
        // Extract parameters
        Map<String, Object> pathParams = new HashMap<>();
        Map<String, Object> queryParams = new HashMap<>();
        
        if (operation.getParameters() != null) {
            for (Parameter param : operation.getParameters()) {
                if ("path".equals(param.getIn())) {
                    pathParams.put(param.getName(), extractDefaultValue(param.getSchema()));
                } else if ("query".equals(param.getIn())) {
                    queryParams.put(param.getName(), extractDefaultValue(param.getSchema()));
                }
            }
        }
        
        // Extract request body schema
        Schema<?> requestSchema = null;
        if (operation.getRequestBody() != null) {
            RequestBody reqBody = operation.getRequestBody();
            if (reqBody.getContent() != null && reqBody.getContent().get("application/json") != null) {
                requestSchema = reqBody.getContent().get("application/json").getSchema();
            }
        }
        
        // Security requirements
        // Herencia OpenAPI: sin 'security' en la operación se hereda el global; una lista vacía la deja pública.
        boolean requiresAuth = operation.getSecurity() != null
            ? !operation.getSecurity().isEmpty()
            : globalSecurity;
        
        Schema<?> responseSchema = extractResponseSchema(operation);
        
        return EndpointInfo.builder()
            .method(method)
            .path(path)
            .operationId(operationId)
            .summary(summary)
            .tags(tags != null ? tags : List.of())
            .pathParams(pathParams)
            .queryParams(queryParams)
            .requestSchema(requestSchema)
            .requiresAuth(requiresAuth)
            .responseSchema(responseSchema)
            .build();
    }
    
    private Schema<?> extractResponseSchema(Operation operation) {
        if (operation.getResponses() == null) return null;
        return operation.getResponses().entrySet().stream()
            .filter(e -> e.getKey().startsWith("2"))
            .map(Map.Entry::getValue)
            .filter(r -> r.getContent() != null && r.getContent().get("application/json") != null)
            .map(r -> r.getContent().get("application/json").getSchema())
            .filter(java.util.Objects::nonNull)
            .findFirst()
            .orElse(null);
    }
    
    private Object extractDefaultValue(Schema<?> schema) {
        if (schema == null) return null;
        
        if (schema.getDefault() != null) {
            return schema.getDefault();
        }
        
        if (schema.getExample() != null) {
            return schema.getExample();
        }
        
        // Generate sensible defaults based on type
        String type = schema.getType();
        return switch (type) {
            case "string" -> "test-value";
            case "integer" -> 1;
            case "number" -> 1.0;
            case "boolean" -> false;
            default -> null;
        };
    }
}
