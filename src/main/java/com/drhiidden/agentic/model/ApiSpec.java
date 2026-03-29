package com.drhiidden.agentic.model;

import lombok.Data;

import java.util.List;

/**
 * Representación del API OpenAPI completo.
 */
@Data
public class ApiSpec {
    private final String title;
    private final String version;
    private final List<EndpointInfo> endpoints;
    
    public List<EndpointInfo> getPublicEndpoints() {
        return endpoints.stream()
            .filter(e -> !e.isRequiresAuth())
            .toList();
    }
    
    public List<EndpointInfo> getProtectedEndpoints() {
        return endpoints.stream()
            .filter(EndpointInfo::isRequiresAuth)
            .toList();
    }
    
    public List<EndpointInfo> getEndpointsByTag(String tag) {
        return endpoints.stream()
            .filter(e -> e.getTags().contains(tag))
            .toList();
    }
}
