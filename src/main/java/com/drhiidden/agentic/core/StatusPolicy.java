package com.drhiidden.agentic.core;

import java.util.List;

/** Decide si un código HTTP cuenta como correcto. */
public final class StatusPolicy {

    private StatusPolicy() {}

    /**
     * Política flexible heredada de v1.0 (modo generado): 2xx siempre vale para expected=200, 404 se tolera
     * y 401 se tolera salvo {@code failOnAuth}. Con {@code strict} solo vale el esperado o cualquier 2xx.
     */
    public static boolean passes(int expected, int actual, boolean failOnAuth, boolean strict) {
        if (actual == expected) return true;
        if (expected == 200 && actual >= 200 && actual < 300) return true;
        if (strict) return false;
        if (expected == 200 && actual == 404) return true;
        return expected == 200 && actual == 401 && !failOnAuth;
    }

    /** Modo casos: el estado debe estar en la lista explícita. */
    public static boolean passes(List<Integer> accepted, int actual) {
        return accepted.contains(actual);
    }
}
