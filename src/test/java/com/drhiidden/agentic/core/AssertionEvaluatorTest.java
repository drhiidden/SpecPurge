package com.drhiidden.agentic.core;

import com.drhiidden.agentic.model.Assertion;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssertionEvaluatorTest {

    private static final String BODY = """
        {"locked": true, "name": "T-123",
         "versions": [
           {"n": 3, "isDefault": true},
           {"n": 2, "isDefault": false}]}
        """;

    private List<String> eval(String path, String op, Object expected) {
        return AssertionEvaluator.evaluate(BODY, List.of(new Assertion(path, op, expected)));
    }

    @Test
    void equalsAndExists() {
        assertTrue(eval("$.locked", "equals", true).isEmpty());
        assertEquals(1, eval("$.locked", "equals", false).size());
        assertTrue(eval("$.name", "exists", null).isEmpty());
        assertEquals(1, eval("$.nope", "exists", null).size());
        assertTrue(eval("$.nope", "notExists", null).isEmpty());
    }

    @Test
    void countWithFilterExpression() {
        assertTrue(eval("$.versions[?(@.isDefault==true)]", "count", 1).isEmpty());
        assertEquals(1, eval("$.versions[?(@.isDefault==true)]", "count", 2).size());
        assertTrue(eval("$.versions", "minCount", 2).isEmpty());
        assertEquals(1, eval("$.versions", "maxCount", 1).size());
    }

    @Test
    void matchesNotEmptyAndContains() {
        assertTrue(eval("$.name", "matches", "^T-\\d+$").isEmpty());
        assertTrue(eval("$.versions", "notEmpty", null).isEmpty());
        assertTrue(eval("$.versions[*].n", "contains", 2).isEmpty());
        assertEquals(1, eval("$.versions[*].n", "contains", 9).size());
    }

    @Test
    void unknownOperatorIsReported() {
        assertEquals(1, eval("$.name", "weird", null).size());
    }
}
