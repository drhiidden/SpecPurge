# AGENTS.md — SpecSurge (SpecPurge repo)

Guía para agentes IA y el propio autor trabajando en futuras versiones.

---

## Identidad del proyecto

| Campo | Valor |
|---|---|
| Nombre público | **SpecSurge** |
| Repo GitHub | `drhiidden/SpecPurge` |
| Tagline | *Zero-config API testing from OpenAPI specs* |
| Versión actual | `v1.0.0` (stable, released) |
| Licencia | MIT |
| Estado | OSS público — cualquier cambio va a `master` + release |

> **Confusión habitual**: el repo se llama `SpecPurge` (nombre histórico del proyecto) pero el producto se llama `SpecSurge`. El JAR, el README, los logs y la documentación siempre dicen `SpecSurge`. No mezclar.

---

## Stack

| Capa | Tecnología | Notas |
|---|---|---|
| Lenguaje | Java 21 | LTS, no bajar de versión |
| Build | Maven + Shade plugin | Produce fat-jar ejecutable |
| Parsing | swagger-parser v3 | OpenAPI 3.0 únicamente |
| Ejecución | Rest-Assured | Fluent HTTP DSL |
| Reporting | HTML inline (sin templates) | Hasta v1.2, luego Thymeleaf |
| Logging | SLF4J + Logback | |

**Package base**: `com.drhiidden.agentic`  
**Main class**: `com.drhiidden.agentic.AgenticTestRunner`  
**JAR output**: `target/openapi-test-generator-{version}.jar`

---

## Estructura del código

```
src/main/java/com/drhiidden/agentic/
├── AgenticTestRunner.java      ← Entry point CLI (args: --spec, --base-url, --output)
├── core/
│   ├── OpenApiParser.java      ← Descubre endpoints desde /v3/api-docs
│   ├── RequestGenerator.java   ← Genera payloads tipados (string, int, enum, email, uuid...)
│   ├── TestExecutor.java       ← Ejecuta requests con Rest-Assured, recoge resultados
│   └── ReportGenerator.java   ← Genera HTML con tabla de resultados y métricas
└── model/
    ├── ApiSpec.java            ← Metadata del API parseada
    ├── EndpointInfo.java       ← Un endpoint (path, method, params, schema)
    ├── TestRequest.java        ← Request generado para un endpoint
    └── TestResult.java         ← Resultado de ejecutar un TestRequest
```

**Flujo de datos**:
```
AgenticTestRunner
  → OpenApiParser     (OpenAPI spec → List<EndpointInfo>)
  → RequestGenerator  (List<EndpointInfo> → List<TestRequest>)
  → TestExecutor      (List<TestRequest> → List<TestResult>)
  → ReportGenerator   (List<TestResult> → test-report-{timestamp}.html)
```

---

## Lógica de negocio crítica (no romper)

### Clasificación de resultados

SpecSurge distingue fallos **esperados** de **bugs reales**:

```java
// PASS: éxito real
200, 201, 202, 204

// PASS: fallos esperados (no son bugs)
401 → endpoint protegido, normal sin auth
404 → recurso no existe aún, normal en test
400 → payload inválido (limitación del generador)

// FAIL: bug real
500 → error de servidor — SIEMPRE es un bug
Cualquier 5xx → reportar como fallo crítico
```

Esta clasificación está en `TestExecutor` y es el corazón del producto. Si se cambia, cambia el valor.

### Generación de payloads

`RequestGenerator` genera valores por tipo de campo:

| Tipo OpenAPI | Valor generado |
|---|---|
| `string` | `"test-string"` |
| `integer` / `number` | `1` |
| `boolean` | `true` |
| `string` + `format: email` | `"test@example.com"` |
| `string` + `format: uuid` | UUID aleatorio |
| `string` + `format: date-time` | ISO 8601 actual |
| `enum` | Primer valor de la lista |

En v1.1 esto lo mejora un LLM — el generador actual es intencional por simplicidad.

---

## Roadmap de versiones

### v1.0.0 ✅ Released
- OpenAPI 3.0 parsing completo
- Payload generation simple
- Ejecución y HTML report
- CLI funcional (`--spec`, `--base-url`, `--output`)

### v1.1 (próxima)
- [ ] Autenticación: flujo login → JWT → usar token en headers
- [ ] AI payload generation (Ollama/OpenAI) para schemas complejos
- [ ] Validación de response schema (no solo status code)

### v1.2
- [ ] Ejecución paralela configurable (`--concurrency`)
- [ ] Contract diff: comparar dos runs (`./before/` vs `./after/`)
- [ ] Performance assertions (`--max-duration 500ms`)

### v1.3
- [ ] SaaS UI — drag-and-drop OpenAPI → report instantáneo
- [ ] Notificaciones Slack/Discord/Webhook
- [ ] Migrar ReportGenerator a Thymeleaf (cuando los reports sean complejos)

---

## Workflow para releases

```bash
# 1. Actualizar versión en pom.xml
# <version>1.1.0</version>

# 2. Compilar fat-jar
mvn clean package

# 3. Verificar el JAR funciona
java -jar target/openapi-test-generator-1.1.0.jar --help

# 4. Tag + push
git tag v1.1.0
git push origin v1.1.0

# 5. GitHub Release con el JAR adjunto
gh release create v1.1.0 \
  target/openapi-test-generator-1.1.0.jar \
  --title "SpecSurge v1.1.0 — AI Payloads + JWT Auth" \
  --notes "..."
```

> **No usar Maven Central**: SpecSurge es un CLI tool, no una librería. Los usuarios lo descargan y ejecutan, no lo incluyen como dependencia.

---

## Reglas para no cometer errores

1. **El nombre es SpecSurge, el repo es SpecPurge** — en docs, logs y mensajes siempre `SpecSurge`
2. **No renombrar el package** `com.drhiidden.agentic` — rompe el JAR existente
3. **No cambiar el JAR output name** sin actualizar el QUICK-START y el release
4. **No trackear `.cursor/` ni `.procontext/`** — están en `.gitignore`, son locales
5. **No hardcodear APIs de ejemplo específicas** en tests o docs — usar `http://localhost:8082` o `SampleShop API` como genérico
6. **Cada release = tag git + GitHub Release con JAR adjunto** — no solo un commit
7. **Los 401/404 son PASS** — no son bugs en el contexto de SpecSurge

---

## Metodología

Desarrollado con [HCP (Human-Code-AI Protocol)](https://github.com/haletheia/human-code-ai-protocol).  
Contexto local del proyecto en `.procontext/` (ignorado por git).
