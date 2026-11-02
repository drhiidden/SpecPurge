package com.drhiidden.agentic;

import com.drhiidden.agentic.core.CaseLoader;
import com.drhiidden.agentic.core.JwtInfo;
import com.drhiidden.agentic.core.OpenApiParser;
import com.drhiidden.agentic.core.ReportGenerator;
import com.drhiidden.agentic.core.ReportWriters;
import com.drhiidden.agentic.core.RequestGenerator;
import com.drhiidden.agentic.core.TestExecutor;
import com.drhiidden.agentic.model.ApiSpec;
import com.drhiidden.agentic.model.EndpointInfo;
import com.drhiidden.agentic.model.TestRequest;
import com.drhiidden.agentic.model.TestResult;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * SpecSurge: smoke y verificación de APIs desde OpenAPI.
 *
 * Modos:
 * - Generado (por defecto): una petición por endpoint del OpenAPI.
 * - Casos ({@code --cases fichero.yml}): peticiones y aserciones declaradas a mano.
 *
 * Extras: Bearer por variable de entorno, solo lectura, validación contra el esquema de respuesta,
 * modo diferencial (baseline vs candidato) y salidas HTML / JUnit / JSON.
 */
@Slf4j
public class AgenticTestRunner {

    private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1", "::1", "[::1]");

    public static void main(String[] args) {
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log.info("🤖 SPECSURGE · OpenAPI smoke tester");
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        try {
            Config config = parseArgs(args);
            System.exit(run(config));
        } catch (Exception e) {
            log.error("❌ Fatal error", e);
            System.exit(2);
        }
    }

