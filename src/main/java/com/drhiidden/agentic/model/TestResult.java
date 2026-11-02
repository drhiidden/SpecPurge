package com.drhiidden.agentic.model;

import lombok.Builder;
import lombok.Data;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

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

    /** Nombre del caso (modo --cases). */
    private String name;
    /** Línea curl para reproducir (token como variable de entorno, nunca en claro). */
    private String curl;
    /** Fallos de aserciones / esquema. */
    @Builder.Default
    private List<String> violations = new ArrayList<>();
    /** Diferencias baseline vs candidato (modo diferencial). */
    @Builder.Default
    private List<String> diffs = new ArrayList<>();
    private int baselineStatus;
    
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
