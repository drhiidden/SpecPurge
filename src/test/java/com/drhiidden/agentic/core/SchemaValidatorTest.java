package com.drhiidden.agentic.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemaValidatorTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private Schema<?> itemSchema() {
        ObjectSchema s = new ObjectSchema();
        s.addProperty("id", new StringSchema());
        s.addProperty("version", new IntegerSchema());
        s.addProperty("state", new StringSchema()._enum(List.of("OPEN", "CLOSED")));
        s.setRequired(List.of("id"));
        return s;
    }

    @Test
    void validObjectHasNoViolations() throws Exception {
        var node = mapper.readTree("{\"id\":\"a\",\"version\":3,\"state\":\"OPEN\"}");
        assertTrue(SchemaValidator.validate(node, itemSchema()).isEmpty());
    }

    @Test
    void reportsMissingRequiredWrongTypeAndEnum() throws Exception {
        var node = mapper.readTree("{\"version\":\"x\",\"state\":\"WEIRD\"}");
        List<String> v = SchemaValidator.validate(node, itemSchema());
        assertTrue(v.stream().anyMatch(s -> s.contains("$.id") && s.contains("requerida")), v.toString());
        assertTrue(v.stream().anyMatch(s -> s.contains("$.version") && s.contains("integer")), v.toString());
        assertTrue(v.stream().anyMatch(s -> s.contains("$.state") && s.contains("enum")), v.toString());
    }

    @Test
    void nullOnOptionalPropertyIsTolerated_nullOnRequiredIsNot() throws Exception {
        assertTrue(SchemaValidator.validate(mapper.readTree("{\"id\":\"a\",\"version\":null}"), itemSchema()).isEmpty());
        assertEquals(1, SchemaValidator.validate(mapper.readTree("{\"id\":null}"), itemSchema()).size());
    }

    @Test
    void validatesArrayItems() throws Exception {
        ArraySchema arr = new ArraySchema();
        arr.setItems(itemSchema());
        List<String> v = SchemaValidator.validate(mapper.readTree("[{\"id\":\"a\"},{\"version\":1}]"), arr);
        assertEquals(1, v.size());
        assertTrue(v.get(0).startsWith("$[1].id"));
    }
}
