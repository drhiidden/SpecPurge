package com.drhiidden.agentic.core;

import com.drhiidden.agentic.model.ApiSpec;
import com.drhiidden.agentic.model.EndpointInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenApiParserTest {

    private static final String SPEC = """
        openapi: 3.0.3
        info: {title: demo, version: "1"}
        security:
          - bearerAuth: []
        paths:
          /inherits:
            get:
              operationId: inherits
              responses:
                "200":
                  description: ok
                  content:
                    application/json:
                      schema: {type: object, required: [id], properties: {id: {type: string}}}
          /public:
            get:
              operationId: publicOp
              security: []
              responses: {"200": {description: ok}}
          /explicit:
            get:
              operationId: explicitOp
              security:
                - bearerAuth: []
              responses: {"200": {description: ok}}
        components:
          securitySchemes:
            bearerAuth: {type: http, scheme: bearer}
        """;

    private EndpointInfo find(ApiSpec spec, String opId) {
        return spec.getEndpoints().stream().filter(e -> opId.equals(e.getOperationId())).findFirst().orElseThrow();
    }

    @Test
    void globalSecurityIsInheritedAndEmptyListMakesOperationPublic(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("api.yml");
        Files.writeString(f, SPEC);
        ApiSpec spec = new OpenApiParser().parseFromUrl(f.toString());

        assertTrue(find(spec, "inherits").isRequiresAuth(), "hereda el security global");
        assertFalse(find(spec, "publicOp").isRequiresAuth(), "security: [] = público");
        assertTrue(find(spec, "explicitOp").isRequiresAuth());
        assertNotNull(find(spec, "inherits").getResponseSchema(), "extrae el esquema de la respuesta 2xx");
        assertTrue(spec.getPublicEndpoints().size() == 1);
    }
}
