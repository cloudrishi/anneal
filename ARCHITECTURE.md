# anneal — Architecture & Design Record

> Living document. Updated at every design decision.  
> Last updated: April 19, 2026

---

## vision

An AI-powered Java migration assistant that analyzes a Java codebase, detects version-specific risks and breaking changes, and produces an incremental migration plan with code-level fix suggestions — grounded in the actual source code, not generic advice.

The tool is a co-pilot, not an autopilot. It surfaces, explains, and suggests. The developer decides.

---

## project name

**anneal**

From metallurgy — controlled heating to remove brittleness and improve structure. Exactly what this tool does to a Java codebase.

Repository: `github.com/cloudrishi/anneal`

---

## target user

A Java engineer or architect responsible for modernizing a Java 8, 9, 11, or 17 codebase to Java 25 LTS. They understand Java but need structured guidance on what breaks, what can be automated, and what requires manual judgment.

---

## guiding principles

- **Incremental over big bang** — nobody migrates 8 → 25 in one commit. The tool plans LTS-to-LTS stops.
- **Trust through transparency** — every finding shows why it was flagged, what rule triggered it, and what the fix does.
- **Co-pilot, not autopilot** — `autoApplicable: false` findings are never applied without developer approval.
- **Pure Java** — no Python sidecar. The entire stack runs on the JVM.
- **Local-first** — no cloud APIs by default. Runs on local hardware via Ollama. Cloud is opt-in only.
- **Detection is deterministic** — AST traversal and rule matching produce no hallucinations. LLM only enriches fix suggestions.

---

## migration path

```
Java 8 → Java 11 (LTS) → Java 17 (LTS) → Java 21 (LTS) → Java 25 (LTS) ← target
```

> Java 9 is not an LTS release. The tool skips it as an intermediate stop — 8→11 is the first recommended boundary crossing. Java 9 risks (JPMS) are detected and flagged regardless.

### 8 → 9 — highest risk boundary

**Root cause:** JPMS (Project Jigsaw) encapsulates JDK internals that were previously accessible on the classpath.

**Breaking changes:**
- `sun.misc.*`, `com.sun.*`, internal JDK APIs — illegal access by default
- Libraries relying on `sun.misc.Unsafe` hard-crash
- Reflection access to private fields requires explicit `--add-opens`
- Unnamed module assumptions — classpath-style loading no longer guaranteed

**Rules:** `JPMS_SUN_IMPORT`, `JPMS_UNSAFE_USAGE`, `JPMS_ILLEGAL_REFLECTIVE_ACCESS`, `JPMS_COM_SUN_IMPORT`

### 9 → 11 — API removals

**Breaking changes:**
- Java EE modules removed: `javax.xml.ws`, `javax.activation`, JAXB, JAX-WS, `javax.annotation`
- Must add external Jakarta dependencies
- `Thread.destroy()` and `Thread.stop(Throwable)` removed

**Rules:** `API_JAXB_REMOVED`, `API_JAVAX_ACTIVATION_REMOVED`, `API_JAX_WS_REMOVED`, `API_JAVAX_ANNOTATION_REMOVED`

### 11 → 17 — encapsulation enforced

**Breaking changes:**
- `--illegal-access` JVM flag removed — workarounds from 9 migration no longer valid
- `SecurityManager` deprecated for removal
- Applet API deprecated

**Rules:** `DEPRECATION_SECURITY_MANAGER`, `BUILD_ILLEGAL_ACCESS_FLAG`

### 17 → 21 — deprecations finalized

**Breaking changes:**
- `Object.finalize()` deprecated for removal
- `Thread.stop()`, `Thread.suspend()`, `Thread.resume()` removed

**Rules:** `DEPRECATION_FINALIZE`, `API_THREAD_STOP_REMOVED`

### 21 → 25 — new LTS target (supported until 2033)

**Key improvements (not breaking — modernization opportunities):**
- Scoped values — safer alternative to ThreadLocal (stable)
- Structured concurrency — stable
- Applet API fully removed

