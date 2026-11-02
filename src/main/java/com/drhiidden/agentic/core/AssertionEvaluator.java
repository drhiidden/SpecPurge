package com.drhiidden.agentic.core;

import com.drhiidden.agentic.model.Assertion;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Evalúa aserciones JsonPath sobre el cuerpo de una respuesta. Devuelve la lista de fallos (vacía = OK). */
public final class AssertionEvaluator {

    private AssertionEvaluator() {}

    public static List<String> evaluate(String body, List<Assertion> assertions) {
        List<String> failures = new ArrayList<>();
        if (assertions == null || assertions.isEmpty()) return failures;
        for (Assertion a : assertions) {
            String failure = evaluateOne(body, a);
            if (failure != null) failures.add(failure);
        }
        return failures;
    }

    private static String evaluateOne(String body, Assertion a) {
        Object value;
        boolean present = true;
        try {
            value = JsonPath.read(body == null ? "" : body, a.path());
        } catch (PathNotFoundException e) {
            value = null;
            present = false;
        } catch (Exception e) {
            return "aserción [" + a + "]: no se pudo evaluar (" + e.getClass().getSimpleName() + ")";
        }

        switch (a.operator()) {
            case "exists":
                boolean wantExists = a.expected() == null || Boolean.parseBoolean(String.valueOf(a.expected()));
                return present == wantExists ? null : "[" + a.path() + "] " + (wantExists ? "no existe" : "existe y no debería");
            case "notExists":
                return present ? "[" + a.path() + "] existe y no debería" : null;
            case "equals":
                if (!present) return "[" + a.path() + "] no existe (esperado " + a.expected() + ")";
                return same(value, a.expected()) ? null : "[" + a.path() + "] = " + value + " (esperado " + a.expected() + ")";
            case "notEquals":
                return present && same(value, a.expected()) ? "[" + a.path() + "] = " + value + " (no debería)" : null;
            case "count":
                return size(value, present) == toLong(a.expected()) ? null
                    : "[" + a.path() + "] tiene " + size(value, present) + " elemento(s) (esperado " + a.expected() + ")";
            case "minCount":
                return size(value, present) >= toLong(a.expected()) ? null
                    : "[" + a.path() + "] tiene " + size(value, present) + " elemento(s) (mínimo " + a.expected() + ")";
            case "maxCount":
                return size(value, present) <= toLong(a.expected()) ? null
                    : "[" + a.path() + "] tiene " + size(value, present) + " elemento(s) (máximo " + a.expected() + ")";
            case "notEmpty":
                return present && size(value, true) > 0 && !"".equals(value) ? null : "[" + a.path() + "] vacío o ausente";
            case "matches":
                return present && Pattern.compile(String.valueOf(a.expected())).matcher(String.valueOf(value)).find() ? null
                    : "[" + a.path() + "] = " + value + " no casa con /" + a.expected() + "/";
            case "contains":
                if (!present) return "[" + a.path() + "] no existe";
                boolean ok = value instanceof Collection<?> c
                    ? c.stream().anyMatch(e -> same(e, a.expected()))
                    : String.valueOf(value).contains(String.valueOf(a.expected()));
                return ok ? null : "[" + a.path() + "] no contiene " + a.expected();
            default:
                return "operador desconocido: " + a.operator();
        }
    }

    private static boolean same(Object actual, Object expected) {
        if (actual == null || expected == null) return actual == expected;
        if (actual instanceof Number an && expected instanceof Number en) {
            return Double.compare(an.doubleValue(), en.doubleValue()) == 0;
        }
        if (actual instanceof Map || actual instanceof Collection) return actual.equals(expected);
        return String.valueOf(actual).equals(String.valueOf(expected));
    }

    private static long size(Object value, boolean present) {
        if (!present || value == null) return 0;
        if (value instanceof Collection<?> c) return c.size();
        if (value instanceof Map<?, ?> m) return m.size();
        return 1;
    }

    private static long toLong(Object o) {
        return o instanceof Number n ? n.longValue() : Long.parseLong(String.valueOf(o));
    }
}
