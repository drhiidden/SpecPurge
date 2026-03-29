package com.drhiidden.agentic.core;

import com.drhiidden.agentic.model.TestResult;
import freemarker.template.Configuration;
import freemarker.template.Template;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Genera reportes HTML con cobertura y resultados.
 * 
 * Features:
 * - Coverage por endpoint
 * - Gráficos de success rate
 * - Response times
 * - Detalles de failures
 */
@Slf4j
public class ReportGenerator {
    
    private final Configuration freemarkerConfig;
    
    public ReportGenerator() {
        this.freemarkerConfig = new Configuration(Configuration.VERSION_2_3_32);
        this.freemarkerConfig.setClassForTemplateLoading(this.getClass(), "/templates");
        this.freemarkerConfig.setDefaultEncoding("UTF-8");
    }
    
    public Path generateHtmlReport(List<TestResult> results, String outputDir) throws IOException {
        Path outputPath = Paths.get(outputDir);
        Files.createDirectories(outputPath);
        
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path reportFile = outputPath.resolve("test-report-" + timestamp + ".html");
        
        Map<String, Object> data = buildReportData(results);
        
        String html = renderHtmlReport(data);
        
        Files.writeString(reportFile, html);
        
        log.info("Report generated: {}", reportFile.toAbsolutePath());
        return reportFile;
    }
    
    private Map<String, Object> buildReportData(List<TestResult> results) {
        Map<String, Object> data = new HashMap<>();
        
        long passed = results.stream().filter(TestResult::isPassed).count();
        long failed = results.size() - passed;
        double successRate = (double) passed / results.size() * 100;
        
        data.put("totalTests", results.size());
        data.put("passed", passed);
        data.put("failed", failed);
        data.put("successRate", String.format("%.1f", successRate));
        data.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        
        // Group by method
        Map<String, Long> byMethod = results.stream()
            .collect(Collectors.groupingBy(TestResult::getMethod, Collectors.counting()));
        data.put("byMethod", byMethod);
        
        // Group by status category
        long successes = results.stream().filter(TestResult::isSuccess).count();
        long clientErrors = results.stream().filter(TestResult::isClientError).count();
        long serverErrors = results.stream().filter(TestResult::isServerError).count();
        
        data.put("successes", successes);
        data.put("clientErrors", clientErrors);
        data.put("serverErrors", serverErrors);
        
        // All results
        data.put("results", results);
        
        // Failed tests only
        data.put("failures", results.stream().filter(r -> !r.isPassed()).toList());
        
        // Performance stats
        double avgDuration = results.stream()
            .mapToLong(r -> r.getDuration().toMillis())
            .average()
            .orElse(0.0);
        data.put("avgDuration", String.format("%.0f", avgDuration));
        
        return data;
    }
    