**Rules:** `CONCURRENCY_THREADLOCAL_SCOPED_VALUE`, `CONCURRENCY_SYNCHRONIZED_STRUCTURED`, `DEPRECATION_APPLET_API`

**Licensing note:** Java 21 free Oracle updates expire September 2026. Java 25 is free under NFTC until September 2027.

---

## modernization opportunities (any version → 25)

| Old pattern | Java 25 equivalent | Rule | Effort |
|---|---|---|---|
| Anonymous class | Lambda / method reference | `LANGUAGE_ANONYMOUS_CLASS_LAMBDA` | TRIVIAL |
| `Date` / `Calendar` | `java.time` | `LANGUAGE_OLD_DATETIME_API` | LOW |
| `instanceof` + cast | Pattern matching | `LANGUAGE_INSTANCEOF_CAST` | TRIVIAL |
| Mutable data class | Record | `LANGUAGE_RECORD_OPPORTUNITY` | LOW |
| `Thread` / thread pool | Virtual thread (Loom) | `CONCURRENCY_THREAD_VIRTUAL` | MEDIUM |
| `ThreadLocal` | `ScopedValue` | `CONCURRENCY_THREADLOCAL_SCOPED_VALUE` | MEDIUM |
| `synchronized` block | Structured concurrency | `CONCURRENCY_SYNCHRONIZED_STRUCTURED` | HIGH |
| `javax.*` dependency coordinates | `jakarta.*` | `BUILD_JAVAX_TO_JAKARTA_COORDS` | LOW |

---

## rule engine

### design — code-driven, not data-driven

Rules are Java classes, not database rows. Each rule is a stateless object that declares what to detect and what to suggest. The rule engine applies them to AST nodes. Rules are version-controlled, testable, and reviewable in PRs.

### rule sets

| Class | Category | Rules | Boundary |
|---|---|---|---|
| `JpmsRules` | JPMS | 4 | 8 → 9 |
| `ApiRemovalRules` | API_REMOVAL | 5 | 9 → 11, 17 → 21 |
| `DeprecationRules` | DEPRECATION | 3 | 11 → 17, 21 → 25 |
| `LanguageRules` | LANGUAGE | 4 | 8 → 25 (modernization) |
| `ConcurrencyRules` | CONCURRENCY | 3 | 21 → 25 (modernization) |
| `BuildRules` | BUILD | 3 | All boundaries |

### data model

**MigrationRule**

| Field | Type | Description |
|---|---|---|
| `ruleId` | String | Unique key e.g. `JPMS_SUN_IMPORT` |
| `category` | RuleCategory | JPMS · API_REMOVAL · DEPRECATION · LANGUAGE · CONCURRENCY · BUILD |
| `severity` | Severity | BREAKING · DEPRECATED · MODERNIZATION |
| `effort` | Effort | TRIVIAL · LOW · MEDIUM · HIGH · MANUAL |
| `introducedIn` | JavaVersion | Version where concern is introduced |
| `removedIn` | JavaVersion | Version where concern becomes a hard break (nullable) |
| `patterns` | List\<DetectionPattern\> | One or more detection patterns |
| `fixTemplate` | FixSuggestion | Template fix — LLM enriches explanation |
| `referenceUrl` | String | Official JEP or migration guide link |

**DetectionPattern**

| Field | Type | Description |
|---|---|---|
| `type` | PatternType | AST_NODE · IMPORT · API_CALL · ANNOTATION · REFLECTION · BUILD |
| `matcher` | String | Pattern string — e.g. `sun.misc.*` |
| `nodeType` | String | JavaParser AST node class (AST_NODE type only) |
| `confidence` | Float | 0.0 to 1.0 |

**FixSuggestion**

| Field | Type | Description |
|---|---|---|
| `fixType` | FixType | IMPORT_REPLACE · API_REPLACE · REFACTOR · ADD_DEPENDENCY · MODULE_INFO · MANUAL |
| `originalCode` | String | The detected code snippet |
| `suggestedCode` | String | Replacement code or guidance |
| `explanation` | String | LLM-generated rationale (empty until LLM enriches) |
| `autoApplicable` | Boolean | Safe to apply without developer review |

**Finding**

