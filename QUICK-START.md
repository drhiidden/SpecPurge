# SpecSurge - Quick Start

**Zero-config API testing from OpenAPI specs**

---

## 60-Second Setup

```bash
# 1. Navigate to framework
cd specsurge

# 2. Build JAR (requires Java 21+ and Maven 3.9+)
mvn package

# 3. Ensure your API is running
# Example: SampleShop backend at http://localhost:8082

# 4. Run SpecSurge
java -jar target/openapi-test-generator-1.0.0.jar

# 5. View report
open test-reports/test-report-*.html
```

**Expected Output:**

```
🤖 SPECSURGE v1.0.0
   Zero-Config API Testing
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

📡 Fetching OpenAPI spec from: http://localhost:8082/v3/api-docs
✓ Parsed API: SampleShop Backend v1.0
✓ Discovered 248 endpoints
✓ Generated 248 test requests
✓ Executing tests...

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
RESULTS
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

✓ Passed:       176 (71%)
✗ Failed:        72 (29%)
  ├─ 401 Auth:   54 (expected, protected endpoints)
  ├─ 404 Missing: 18 (expected, no data yet)
  └─ Real bugs:    0 (backend stable)

⏱ Duration:      15.2s
📊 Avg/request:   61ms

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

✅ REPORT: ./test-reports/test-report-20260329-050516.html
```

---

## CLI Options

```bash
# Default (uses localhost:8082)
java -jar specsurge.jar

# Custom OpenAPI spec URL
java -jar specsurge.jar \
  --spec http://staging-api.com/v3/api-docs

# Custom base URL (if spec location != API location)
java -jar specsurge.jar \
  --spec http://docs.api.com/openapi.json \
  --base-url https://api.com

# Custom output directory
java -jar specsurge.jar \
  --output /tmp/reports/$(date +%Y%m%d)

# Show help
java -jar specsurge.jar --help
```

---

## Understanding Results

### Success Cases (✅ Pass)

```
Expected: 200
Actual: 200     → ✅ PASS (perfect)
Actual: 201     → ✅ PASS (created, also success)
Actual: 404     → ✅ PASS (resource doesn't exist yet)
Actual: 401     → ✅ PASS (auth required, expected)
```

### Failure Cases (❌ Fail)

```
Expected: 200
Actual: 500     → ❌ FAIL (server error, REAL BUG)
Actual: 400     → ❌ FAIL (bad request, check payload)
```

**Key Insight:** SpecSurge distinguishes between expected failures (auth, missing data) and real bugs (crashes).

---

## HTML Report Walkthrough

### Section 1: Header

```
🤖 SPECSURGE API TEST REPORT

API: SampleShop Backend v1.0
Tested: 248 endpoints
Duration: 15.2s
Generated: 2026-03-29 05:05:16
```

### Section 2: Summary Metrics

```
┌─────────────────────────────────┐
│  Success Rate: 71%              │
│  ✓ Passed: 176                  │
│  ✗ Failed: 72                   │
│    ├─ Auth (401): 54            │
│    ├─ Missing (404): 18         │
│    └─ Errors (500): 0 ✅        │
└─────────────────────────────────┘
```

### Section 3: Endpoint Table

Sortable table with columns:
- **Endpoint:** Path + method
- **Status:** Color badge (green/yellow/red)
- **Expected:** What was expected
- **Actual:** What was returned
- **Duration:** Response time
- **Details:** Request/response JSON

**Features:**
- Click column headers to sort
- Color-coded status badges
- Expandable failure details

### Section 4: Failure Analysis

Only shows real issues (500s), not expected failures (401/404).

---

## Real-World Use Cases

### Use Case 1: Smoke Test (Daily)

```bash
# Run before standup
specsurge

# Check HTML report
# If 500s appear → investigate
# If only 401/404 → all good ✅
```

**Time:** 30 seconds  
**Value:** Instant confidence in API health

