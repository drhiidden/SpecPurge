# Roadmap

## v1.0 (RELEASED) ✅

- OpenAPI 3.0 parsing
- Smart payload generation
- 248-endpoint validation
- HTML reports
- CLI ready
- Exit codes for CI/CD

---

## v1.1 - AI Integration (4 weeks)

### LLM-Powered Payload Generation

```java
// GPT-4/Claude-powered payload generation
specsurge --spec api.com/openapi.json \
          --ai-provider openai \
          --ai-model gpt-4

// Generates realistic, context-aware payloads:
POST /api/artists {
  "name": "Kendrick Lamar",           // Real artist
  "genre": "Conscious Hip-Hop",       // Valid genre
  "birthDate": "1987-06-17",          // Accurate
  "albums": ["DAMN.", "TPAB"]         // Actual discography
}
```

**Features:**
- LLM-powered payload generation
- Business rule inference
- Realistic test data
- JWT authentication flow automation

**Why it matters:** Complex business rules + realistic test data = higher quality tests.

---

## v1.2 - Advanced Features (3 months)

### Response Schema Validation

```bash
specsurge --spec api.com/openapi.json --validate-schemas
```

Validates that response bodies match OpenAPI schemas:
- Field types
- Required fields
- Enum values
- Format constraints

### Contract Diff Detection

```bash
specsurge --spec api.com/openapi.json --baseline ./baseline-report.html
```

Detects breaking changes:
- New endpoints
- Removed endpoints
- Schema changes
- Response code changes

### Performance Assertions

```bash
specsurge --spec api.com/openapi.json --max-latency 200ms
```

Fails if:
- Average latency > 200ms
- Any endpoint > 1s

### Parallel Execution

```bash
specsurge --spec api.com/openapi.json --parallel 10
```

Run 10 requests concurrently (default: sequential).

### Custom Validators

```java
@Validator
public class CustomBusinessRuleValidator {
  public boolean validate(TestResult result) {
    // Custom logic
    return result.status == 200 && result.body.contains("success");
  }
}
```

---

## v1.3 - Enterprise Ready (6 months)

### SaaS Platform (Drag-and-Drop UI)

```
https://specsurge.io
  ├─ Dashboard
  │  ├─ API inventory
  │  ├─ Test history
  │  └─ Trend analysis
  ├─ Collaborative testing
  │  ├─ Team workspaces
  │  ├─ Role-based access
  │  └─ Shared reports
  ├─ Integrations
  │  ├─ Slack notifications
  │  ├─ Discord webhooks
  │  ├─ Jira issue creation
  │  └─ PagerDuty alerts
  └─ Multi-API comparison
     ├─ Cross-API trends
     └─ Unified dashboard
```

**Features:**
- Visual test designer (no code)
- Historical trend analysis
- Team collaboration
- Slack/Discord/Jira integrations
- Multi-API comparison
- Custom dashboards

**Pricing:**
- Free tier: 1 API, 100 endpoints
- Pro: $49/month - 10 APIs, unlimited endpoints
- Enterprise: Custom pricing

---

## Vision (Long-Term)

**AI-powered API testing for every development team worldwide.**

SpecSurge aims to be the **standard tool** for:
- API smoke testing in CI/CD
- Developer onboarding (instant API map)
- Contract validation (spec ↔ reality sync)
- AI-assisted realistic test data

**Target Market:**
- DevOps teams
- Backend developers
- QA engineers
- API designers

**Open Source Forever:** Core CLI will always be free and open source.
