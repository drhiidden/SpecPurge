package com.drhiidden.agentic.core;

import com.drhiidden.agentic.model.TestResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Salidas máquina-legibles: JUnit XML (CI) y JSON. */
public final class ReportWriters {

    private ReportWriters() {}

    public static Path writeJunit(List<TestResult> results, String outputDir) throws IOException {
        Path dir = Paths.get(outputDir);
        Files.createDirectories(dir);
        Path file = dir.resolve("junit-" + stamp() + ".xml");

        long failures = results.stream().filter(r -> !r.isPassed()).count();
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<testsuite name=\"specsurge\" tests=\"").append(results.size())
            .append("\" failures=\"").append(failures).append("\">\n");
        for (TestResult r : results) {
            String name = r.getName() != null ? r.getName() : r.getMethod() + " " + r.getPath();
            xml.append("  <testcase classname=\"").append(escAttr(r.getMethod())).append("\" name=\"").append(escAttr(name))
                .append("\" time=\"").append(r.getDuration().toMillis() / 1000.0).append("\"");
            if (r.isPassed()) {
                xml.append("/>\n");
            } else {
                List<String> lines = new ArrayList<>();
                lines.add("HTTP " + r.getActualStatus() + " (esperado " + r.getExpectedStatus() + ")");
                if (r.getErrorMessage() != null) lines.add(r.getErrorMessage());
                lines.addAll(r.getViolations());
                lines.addAll(r.getDiffs());
                xml.append(">\n    <failure message=\"").append(escAttr(lines.get(0))).append("\">")
                    .append(escText(String.join("\n", lines))).append("</failure>\n  </testcase>\n");
            }
        }
        xml.append("</testsuite>\n");
        Files.writeString(file, xml.toString());
        return file;
    }

    public static Path writeJson(List<TestResult> results, String outputDir) throws IOException {
        Path dir = Paths.get(outputDir);
        Files.createDirectories(dir);
        Path file = dir.resolve("results-" + stamp() + ".json");

        List<Map<String, Object>> rows = new ArrayList<>();
        for (TestResult r : results) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", r.getName());
            m.put("method", r.getMethod());
            m.put("path", r.getPath());
            m.put("operationId", r.getOperationId());
            m.put("passed", r.isPassed());
            m.put("expectedStatus", r.getExpectedStatus());
            m.put("actualStatus", r.getActualStatus());
            m.put("baselineStatus", r.getBaselineStatus());
            m.put("durationMs", r.getDuration().toMillis());
            m.put("errorMessage", r.getErrorMessage());
            m.put("violations", r.getViolations());
            m.put("diffs", r.getDiffs());
            m.put("curl", r.getCurl());
            rows.add(m);
        }
        new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT).writeValue(file.toFile(), rows);
        return file;
    }

    private static String stamp() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
    }

    private static String escAttr(String s) {
        return escText(s).replace("\"", "&quot;").replace("\n", " ");
    }

    private static String escText(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
