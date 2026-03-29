package com.drhiidden.agentic.model;

import lombok.Builder;
import lombok.Data;

import java.time.Duration;

/**
 * Resultado de ejecución de un test.
 */
@Data
@Builder
public class TestResult {
    private String method;
    private String path;
    private String operationId;
    
    private int expectedStatus;
    private int actualStatus;
    private boolean passed;
    
    private String requestBody;
    private String responseBody;
    
    private Duration duration;
    private String errorMessage;
    
    public boolean isSuccess() {
        return actualStatus >= 200 && actualStatus < 300;
    }
    
    public boolean isClientError() {
        return actualStatus >= 400 && actualStatus < 500;
    }
    
    public boolean isServerError() {
        return actualStatus >= 500;
    }
}