    /** Devuelve el exit code: 0 OK, 1 fallos en pruebas, 2 error de uso/entorno. */
    static int run(Config config) throws Exception {
        // 1) Token: SOLO desde variable de entorno (nunca por argumento de CLI)
        String bearerToken = null;
        if (config.bearerEnv() != null) {
            bearerToken = System.getenv(config.bearerEnv());
            if (bearerToken == null || bearerToken.isBlank()) {
                log.error("❌ La variable de entorno {} (--bearer-env) está vacía o no existe", config.bearerEnv());
                return 2;
            }
            bearerToken = bearerToken.trim();
            var exp = JwtInfo.expiry(bearerToken);
            if (exp.isPresent()) {
                Duration left = Duration.between(Instant.now(), exp.get());
                if (left.isNegative()) {
                    log.error("❌ El token de {} caducó hace {} min: genera uno nuevo (un 401 sería ruido)",
                        config.bearerEnv(), -left.toMinutes());
                    return 2;
                }
                if (left.toMinutes() < 10) {
                    log.warn("⚠️  El token de {} caduca en {} min", config.bearerEnv(), left.toMinutes());
                }
            }
        }

        // 2) Guardarraíles
        if (bearerToken != null && !config.readOnly() && !config.allowWrites()) {
            log.error("❌ Con --bearer-env se exige --read-only (solo GET/HEAD) o --allow-writes explícito");
            return 2;
        }
        List<String> urls = new ArrayList<>(List.of(config.baseUrl()));
        if (config.compareBaseUrl() != null) urls.add(config.compareBaseUrl());
        for (String url : urls) {
            String host = URI.create(url).getHost();
            boolean remote = host == null || !LOCAL_HOSTS.contains(host.toLowerCase());
            if (remote && (bearerToken != null || !config.readOnly()) && !config.allowHosts().contains(host)) {
                log.error("❌ Host remoto '{}': con token o con escrituras hay que autorizarlo con --allow-host {}", host, host);
                return 2;
            }
        }

        // 3) Spec (opcional en modo casos)
        ApiSpec spec = null;
        if (config.casesFile() == null || config.specGiven()) {
            log.info("📡 Fetching OpenAPI spec from: {}", config.specUrl());
            spec = new OpenApiParser().parseFromUrl(config.specUrl());
            log.info("✓ Parsed {} endpoints from OpenAPI spec", spec.getEndpoints().size());
        }

        // 4) Peticiones
        List<TestRequest> testRequests;
        List<String> skipped = new ArrayList<>();
        if (config.casesFile() != null) {
            testRequests = CaseLoader.load(Paths.get(config.casesFile()), spec, config.params(), config.validateSchema(), skipped);
            if (config.readOnly()) {
                int before = testRequests.size();
                testRequests = testRequests.stream().filter(r -> List.of("GET", "HEAD").contains(r.getMethod())).toList();
                if (testRequests.size() < before) {
                    log.warn("⏭️  {} caso(s) con escritura omitidos por --read-only", before - testRequests.size());
                }
            }
        } else {
            List<EndpointInfo> selected = selectEndpoints(spec, config);
            log.info("✓ Selected {} endpoints (protected: {}, read-only: {}, include: {})",
                selected.size(), config.includeProtected(), config.readOnly(),
                config.includePattern() != null ? config.includePattern().pattern() : "-");
            if (selected.isEmpty()) {
                log.error("❌ Ningún endpoint seleccionado (revisa --include / --include-protected / --read-only)");
                return 2;
            }
            applyParams(selected, config.params());
            testRequests = new RequestGenerator(new ApiSpec(spec.getTitle(), spec.getVersion(), selected))
                .withSchemaValidation(config.validateSchema())
                .generateAllRequests(config.includeProtected());
        }
        skipped.forEach(s -> log.warn("⏭️  OMITIDO: {}", s));
        log.info("✓ {} peticiones listas", testRequests.size());
        if (testRequests.isEmpty()) {
            log.error("❌ Nada que ejecutar");
            return 2;
        }

        // 5) Ejecución
        TestExecutor executor = new TestExecutor(new TestExecutor.Options(
            config.baseUrl(), config.compareBaseUrl(), bearerToken, config.bearerEnv(),
            config.failOnAuth(), config.strict(), config.failOnDiff(), config.delayMs(), config.maxRequests()));
        List<TestResult> results = executor.executeAll(testRequests);

        // 6) Informes
        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("Candidato", config.baseUrl());
        if (config.compareBaseUrl() != null) meta.put("Baseline", config.compareBaseUrl());
        meta.put("Modo", (config.casesFile() != null ? "casos" : "generado")
            + (config.readOnly() ? " · solo lectura" : "")
            + (bearerToken != null ? " · con token" : " · sin token")
            + (config.validateSchema() ? " · esquema" : ""));
        if (!skipped.isEmpty()) meta.put("Omitidos", String.valueOf(skipped.size()));

        Path reportPath = null;
        for (String fmt : config.formats()) {
            switch (fmt) {
                case "html" -> reportPath = new ReportGenerator().generateHtmlReport(results, config.outputDir(), meta);
                case "junit" -> log.info("JUnit: {}", ReportWriters.writeJunit(results, config.outputDir()).toAbsolutePath());
                case "json" -> log.info("JSON: {}", ReportWriters.writeJson(results, config.outputDir()).toAbsolutePath());
                default -> log.warn("Formato desconocido: {}", fmt);
            }
        }

        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        if (reportPath != null) log.info("✅ REPORT GENERATED: {}", reportPath.toAbsolutePath());
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        long failures = results.stream().filter(r -> !r.isPassed()).count();
        return failures > 0 ? 1 : 0;
    }

    static List<EndpointInfo> selectEndpoints(ApiSpec spec, Config config) {
        return spec.getEndpoints().stream()
            .filter(e -> config.includeProtected() || !e.isRequiresAuth())
            .filter(e -> !config.readOnly() || List.of("GET", "HEAD").contains(e.getMethod()))
            .filter(e -> config.includePattern() == null || config.includePattern().matcher(e.getPath()).find())
            .toList();
    }

    /** Sobrescribe valores de parámetros de path/query con los indicados en --param nombre=valor. */
    static void applyParams(List<EndpointInfo> endpoints, Map<String, String> params) {
        if (params.isEmpty()) return;
        for (EndpointInfo e : endpoints) {
            params.forEach((k, v) -> {
                if (e.getPathParams() != null && e.getPathParams().containsKey(k)) {
                    e.getPathParams().put(k, v);
                }
                if (e.getQueryParams() != null && e.getQueryParams().containsKey(k)) {
                    e.getQueryParams().put(k, v);
                }
            });
        }
    }