| Field | Type | Description |
|---|---|---|
| `findingId` | String | Unique within scan |
| `ruleId` | String | Rule that produced this finding |
| `severity` | Severity | Inherited from rule |
| `filePath` | String | Absolute path to affected file |
| `lineNumber` | int | Line where finding was detected |
| `originalCode` | String | Snippet that triggered the finding |
| `confidence` | float | Pattern confidence at time of match |
| `status` | FindingStatus | OPEN · ACCEPTED · REJECTED · DEFERRED |

**ScanResult**

| Field | Type | Description |
|---|---|---|
| `scanId` | UUID | Primary key |
| `repoPath` | String | Absolute path to scanned repository |
| `detectedVersion` | JavaVersion | Detected from pom.xml / build.gradle |
| `targetVersion` | JavaVersion | Always V25 |
| `findings` | List\<Finding\> | All findings produced by rule engine |
| `riskScore` | Integer | 0–100 aggregate risk score |
| `phase` | MigrationPhase | ANALYSIS · PLANNING · FIXING · VALIDATION |

### enums

```
Severity:       BREAKING · DEPRECATED · MODERNIZATION
Effort:         TRIVIAL · LOW · MEDIUM · HIGH · MANUAL
RuleCategory:   JPMS · API_REMOVAL · DEPRECATION · LANGUAGE · CONCURRENCY · BUILD
JavaVersion:    V8 · V9 · V11 · V17 · V21 · V25
MigrationPhase: ANALYSIS · PLANNING · FIXING · VALIDATION
FixType:        IMPORT_REPLACE · API_REPLACE · REFACTOR · ADD_DEPENDENCY · MODULE_INFO · MANUAL
PatternType:    AST_NODE · IMPORT · API_CALL · ANNOTATION · REFLECTION · BUILD
FindingStatus:  OPEN · ACCEPTED · REJECTED · DEFERRED
```

---

## migration phases

### Phase 1 — Analysis
- Detect source Java version from `pom.xml` / `build.gradle`
- Scan AST for migration risks using rule engine
- Produce migration report: findings, risk score, effort estimate per boundary
- Read-only — no code changes

### Phase 2 — Planning
- Plot incremental path: 8→11→17→21→25 (LTS to LTS)
- Scope each step: what breaks, what can be fixed, what must be manual
- Produce per-boundary scoped report
- Developer reviews and confirms the plan

### Phase 3 — Fixing
- Per finding: show original code, suggested fix, LLM explanation
- Developer accepts or rejects each suggestion
- Tool tracks accepted/rejected/deferred state
- `autoApplicable: true` fixes may be batched for developer approval

### Phase 4 — Validation
- Re-scan after developer applies fixes
- Confirm resolved findings are cleared
- Track progress: 847 findings → 203 remaining
- Generate SARIF report for CI integration

---

## tech stack

| Layer | Technology | Version | Justification |
|---|---|---|---|
| Backend | Quarkus LTS | 3.33.1 | Latest LTS (March 2026), Kubernetes-native, CDI, health probes |
| AST Analysis | JavaParser | 3.28.0 | Java 1–25 support, SymbolSolver for type resolution |
| LLM Orchestration | LangChain4j | 1.13.0 | Java-native, Ollama + Anthropic support |
| Embeddings | langchain4j-embeddings-all-minilm-l6-v2 | 0.36.2 | Pure Java ONNX, 384-dim vectors — pinned (not in 1.13.0) |
| Vector Store | quarkus-langchain4j-pgvector | 0.26.2 | Quarkiverse extension — correct coordinate |
| LLM — code | codellama:13b | via Ollama | Best local code reasoning on M1 Pro 32GB |
| LLM — prose | llama3.1:8b | via Ollama | Better natural language for report narrative |
| LLM — deep (opt-in) | claude-sonnet-4-6 | Anthropic API | Complex refactors requiring deep reasoning |
| Frontend | Next.js | 16 | TypeScript, React, Tailwind |
| Deployment | Kubernetes + Helm | — | Cloud-native from day one |
| CI | GitHub Actions | — | SARIF output, Gradle test runner |
| Build JDK | Temurin | 25.0.2-tem | Matches migration target; installed via SDKMAN |
| Lombok | Lombok | 1.18.42 | Java 25 support added in 1.18.40; 1.18.36 does not compile on Java 25 |