    private String renderHtmlReport(Map<String, Object> data) {
        // Generate HTML inline (sin FreeMarker template file por simplicidad)
        StringBuilder html = new StringBuilder();
        
        html.append("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <title>Agentic API Test Report</title>
                <style>
                    * { margin: 0; padding: 0; box-sizing: border-box; }
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                        padding: 40px 20px;
                        color: #1a202c;
                    }
                    .container {
                        max-width: 1200px;
                        margin: 0 auto;
                        background: white;
                        border-radius: 16px;
                        box-shadow: 0 20px 60px rgba(0,0,0,0.3);
                        overflow: hidden;
                    }
                    .header {
                        background: linear-gradient(135deg, #1a202c 0%, #2d3748 100%);
                        color: white;
                        padding: 40px;
                        text-align: center;
                    }
                    .header h1 {
                        font-size: 2.5rem;
                        margin-bottom: 10px;
                        font-weight: 700;
                    }
                    .header .subtitle {
                        font-size: 1.1rem;
                        opacity: 0.8;
                    }
                    .stats {
                        display: grid;
                        grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
                        gap: 20px;
                        padding: 40px;
                        background: #f7fafc;
                    }
                    .stat-card {
                        background: white;
                        padding: 24px;
                        border-radius: 12px;
                        box-shadow: 0 2px 8px rgba(0,0,0,0.1);
                        text-align: center;
                    }
                    .stat-value {
                        font-size: 3rem;
                        font-weight: 700;
                        margin-bottom: 8px;
                    }
                    .stat-value.passed { color: #48bb78; }
                    .stat-value.failed { color: #f56565; }
                    .stat-value.rate { color: #667eea; }
                    .stat-label {
                        font-size: 0.9rem;
                        color: #718096;
                        text-transform: uppercase;
                        letter-spacing: 1px;
                        font-weight: 600;
                    }
                    .results {
                        padding: 40px;
                    }
                    .results h2 {
                        font-size: 1.8rem;
                        margin-bottom: 24px;
                        color: #2d3748;
                    }
                    table {
                        width: 100%;
                        border-collapse: collapse;
                        margin-bottom: 40px;
                    }
                    thead {
                        background: #2d3748;
                        color: white;
                    }
                    th {
                        padding: 16px;
                        text-align: left;
                        font-weight: 600;
                        font-size: 0.85rem;
                        text-transform: uppercase;
                        letter-spacing: 0.5px;
                    }
                    td {
                        padding: 16px;
                        border-bottom: 1px solid #e2e8f0;
                    }
                    tbody tr:hover {
                        background: #f7fafc;
                    }
                    .badge {
                        display: inline-block;
                        padding: 4px 12px;
                        border-radius: 12px;
                        font-size: 0.75rem;
                        font-weight: 600;
                        text-transform: uppercase;
                    }
                    .badge.success { background: #c6f6d5; color: #22543d; }
                    .badge.error { background: #fed7d7; color: #742a2a; }
                    .badge.warning { background: #feebc8; color: #7c2d12; }
                    .method {
                        font-weight: 700;
                        font-size: 0.85rem;
                        padding: 4px 8px;
                        border-radius: 4px;
                        display: inline-block;
                        min-width: 60px;
                        text-align: center;
                    }
                    .method.get { background: #bee3f8; color: #2c5282; }
                    .method.post { background: #c6f6d5; color: #22543d; }
                    .method.put { background: #feebc8; color: #7c2d12; }
                    .method.patch { background: #e9d8fd; color: #44337a; }
                    .method.delete { background: #fed7d7; color: #742a2a; }
                    .footer {
                        padding: 24px 40px;
                        background: #f7fafc;
                        color: #718096;
                        text-align: center;
                        font-size: 0.9rem;
                    }
                    code {
                        background: #2d3748;
                        color: #48bb78;
                        padding: 2px 6px;
                        border-radius: 4px;
                        font-size: 0.85rem;
                        font-family: 'Monaco', 'Courier New', monospace;
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>🤖 Agentic API Test Report</h1>
                        <div class="subtitle">Automatic test generation from OpenAPI specification</div>
                    </div>
                    
                    <div class="stats">
                        <div class="stat-card">
                            <div class="stat-value passed">""")
            .append(data.get("passed"))
            .append("""
                </div>
                            <div class="stat-label">Passed</div>
                        </div>
                        <div class="stat-card">
                            <div class="stat-value failed">""")
            .append(data.get("failed"))
            .append("""
                </div>
                            <div class="stat-label">Failed</div>
                        </div>
                        <div class="stat-card">
                            <div class="stat-value rate">""")
            .append(data.get("successRate"))
            .append("""
                %</div>
                            <div class="stat-label">Success Rate</div>
                        </div>
                        <div class="stat-card">
                            <div class="stat-value">""")
            .append(data.get("totalTests"))
            .append("""
                </div>
                            <div class="stat-label">Total Tests</div>
                        </div>
                    </div>
                    
                    <div class="results">
                        <h2>Test Results</h2>
                        <table>
                            <thead>
                                <tr>
                                    <th>Method</th>
                                    <th>Endpoint</th>
                                    <th>Status</th>
                                    <th>Expected</th>
                                    <th>Duration</th>
                                    <th>Result</th>
                                </tr>
                            </thead>
                            <tbody>
            """);
        
        @SuppressWarnings("unchecked")
        List<TestResult> results = (List<TestResult>) data.get("results");
        
        for (TestResult result : results) {
            String methodClass = result.getMethod().toLowerCase();
            String statusBadge = result.isPassed() ? "success" : "error";
            String statusText = result.isPassed() ? "PASS" : "FAIL";
            
            html.append(String.format("""
                                <tr>
                                    <td><span class="method %s">%s</span></td>
                                    <td><code>%s</code></td>
                                    <td>%d</td>
                                    <td>%d</td>
                                    <td>%dms</td>
                                    <td><span class="badge %s">%s</span></td>
                                </tr>
                """,
                methodClass, result.getMethod(),
                result.getPath(),
                result.getActualStatus(),
                result.getExpectedStatus(),
                result.getDuration().toMillis(),
                statusBadge, statusText
            ));
        }
        
        html.append("""
                            </tbody>
                        </table>
                    </div>
                    
                    <div class="footer">
                        Generated by FSJ Agentic Test Generator v1.0.0<br>
                        Timestamp: """)
            .append(data.get("timestamp"))
            .append("""
                    </div>
                </div>
            </body>
            </html>
            """);
        
        return html.toString();
    }
}