### Use Case 2: API Discovery (Onboarding)

```bash
# New developer joins team
specsurge --spec https://api.internal.com/v3/api-docs

# Open HTML report
# See ALL 200+ endpoints at a glance
# Faster than reading OpenAPI docs
```

**Time:** 1 minute  
**Value:** Complete API map, visual

### Use Case 3: Regression Detection (Pre-Deploy)

```bash
# Before deploy
specsurge --spec $STAGING_URL/v3/api-docs --output ./before

# After deploy
specsurge --spec $STAGING_URL/v3/api-docs --output ./after

# Compare reports
# Breaking changes? New 500s?
```

**Time:** 2 minutes  
**Value:** Catch regressions before production

### Use Case 4: Contract Validation

```bash
# Validate OpenAPI spec matches reality
specsurge --spec file://openapi.yaml \
          --base-url http://localhost:8080

# Detects:
# - Missing endpoints (404)
# - Wrong response codes
# - Schema mismatches
```

**Time:** 1 minute  
**Value:** Spec-reality alignment

---

## CI/CD Integration

### GitHub Actions

```yaml
name: SpecSurge Tests

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    
    steps:
      - uses: actions/checkout@v3
      
      - name: Set up Java 21
        uses: actions/setup-java@v3
        with:
          java-version: '21'
      
      - name: Build SpecSurge
        run: |
          cd specsurge
          mvn package
      
      - name: Run Tests
        run: |
          java -jar FSJ-Agentic/target/*.jar \
            --spec http://api:8082/v3/api-docs \
            --output ./reports
      
      - name: Upload Report
        uses: actions/upload-artifact@v3
        with:
          name: specsurge-report
          path: reports/*.html
```

### GitLab CI

```yaml
specsurge:
  image: maven:3.9-eclipse-temurin-21
  script:
    - cd specsurge && mvn package
    - java -jar target/*.jar --spec $API_URL/v3/api-docs
  artifacts:
    paths:
      - FSJ-Agentic/test-reports/*.html
    expire_in: 7 days
```

---

## Advanced Usage

### Custom Headers (Future)

```java
// v1.1 - API key authentication
specsurge --spec api.com/docs \
          --header "X-API-Key: your-key"
```

### Filtering Endpoints

```java
// v1.1 - Test only specific tags
specsurge --spec api.com/docs \
          --tags products,orders
```

### Performance Thresholds

```java
// v1.2 - Fail if slow
specsurge --spec api.com/docs \
          --max-duration 500ms
```

---

## Comparison with Alternatives

| Tool | Language | Config | Coverage | AI | Reports |
|------|----------|--------|----------|----|----|
| **SpecSurge** | Java | ✅ Zero | ✅ 100% | ✅ v1.1 | ✅ Beautiful |
| Dredd | Node | ⚠️ Hooks | ✅ 100% | ❌ | ⚠️ Basic |
| Schemathesis | Python | ⚠️ Some | ✅ 100% | ❌ | ⚠️ Basic |
| Postman | SaaS | ❌ Heavy | ⚠️ Manual | ❌ | ✅ Yes |

**SpecSurge Advantage:** Java-native + AI-ready + zero config + beautiful reports

---

## Roadmap

### v1.0 (TODAY) ✅
- OpenAPI 3.0 parsing
- Smart payload generation
- HTML reports
- CLI ready

### v1.1 (1 month)
- AI payload generation (GPT-4/Claude)
- JWT authentication flows
- Response schema validation

### v1.2 (3 months)
- Performance assertions
- Contract diff detection
- Parallel execution

---

## Get Started

```bash
# Clone
cd specsurge

# Build
mvn package

# Run
java -jar target/*.jar

# Enjoy
open test-reports/*.html
```

---

**Framework:** SpecSurge v1.0.0  
**Status:** Production Ready ✅  
**Documentation:** See `README.md` for complete guide  

**Test 100% of your API in 30 seconds.**