### Dependency coordinate corrections (learned during implementation)

| Wrong | Correct | Reason |
|---|---|---|
| `dev.langchain4j:langchain4j-pgvector:1.13.0` | `io.quarkiverse.langchain4j:quarkus-langchain4j-pgvector:0.26.2` | Artifact doesn't exist at 1.13.0 — use Quarkiverse extension |
| `dev.langchain4j:langchain4j-embeddings-all-minilm-l6-v2:1.13.0` | same groupId, version `0.36.2` | Last release was Nov 2024; not updated to 1.13.0 |
| `io.quarkus:quarkus-junit5` | `io.quarkus:quarkus-junit` | Renamed in Quarkus 3.31 |
| `org.projectlombok:lombok:1.18.36` | `org.projectlombok:lombok:1.18.42` | 1.18.40+ required for Java 25 support |

### Build Java version

The tool itself is built and runs on **Java 25**. A Java modernization tool must run on the version it targets.

```kotlin
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}
```

SDKMAN install:
```bash
sdk install java 25.0.2-tem
sdk use java 25.0.2-tem
```

### Hardware target (development)
- Apple M1 Pro, 32GB unified memory
- `codellama:13b` — ~8GB RAM
- `llama3.1:8b` — ~5GB RAM
- `deepseek-coder:6.7b` — fallback

---

## project structure

```
anneal/
├── .github/workflows/ci.yml
├── gradle/
│   ├── libs.versions.toml              Version catalog — all dependency versions
│   └── wrapper/
│       └── gradle-wrapper.properties   Pins Gradle 9.4.1
├── anneal-api/                         REST layer — Quarkus resources, DTOs
│   ├── build.gradle.kts
│   └── src/main/java/com/rish/anneal/api/
│       ├── resource/
│       │   └── ScanResource.java       GET /api/health, POST /api/scan
│       ├── dto/
│       │   ├── ScanRequest.java        Request record
│       │   ├── ScanResponse.java       Response record with boundary scores
│       │   └── FindingDto.java         Individual finding DTO
│       ├── mapper/
│       │   └── ScanMapper.java         Domain → DTO, stateless
│       └── registry/
│           └── RuleRegistry.java       CDI bean — aggregates all rule sets
├── anneal-core/                        Domain — pure Java, zero framework deps
│   ├── build.gradle.kts
│   └── src/main/java/com/rish/anneal/core/
│       ├── model/                      Enums + domain classes
│       │   ├── JavaVersion.java
│       │   ├── Severity.java
│       │   ├── Effort.java
│       │   ├── RuleCategory.java
│       │   ├── MigrationPhase.java
│       │   ├── PatternType.java
│       │   ├── FixType.java
│       │   ├── FixSuggestion.java
│       │   ├── DetectionPattern.java
│       │   ├── MigrationRule.java
│       │   ├── Finding.java
│       │   └── ScanResult.java
│       ├── rule/                       Rule sets — one class per category
│       │   ├── JpmsRules.java
│       │   ├── ApiRemovalRules.java
│       │   ├── DeprecationRules.java
│       │   ├── LanguageRules.java
│       │   ├── ConcurrencyRules.java
│       │   └── BuildRules.java
│       ├── engine/                     Rule engine + risk scoring
│       │   ├── RuleEngine.java         Applies rules to CompilationUnit — stateless
│       │   └── RiskScoreCalculator.java  Weighted score, per-boundary breakdown
│       ├── scanner/                    JavaParser integration, file walker
│       │   ├── CodebaseScanner.java    Walks repo, coordinates scan
│       │   ├── BuildFileScanner.java   pom.xml / build.gradle text scanning
│       │   └── VersionDetector.java    Detects Java version from build files
│       └── phase/                      Phase orchestration — coming next
├── anneal-llm/                         LangChain4j — fix generation, embeddings
│   └── build.gradle.kts
├── anneal-store/                       Persistence — Panache, Flyway, pgvector
│   └── build.gradle.kts
├── anneal-ui/                          Next.js frontend
├── docs/
│   └── architecture.png
├── helm/anneal/
├── build.gradle.kts                    Root — common config, Quarkus BOM
├── settings.gradle.kts                 Module declarations
├── gradlew / gradlew.bat               Gradle wrapper scripts
├── docker-compose.yml
├── init.sql
├── .env.example
├── ARCHITECTURE.md
└── README.md
```

