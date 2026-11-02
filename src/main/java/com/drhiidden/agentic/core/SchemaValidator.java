package com.drhiidden.agentic.core;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.models.media.Schema;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Validador ligero de respuestas JSON contra el esquema OpenAPI 3.0 (modelo de swagger-parser).
 *
 * Comprueba tipo, propiedades requeridas, enum, items de arrays y oneOf/anyOf. Es deliberadamente
 * tolerante: ignora additionalProperties, formatos y valores null en propiedades no requeridas, para
 * detectar deriva de contrato real sin generar ruido.
 */
public final class SchemaValidator {

    private static final int MAX_DEPTH = 12;
    private static final int MAX_ARRAY_ITEMS = 50;
    private static final int MAX_VIOLATIONS = 25;

    private SchemaValidator() {}

    public static List<String> validate(JsonNode node, Schema<?> schema) {
        List<String> out = new ArrayList<>();
        walk("$", node, schema, out, 0);
        return out;
    }

    private static void walk(String path, JsonNode n, Schema<?> s, List<String> out, int depth) {
        if (s == null || n == null || depth > MAX_DEPTH || out.size() >= MAX_VIOLATIONS) return;
        if (n.isNull()) return; // null en propiedades requeridas se comprueba en el objeto padre

        // oneOf / anyOf: basta con que valide una alternativa
        List<Schema> alternatives = new ArrayList<>();
        if (s.getOneOf() != null) alternatives.addAll(s.getOneOf());
        if (s.getAnyOf() != null) alternatives.addAll(s.getAnyOf());
        if (!alternatives.isEmpty()) {
            boolean anyOk = alternatives.stream().anyMatch(alt -> {
                List<String> tmp = new ArrayList<>();
                walk(path, n, alt, tmp, depth + 1);
                return tmp.isEmpty();
            });
            if (!anyOk) out.add(path + ": no cumple ninguna alternativa oneOf/anyOf");
            return;
        }

        String type = s.getType();
        if (type == null && s.getProperties() != null) type = "object";

        if (type != null && !typeMatches(type, n)) {
            out.add(path + ": se esperaba " + type + " y llegó " + n.getNodeType().name().toLowerCase());
            return;
        }

        if (s.getEnum() != null && !s.getEnum().isEmpty() && (n.isTextual() || n.isNumber())) {
            String val = n.asText();
            boolean ok = s.getEnum().stream().anyMatch(e -> String.valueOf(e).equals(val));
            if (!ok) out.add(path + ": valor '" + val + "' fuera del enum " + s.getEnum());
        }

        if ("array".equals(type) && n.isArray()) {
            Schema<?> items = s.getItems();
            int i = 0;
            for (JsonNode el : n) {
                if (i >= MAX_ARRAY_ITEMS) break;
                walk(path + "[" + i + "]", el, items, out, depth + 1);
                i++;
            }
        }

        if ("object".equals(type) && n.isObject()) {
            if (s.getRequired() != null) {
                for (Object req : s.getRequired()) {
                    String name = String.valueOf(req);
                    JsonNode child = n.get(name);
                    if (child == null) {
                        out.add(path + "." + name + ": propiedad requerida ausente");
                    } else if (child.isNull() && !isNullable(s, name)) {
                        out.add(path + "." + name + ": propiedad requerida con valor null");
                    }
                }
            }
            if (s.getProperties() != null) {
                for (Map.Entry<String, Schema> p : ((Map<String, Schema>) s.getProperties()).entrySet()) {
                    JsonNode child = n.get(p.getKey());
                    if (child != null) walk(path + "." + p.getKey(), child, p.getValue(), out, depth + 1);
                }
            }
        }
    }

    private static boolean isNullable(Schema<?> parent, String prop) {
        if (parent.getProperties() == null) return false;
        Schema<?> p = (Schema<?>) parent.getProperties().get(prop);
        return p != null && Boolean.TRUE.equals(p.getNullable());
    }

    private static boolean typeMatches(String type, JsonNode n) {
        return switch (type) {
            case "string" -> n.isTextual();
            case "integer" -> n.isIntegralNumber();
            case "number" -> n.isNumber();
            case "boolean" -> n.isBoolean();
            case "array" -> n.isArray();
            case "object" -> n.isObject();
            default -> true;
        };
    }
}
