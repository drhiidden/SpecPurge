package com.drhiidden.agentic.model;

/**
 * Aserción sobre el cuerpo JSON de la respuesta (JsonPath).
 *
 * @param path     expresión JsonPath, p. ej. {@code $.versions[?(@.isDefault==true)]}
 * @param operator exists | notExists | equals | notEquals | count | minCount | maxCount | notEmpty | matches | contains
 * @param expected valor esperado (puede ser null para exists/notEmpty)
 */
public record Assertion(String path, String operator, Object expected) {

    @Override
    public String toString() {
        return path + " " + operator + (expected != null ? " " + expected : "");
    }
}