---

## LLM strategy

### model roles

| Role | Task | Model |
|---|---|---|
| Detection | AST traversal, rule matching | None — fully deterministic |
| Fix generation | Suggest refactored code | `codellama:13b` (default) |
| Explanation | Natural language rationale | `llama3.1:8b` (default) |
| Deep reasoning | Complex refactors | Cloud model — explicit opt-in |

### supported providers

| Provider | Models | Use case |
|---|---|---|
| Ollama (default) | `codellama:13b`, `llama3.1:8b` | Local, private, free |
| Anthropic | `claude-sonnet-4-6` | Deep reasoning, complex refactors |
| OpenAI | `gpt-4o` | Alternative cloud option |
| Groq | `llama3.1:70b` | Fast cloud inference |

`allow-cloud-fallback: false` is the default — code never leaves the machine without explicit user consent.

---

## decisions log

| Date | Decision | Rationale |
|---|---|---|
| 2026-04-19 | Pure Java stack — no Python | Architectural statement; single deployable unit on the JVM |
| 2026-04-19 | Quarkus over Spring Boot | Kubernetes-native, lighter footprint, differentiates from codebase-chat |
| 2026-04-19 | pgvector over Qdrant/Weaviate | Right scale; operational simplicity wins |
| 2026-04-19 | codellama:13b as primary LLM | Best local code reasoning on M1 Pro 32GB |
| 2026-04-19 | llama3.1:8b for prose | Better natural language than codellama |
| 2026-04-19 | Co-pilot model — no auto-apply | Trust requires transparency; developer always decides |
| 2026-04-19 | LTS-to-LTS incremental path | Nobody migrates 8→25 in one commit |
| 2026-04-19 | Target Java 25, not Java 21 | Java 21 free updates expire September 2026; Java 25 supported until 2033 |
| 2026-04-19 | module-info.java — MANUAL only | Incorrect generation breaks the build |
| 2026-04-19 | Multi-module Gradle — per-module scan | Each module has its own dependency tree |
| 2026-04-19 | CI format — SARIF | Industry standard; works across GitHub, GitLab, Azure DevOps |
| 2026-04-19 | Rule engine — code-driven | Rules are logic not data; testable, version-controlled |
| 2026-04-19 | Detection is LLM-free | Deterministic; faster and more reliable |
| 2026-04-19 | `allow-cloud-fallback: false` default | Code never leaves machine without consent |
| 2026-04-19 | RuleRegistry in anneal-api, not anneal-core | anneal-core stays pure Java; CDI lives in the Quarkus layer |
| 2026-04-19 | Lombok 1.18.42 | 1.18.40+ required for Java 25; 1.18.36 fails with ExceptionInInitializerError |
| 2026-04-19 | Pinned embedding artifact to 0.36.2 | langchain4j-embeddings-all-minilm-l6-v2 not released at 1.13.0 |
| 2026-04-19 | quarkus-langchain4j-pgvector from Quarkiverse | dev.langchain4j:langchain4j-pgvector does not exist at 1.13.0 |

---

## open questions

- Does `anneal-core` need `quarkus-arc` for CDI annotations, or stay zero-dependency pure Java?
- How do we handle `build.gradle` projects — JavaParser covers `.java` files, but Gradle build scanning needs a separate parser
- Integration test strategy — real pgvector instance or mocked embedding store?

---

## risk score

### formula

Each finding contributes a weighted score based on severity, confidence, and effort:

```
findingScore = severityWeight × confidence × effortMultiplier
```

**Severity weights:**
```
BREAKING       = 10
DEPRECATED     =  5
MODERNIZATION  =  1
```

