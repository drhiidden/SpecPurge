# Examples & Use Cases

## 1. Smoke Testing (CI/CD)

```bash
# Every commit
specsurge --spec $API_URL/v3/api-docs

# Exit code: 0 if no crashes, 1 if failures
# Duration: <30s for most APIs
```

**Beneficio**: Detecta regresiones antes de llegar a producción.

---

## 2. API Discovery (Onboarding)

```bash
# New developer onboarding
specsurge --spec https://api.internal.com/openapi.json

# See ALL endpoints in the HTML report
# Faster than reading docs
```

**Beneficio**: Mapa completo de la API en 15 segundos.

---

## 3. Regression Detection

```bash
# Before deploy
specsurge --spec $STAGING_URL/v3/api-docs --output ./before

# After deploy
specsurge --spec $STAGING_URL/v3/api-docs --output ./after

# Compare reports (breaking changes?)
diff before/test-report.html after/test-report.html
```

**Beneficio**: Detecta cambios inesperados en respuestas de la API.

---

## 4. Contract Validation

```bash
# Validate OpenAPI spec matches reality
specsurge --spec file://openapi.yaml --base-url http://localhost:8080

# Detects:
# - Missing endpoints (404)
# - Wrong response codes
# - Schema mismatches
```

**Beneficio**: Asegura que tu spec OpenAPI está sincronizado con el código real.

---

## Use Cases by Role

### For QA Engineers

```bash
# Test complete API surface in seconds
specsurge --spec $API/v3/api-docs

# Focus on real bugs (not 404s/401s)
# HTML report shows only actionable issues
```

### For Backend Developers

```bash
# Smoke test after changes
specsurge

# Instant feedback on breaking changes
# Exit code 1 if any 500s detected
```

### For DevOps

```bash
# Add to CI/CD pipeline
specsurge --spec $API/openapi.json --output $ARTIFACTS

# Historical reports for trend analysis
# Zero maintenance (syncs with API automatically)
```

### For API Designers

```bash
# Validate OpenAPI spec accuracy
specsurge --spec file://openapi.yaml --base-url http://localhost

# Ensures spec matches reality
# Finds undocumented endpoints
```

---

## Real Results: SampleShop API (Music Platform)

**Discovery:**
- ✅ 248 endpoints found (complete API map)
- ✅ 42 artist-related endpoints
- ✅ 8 CMS endpoints (news, carousels)
- ✅ 161 additional endpoints (battles, events, labels...)

**Quality:**
- ✅ 176 endpoints working (71% success)
- ⚠️ 54 require auth (expected behavior)
- ⚠️ 18 empty resources (expected, no data)
- ✅ 0 server crashes (500s)

**Performance:**
- ⚡ 15.2 seconds execution
- ⚡ 61ms average per request
- 📊 89KB HTML report

**Time Saved:**
- Manual testing: 40 hours → 15 seconds
- **ROI: 9,600x faster**
