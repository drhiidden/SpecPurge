package com.drhiidden.agentic.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Compara dos respuestas JSON (baseline vs candidato) y lista las diferencias por ruta. */
public final class DiffEngine {

    private static final int MAX_DIFFS = 40;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private DiffEngine() {}

    public static List<String> diff(String baselineBody, String candidateBody, List<String> ignoreFields) {
        Set<String> ignore = ignoreFields == null ? Set.of() : Set.copyOf(ignoreFields);
        List<String> out = new ArrayList<>();
        try {
            JsonNode a = MAPPER.readTree(baselineBody == null || baselineBody.isBlank() ? "null" : baselineBody);
            JsonNode b = MAPPER.readTree(candidateBody == null || candidateBody.isBlank() ? "null" : candidateBody);
            walk("$", a, b, ignore, out);
        } catch (Exception e) {
            if (!String.valueOf(baselineBody).equals(String.valueOf(candidateBody))) {
                out.add("$: cuerpos no JSON distintos");
            }
        }
        return out;
    }

    private static void walk(String path, JsonNode a, JsonNode b, Set<String> ignore, List<String> out) {
        if (out.size() >= MAX_DIFFS) return;
        if (a == null && b == null) return;
        if (a == null) { out.add(path + ": solo en candidato = " + brief(b)); return; }
        if (b == null) { out.add(path + ": solo en baseline = " + brief(a)); return; }
        if (a.isObject() && b.isObject()) {
            Set<String> names = new TreeSet<>();
            for (Iterator<String> it = a.fieldNames(); it.hasNext(); ) names.add(it.next());
            for (Iterator<String> it = b.fieldNames(); it.hasNext(); ) names.add(it.next());
            for (String name : names) {
                if (ignore.contains(name)) continue;
                walk(path + "." + name, a.get(name), b.get(name), ignore, out);
            }
        } else if (a.isArray() && b.isArray()) {
            if (a.size() != b.size()) out.add(path + ": " + a.size() + " elemento(s) en baseline vs " + b.size() + " en candidato");
            int n = Math.min(a.size(), b.size());
            for (int i = 0; i < n; i++) walk(path + "[" + i + "]", a.get(i), b.get(i), ignore, out);
        } else if (!a.equals(b)) {
            out.add(path + ": baseline=" + brief(a) + " candidato=" + brief(b));
        }
    }

    private static String brief(JsonNode n) {
        String s = n.toString();
        return s.length() > 80 ? s.substring(0, 77) + "..." : s;
    }
}