**Effort multipliers:**
```
MANUAL   = 1.5   — hardest to fix, highest risk
HIGH     = 1.3
MEDIUM   = 1.1
LOW      = 1.0
TRIVIAL  = 0.8   — easy fix, lower urgency
```

**Aggregate:**
```
rawScore  = sum of all findingScores
riskScore = min(100, rawScore)    — capped at 100
```

### risk bands

| Score | Band | Meaning |
|---|---|---|
| 0–20 | LOW | Mostly modernization opportunities, no blockers |
| 21–50 | MEDIUM | Some deprecated APIs, addressable incrementally |
| 51–80 | HIGH | Multiple breaking changes — plan carefully |
| 81–100 | CRITICAL | JPMS violations or mass API removals — must fix before migrating |

### examples

| Finding | Severity | Confidence | Effort | Score |
|---|---|---|---|---|
| `JPMS_SUN_IMPORT` | BREAKING | 1.0 | HIGH | 13.0 |
| `JPMS_ILLEGAL_REFLECTIVE_ACCESS` | BREAKING | 0.7 | HIGH | 9.1 |
| `API_JAXB_REMOVED` | BREAKING | 1.0 | LOW | 10.0 |
| `DEPRECATION_FINALIZE` | DEPRECATED | 0.9 | MEDIUM | 4.95 |
| `LANGUAGE_RECORD_OPPORTUNITY` | MODERNIZATION | 0.5 | LOW | 0.5 |

A project with 5 JPMS violations hits CRITICAL immediately — the ceiling is intentional. At that point the exact number doesn't matter, the message is clear.

### implementation

`RiskScoreCalculator` in `anneal-core/engine/` — stateless, deterministic, no LLM involvement.

---

## scanner layer

### classes

| Class | Package | Responsibility |
|---|---|---|
| `RuleEngine` | `anneal-core/engine` | Applies rules to a parsed `CompilationUnit` — stateless, deterministic |
| `CodebaseScanner` | `anneal-core/scanner` | Walks repo, parses `.java` files, coordinates rule engine and build scanner |
| `BuildFileScanner` | `anneal-core/scanner` | Text-based scanning of `pom.xml`, `build.gradle`, `build.gradle.kts` |

### pattern dispatch

`RuleEngine` dispatches on `PatternType`:

| PatternType | Handler | Notes |
|---|---|---|
| IMPORT | `matchImport()` | Wildcard and exact match support |
| API_CALL | `matchApiCall()` | Extracts method name from `java.lang.Thread#stop()` format |
| AST_NODE | `matchAstNode()` | MethodDeclaration and ObjectCreationExpr supported |
| REFLECTION | `matchReflection()` | Matches method call by name |
| BUILD | `BuildFileScanner` | Separate scanner — not handled by RuleEngine |
| ANNOTATION | — | Future scanner — returns empty list currently |

### design decisions

- `target/` and `build/` directories excluded from scan — compiled classes not scanned
- JavaParser configured with `ReflectionTypeSolver` + `JavaParserTypeSolver` for type resolution
- Language level set to `JAVA_21` — highest stable level JavaParser supports
- `BuildFileScanner` uses text-based line scanning — simpler and faster than XML parsing for our patterns
- Parse errors are logged as warnings and skipped — scan continues on malformed files
- `CodebaseScanner` is stateless — safe to reuse across multiple scans

---

## build system

### Gradle 9.4.1 — Kotlin DSL

Migrated from Maven to Gradle mid-project. Decision: a Java modernization tool that recommends Gradle to its users should be built with Gradle. Dogfooding.

### structure

| File | Purpose |
|---|---|
| `gradle/libs.versions.toml` | Version catalog — single source of truth for all dependency versions |
| `settings.gradle.kts` | Root settings — declares all submodules |
| `build.gradle.kts` | Root build — common config, Quarkus BOM, shared dependencies |
| `anneal-core/build.gradle.kts` | Core deps only — JavaParser |
| `anneal-store/build.gradle.kts` | Persistence deps — Panache, Flyway, pgvector |
| `anneal-llm/build.gradle.kts` | LLM deps — LangChain4j, Ollama, Anthropic, embeddings |
| `anneal-api/build.gradle.kts` | REST layer — Quarkus plugin, health, OpenAPI |

