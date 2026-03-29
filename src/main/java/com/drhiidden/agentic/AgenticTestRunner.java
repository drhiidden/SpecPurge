package com.drhiidden.agentic;

import com.drhiidden.agentic.core.OpenApiParser;
import com.drhiidden.agentic.core.RequestGenerator;
import com.drhiidden.agentic.core.TestExecutor;
import com.drhiidden.agentic.core.ReportGenerator;
import com.drhiidden.agentic.model.ApiSpec;
import com.drhiidden.agentic.model.TestResult;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Generador Agéntico de Tests desde OpenAPI.
 * 
 * Workflow:
 * 1. Fetch OpenAPI spec desde /v3/api-docs
 * 2. Parse endpoints + schemas
 * 3. Generar payloads automáticamente (con AI si es complejo)
 * 4. Ejecutar requests contra API
 * 5. Reportar coverage + resultados en HTML
 * 
 * Uso:
 * <pre>
 * java -jar agentic-test-generator.jar \
 *   --spec http://localhost:8082/v3/api-docs \
 *   --output ./test-reports/
 * </pre>
 */
@Slf4j
public class AgenticTestRunner {
    
    public static void main(String[] args) {
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log.info("🤖 AGENTIC OPENAPI TEST GENERATOR");
        log.info("   AI-Powered Automatic API Testing for 2026");
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        
        try {
            // Parse CLI args
            Config config = parseArgs(args);
            
            log.info("📡 Fetching OpenAPI spec from: {}", config.specUrl());
            
            // 1. Parse OpenAPI
            OpenApiParser parser = new OpenApiParser();
            ApiSpec spec = parser.parseFromUrl(config.specUrl());
            
            log.info("✓ Parsed {} endpoints from OpenAPI spec", spec.getEndpoints().size());
            
            // 2. Generate test requests
            RequestGenerator generator = new RequestGenerator(spec);
            var testRequests = generator.generateAllRequests();
            
            log.info("✓ Generated {} test requests", testRequests.size());
            
            // 3. Execute tests
            TestExecutor executor = new TestExecutor(config.baseUrl());
            List<TestResult> results = executor.executeAll(testRequests);
            
            log.info("✓ Executed {} tests", results.size());
            
            // 4. Generate HTML report
            ReportGenerator reporter = new ReportGenerator();
            Path reportPath = reporter.generateHtmlReport(results, config.outputDir());
            
            log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            log.info("✅ REPORT GENERATED: {}", reportPath.toAbsolutePath());
            log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            
            // Exit code based on results
            long failures = results.stream().filter(r -> !r.isPassed()).count();
            System.exit(failures > 0 ? 1 : 0);
            
        } catch (Exception e) {
            log.error("❌ Fatal error", e);
            System.exit(2);
        }
    }
    
    private static Config parseArgs(String[] args) {
        String specUrl = "http://localhost:8082/v3/api-docs";
        String baseUrl = "http://localhost:8082";
        String outputDir = "./test-reports";
        
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--spec" -> specUrl = args[++i];
                case "--base-url" -> baseUrl = args[++i];
                case "--output" -> outputDir = args[++i];
                case "--help" -> {
                    printHelp();
                    System.exit(0);
                }
            }
        }
        
        return new Config(specUrl, baseUrl, outputDir);
    }
    
    private static void printHelp() {
        System.out.println("""
            Agentic OpenAPI Test Generator
            
            Usage:
              java -jar openapi-test-generator.jar [OPTIONS]
            
            Options:
              --spec URL        OpenAPI spec URL (default: http://localhost:8082/v3/api-docs)
              --base-url URL    API base URL (default: http://localhost:8082)
              --output DIR      Output directory for reports (default: ./test-reports)
              --help            Show this help
            
            Example:
              java -jar openapi-test-generator.jar \\
                --spec http://localhost:8082/v3/api-docs \\
                --output ./reports/
            """);
    }
    
    private record Config(String specUrl, String baseUrl, String outputDir) {}
}