    static Config parseArgs(String[] args) {
        String specUrl = "http://localhost:8082/v3/api-docs";
        boolean specGiven = false;
        String baseUrl = "http://localhost:8082";
        String compareBaseUrl = null;
        String outputDir = "./test-reports";
        String casesFile = null;
        String bearerEnv = null;
        boolean readOnly = false;
        boolean allowWrites = false;
        boolean includeProtected = false;
        boolean failOnAuth = false;
        boolean failOnDiff = false;
        boolean validateSchema = false;
        boolean strict = false;
        long delayMs = 0;
        int maxRequests = 500;
        Pattern includePattern = null;
        List<String> formats = new ArrayList<>(List.of("html"));
        Set<String> allowHosts = new java.util.HashSet<>();
        Map<String, String> params = new LinkedHashMap<>();

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--spec" -> { specUrl = args[++i]; specGiven = true; }
                case "--base-url" -> baseUrl = args[++i];
                case "--compare-base-url" -> compareBaseUrl = args[++i];
                case "--output" -> outputDir = args[++i];
                case "--cases" -> casesFile = args[++i];
                case "--bearer-env" -> bearerEnv = args[++i];
                case "--read-only" -> readOnly = true;
                case "--allow-writes" -> allowWrites = true;
                case "--include-protected" -> includeProtected = true;
                case "--fail-on-auth" -> failOnAuth = true;
                case "--fail-on-diff" -> failOnDiff = true;
                case "--validate-schema" -> validateSchema = true;
                case "--strict" -> strict = true;
                case "--delay-ms" -> delayMs = Long.parseLong(args[++i]);
                case "--max-requests" -> maxRequests = Integer.parseInt(args[++i]);
                case "--include" -> includePattern = Pattern.compile(args[++i]);
                case "--format" -> formats = new ArrayList<>(List.of(args[++i].split(",")));
                case "--allow-host" -> allowHosts.add(args[++i]);
                case "--param" -> {
                    String kv = args[++i];
                    int eq = kv.indexOf('=');
                    if (eq <= 0) throw new IllegalArgumentException("--param espera nombre=valor, recibido: " + kv);
                    params.put(kv.substring(0, eq), kv.substring(eq + 1));
                }
                case "--help" -> {
                    printHelp();
                    System.exit(0);
                }
                default -> throw new IllegalArgumentException("Opción desconocida: " + args[i]);
            }
        }

        return new Config(specUrl, specGiven, baseUrl, compareBaseUrl, outputDir, casesFile, bearerEnv, readOnly,
            allowWrites, includeProtected, failOnAuth, failOnDiff, validateSchema, strict, delayMs, maxRequests,
            includePattern, formats, allowHosts, params);
    }

    private static void printHelp() {
        System.out.println("""
            SpecSurge - OpenAPI smoke tester

            Usage:
              java -jar specsurge-core.jar [OPTIONS]

            Origen
              --spec URL|FILE        OpenAPI: URL o fichero local (default: http://localhost:8082/v3/api-docs)
              --cases FILE.yml       Casos declarativos con aserciones (docs/cases.md); --spec es opcional
              --base-url URL         API candidata (default: http://localhost:8082)
              --compare-base-url URL API baseline: ejecuta lo mismo y lista diferencias (modo diferencial)

            Seguridad
              --bearer-env VAR       Authorization: Bearer $VAR (el token se lee del entorno, nunca por argumento)
              --read-only            Solo GET/HEAD
              --allow-writes         Permite escrituras junto a --bearer-env (por defecto se rechaza)
              --allow-host HOST      Autoriza un host remoto (obligatorio con token o escrituras; repetible)
              --max-requests N       Tope de peticiones (default 500)
              --delay-ms N           Pausa entre peticiones

            Selección (modo generado)
              --include-protected    Incluye endpoints con 'security' (por defecto solo públicos)
              --include REGEX        Solo rutas que casen con la expresión
              --param K=V            Valor real para parámetros de path/query/vars (repetible)

            Criterios
              --fail-on-auth         401 cuenta como fallo (403 siempre lo es)
              --strict               En modo generado, 404 no cuenta como correcto
              --validate-schema      Valida la respuesta 2xx contra el esquema del OpenAPI
              --fail-on-diff         Cualquier diferencia baseline/candidato es fallo

            Salida
              --output DIR           Carpeta de informes (default: ./test-reports)
              --format LIST          html,junit,json (default: html)
              --help                 Muestra esta ayuda
            """);
    }

    record Config(String specUrl, boolean specGiven, String baseUrl, String compareBaseUrl, String outputDir,
                  String casesFile, String bearerEnv, boolean readOnly, boolean allowWrites,
                  boolean includeProtected, boolean failOnAuth, boolean failOnDiff, boolean validateSchema,
                  boolean strict, long delayMs, int maxRequests, Pattern includePattern, List<String> formats,
                  Set<String> allowHosts, Map<String, String> params) {}
}