### version catalog — `gradle/libs.versions.toml`

Replaces Maven's `dependencyManagement`. All versions in one file, referenced by alias across all modules. The coordinate correction problems we hit with Maven (wrong artifact IDs, versions that don't exist) are prevented here — one place to fix, all modules pick it up.

```toml
[versions]
quarkus = "3.33.1"
langchain4j = "1.13.0"
langchain4j-embeddings = "0.36.2"   # pinned — not released at 1.13.0
langchain4j-pgvector = "0.26.2"     # Quarkiverse — different version stream
javaparser = "3.28.0"
lombok = "1.18.42"                  # 1.18.40+ required for Java 25
```

### Java toolchain

```kotlin
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}
```

Gradle toolchain API ensures the correct JDK is used regardless of what's on `PATH`. More reliable than Maven's `maven.compiler.release`.

### Gradle wrapper

Always use `./gradlew` — never the global `gradle` command. Ensures every developer and CI uses exactly Gradle 9.4.1.

```bash
./gradlew compileJava   # compile all modules
./gradlew test          # run all tests
./gradlew :anneal-api:quarkusDev  # Quarkus dev mode
```

### decisions

| Date | Decision | Rationale |
|---|---|---|
| 2026-04-19 | Migrate Maven → Gradle | Dogfooding — anneal recommends Gradle; must use it |
| 2026-04-19 | Kotlin DSL over Groovy DSL | Type-safe, IDE-friendly, modern standard for new projects |
| 2026-04-19 | Version catalog | Single source of truth; prevents coordinate errors |
| 2026-04-19 | Gradle toolchain API | More reliable JDK selection than relying on PATH |
| 2026-04-19 | Gradle 9.4.1 | Latest stable; Java 25 + Java 26 support |
| 2026-04-19 | Auto-discovery of libs.versions.toml | Gradle 9 picks it up automatically — no manual `from()` call needed |
| 2026-04-20 | DTOs as Java 25 records | No Lombok, no boilerplate — records are the right tool for immutable data transfer |
| 2026-04-20 | ScanMapper as static methods | No state, no CDI — pure transformation; easier to test |
| 2026-04-20 | quarkus-hibernate-validator for jakarta.validation | Required for @NotBlank on request DTOs |

---

## REST layer

### endpoints

| Method | Path | Description |
|---|---|---|
| GET | `/api/health` | Liveness check — returns `{"status":"ok","service":"anneal"}` |
| POST | `/api/scan` | Scan a Java repository — returns full `ScanResponse` |

### classes

| Class | Package | Responsibility |
|---|---|---|
| `ScanResource` | `anneal-api/resource` | Quarkus REST resource — two endpoints |
| `ScanRequest` | `anneal-api/dto` | Request record — `repoPath` + optional `sourceVersion` |
| `ScanResponse` | `anneal-api/dto` | Response record — findings, risk score, boundary scores |
| `FindingDto` | `anneal-api/dto` | Individual finding DTO |
| `ScanMapper` | `anneal-api/mapper` | Domain → DTO transformation, stateless |

### scan flow

```
POST /api/scan
  → validate path exists and is directory
  → resolve source version (caller-specified or VersionDetector auto-detect)
  → RuleRegistry.rulesFor(source, V25)
  → CodebaseScanner.scan()
  → ScanMapper.toResponse()
  → 200 OK with ScanResponse
```

### design decisions

- All DTOs are Java 25 records — no Lombok, no boilerplate
- `@NotBlank` on `ScanRequest.repoPath` — Quarkus returns 400 automatically
- Findings sorted by severity then confidence descending — most critical first
- `VersionDetector` CDI-injected; `CodebaseScanner`, `RuleEngine`, `BuildFileScanner` constructed inline — stateless, no CDI needed
- `referenceUrl` is null in `FindingDto` for now — `MigrationRule` owns it, not `Finding`; denormalization deferred
- `quarkus-hibernate-validator` required for `jakarta.validation` annotations
