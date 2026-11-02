# Changelog

All notable changes to **SpecSurge** will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]

### Added
- **Cases mode** (`--cases FILE.yml`): declarative requests with JsonPath assertions, `${var}`/`${env:X}` interpolation; unresolved cases are skipped and listed. See `docs/cases.md`.
- **Schema validation** (`--validate-schema`): 2xx responses checked against the OpenAPI response schema.
- **Differential mode** (`--compare-base-url`, `--fail-on-diff`): same requests against a baseline, differences listed per JSON path.
- **Auth**: `--bearer-env VAR` (env only, masked in reports), JWT `exp` check (expired token aborts), `--fail-on-auth`.
- **Safety**: `--read-only`, `--allow-writes`, `--allow-host`, `--max-requests`, `--delay-ms`; credentials without `--read-only` are rejected.
- **Selection**: `--include-protected`, `--include REGEX`, `--param K=V`; `--spec` accepts local files.
- **Reports**: failure details with a reproducible `curl`, HTML escaping, `--format html,junit,json`.
- **Strictness**: `--strict` (404 no longer passes in generated mode).
- Unit and integration tests (JDK HttpServer) and `maven-surefire-plugin`.

### Fixed
- Global `security` of the OpenAPI document is now inherited by operations (previously every operation without its own `security` was treated as public); `security: []` makes an operation public.
- Null query parameters are no longer sent; unknown CLI options fail fast; docs reference the real JAR name (`specsurge-core`).

### Changed
- Remote hosts require `--allow-host` when a token or write methods are used (localhost is always allowed).

---

## [1.0.0] - 2026-03-29

### 🎉 Market Launch - SpecSurge

**Brand:** SpecSurge - Zero-config API testing from OpenAPI specs  
**Positioning:** Automatic testing for the AI era

### Added

#### Core Features
- **OpenAPI 3.0 Parser**: Automatic endpoint discovery from `/v3/api-docs`
- **Smart Payload Generator**: Type-aware generation (string, integer, enum, date-time, email, uuid)
- **Test Executor**: Parallel execution with Rest-Assured
- **HTML Report Generator**: Beautiful gradient-styled reports with coverage metrics

#### Intelligence
- Pattern matching for field names (title, email, url, category)
- Format-aware generation (ISO 8601 dates, valid emails, UUIDs)
- Enum handling (first value selection)
- Flexible status matching (200-299 success, 404/401 acceptable)

#### Developer Experience
- Zero configuration required
- CLI with sensible defaults
- Executable JAR with Maven Shade
- Detailed logging (SLF4J + Logback)

### Tested Against
- **SampleShop API**: 248 endpoints discovered
- **Success Rate**: 71% (176/248 passed)
- **Execution Time**: ~15 seconds
- **Report Size**: 89KB HTML

### Market Readiness
- Production-ready for smoke tests
- CI/CD integration ready
- Open source MIT license
- AI integration architecture ready (v1.1)

---

## [Unreleased] - Roadmap

### v1.1 - AI Integration (Planned)
- [ ] OpenAI/Claude payload generation for complex schemas
- [ ] Business rule inference from descriptions
- [ ] Realistic test data (product names, real emails)
- [ ] Smart validation error interpretation

### v1.2 - Advanced Features (Planned)
- [ ] Authentication flow (login → JWT → protected endpoints)
- [ ] Contract diff detection (breaking change alerts)
- [ ] Performance assertions (response time thresholds)
- [ ] Parallel execution with configurable concurrency
- [ ] Response schema validation

### v1.3 - Enterprise Ready (Planned)
- [ ] SaaS UI (drag-and-drop OpenAPI → instant report)
- [ ] Slack/Discord/Webhook notifications
- [ ] Multi-API comparison (staging vs production)
- [ ] Historical trend analysis
- [ ] Team collaboration features

---

## Design Decisions

### Why Swagger Parser v3?
OpenAPI 3.0 is the industry standard. Parser v3 provides:
- Full schema resolution
- Reference handling ($ref)
- Example extraction
- Wide community support

### Why Rest-Assured?
- Fluent Java DSL
- JSON path extraction
- Flexible response matching
- Wide adoption in Java ecosystem

### Why Inline HTML Generation?
- Zero template dependencies for v1.0
- Full control over styling
- Gradient modern design
- Easy to customize

### Why NOT FreeMarker Templates?
Initially planned, but inline generation:
- Reduces dependencies
- Simplifies debugging
- Faster iteration
- Still maintainable for current report complexity

**Future**: When reports become complex (charts, graphs), migrate to FreeMarker/Thymeleaf.

---

## Known Limitations v1.0

1. **Auth**: Only public endpoints tested (no JWT flow)
2. **Complex Schemas**: Uses simple defaults (AI needed for complex business logic)
3. **Sequential Execution**: No parallel requests (fixed in v1.2)
4. **Basic Assertions**: Only status code validation (no response schema check)

---

## Competitor Analysis

### vs Dredd (Node.js)
- ✅ Better: Java ecosystem, AI-ready architecture
- ❌ Missing: Response schema validation

### vs Schemathesis (Python)
- ✅ Better: Maven integration, type-safe Java
- ❌ Missing: Hypothesis-driven property testing

### vs Postman Collection Runner
- ✅ Better: Zero config, automatic discovery
- ❌ Missing: UI for non-technical users

---

## License

MIT License - See LICENSE file

---

**Project**: FSJ Agentic OpenAPI Test Generator  
**Market**: API Testing Automation (2026)  
**Status**: Production Ready (v1.0) | AI Enhancement Planned (v1.1)
