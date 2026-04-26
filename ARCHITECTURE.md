# anneal — Architecture & Design Record

> Living document. Updated at every design decision.  
> Last updated: April 24, 2026

---

## vision

An AI-powered Java migration assistant that analyzes a Java codebase, detects version-specific risks and breaking
changes, and produces an incremental migration plan with code-level fix suggestions — grounded in the actual source
code, not generic advice.

The tool is a co-pilot, not an autopilot. It surfaces, explains, and suggests. The developer decides.

---

## project name

**anneal**

From metallurgy — controlled heating to remove brittleness and improve structure. Exactly what this tool does to a Java
codebase.

Repository: `github.com/cloudrishi/anneal`

---

## target user

A Java engineer or architect responsible for modernizing a Java 8, 9, 11, or 17 codebase to Java 25 LTS. They understand
Java but need structured guidance on what breaks, what can be automated, and what requires manual judgment.

---

## guiding principles

- **Incremental over big bang** — nobody migrates 8 → 25 in one commit. The tool plans LTS-to-LTS stops.
- **Trust through transparency** — every finding shows why it was flagged, what rule triggered it, and what the fix
  does.
- **Co-pilot, not autopilot** — `autoApplicable: false` findings are never applied without developer approval.
- **Pure Java** — no Python sidecar. The entire stack runs on the JVM.
- **Local-first** — no cloud APIs by default. Runs on local hardware via Ollama. Cloud is opt-in only.
- **Detection is deterministic** — AST traversal and rule matching produce no hallucinations. LLM only enriches fix
  suggestions.

---

## migration path

```
Java 8 -> Java 11 (LTS) -> Java 17 (LTS) -> Java 21 (LTS) -> Java 25 (LTS) <- target
```

> Java 9 is not an LTS release. The tool skips it as an intermediate stop — 8->11 is the first recommended boundary
> crossing. Java 9 risks (JPMS) are detected and flagged regardless.

### 8 -> 9 — highest risk boundary

**Root cause:** JPMS (Project Jigsaw) encapsulates JDK internals that were previously accessible on the classpath.

**Breaking changes:**

- `sun.misc.*`, `com.sun.*`, internal JDK APIs — illegal access by default
- Libraries relying on `sun.misc.Unsafe` hard-crash
- Reflection access to private fields requires explicit `--add-opens`
- Unnamed module assumptions — classpath-style loading no longer guaranteed

**Rules:** `JPMS_SUN_IMPORT`, `JPMS_UNSAFE_USAGE`, `JPMS_ILLEGAL_REFLECTIVE_ACCESS`, `JPMS_COM_SUN_IMPORT`

### 9 -> 11 — API removals

**Breaking changes:**

- Java EE modules removed: `javax.xml.ws`, `javax.activation`, JAXB, JAX-WS, `javax.annotation`
- Must add external Jakarta dependencies
- `Thread.destroy()` and `Thread.stop(Throwable)` removed

**Rules:** `API_JAXB_REMOVED`, `API_JAVAX_ACTIVATION_REMOVED`, `API_JAX_WS_REMOVED`, `API_JAVAX_ANNOTATION_REMOVED`

### 11 -> 17 — encapsulation enforced

**Breaking changes:**

- `--illegal-access` JVM flag removed — workarounds from 9 migration no longer valid
- `SecurityManager` deprecated for removal
- Applet API deprecated

**Rules:** `DEPRECATION_SECURITY_MANAGER`, `BUILD_ILLEGAL_ACCESS_FLAG`

### 17 -> 21 — deprecations finalized

**Breaking changes:**

- `Object.finalize()` deprecated for removal
- `Thread.stop()`, `Thread.suspend()`, `Thread.resume()` removed

**Rules:** `DEPRECATION_FINALIZE`, `API_THREAD_STOP_REMOVED`

### 21 -> 25 — new LTS target (supported until 2033)

**Key improvements (not breaking — modernization opportunities):**

- Scoped values — safer alternative to ThreadLocal (stable)
- Structured concurrency — stable
- Applet API fully removed

**Rules:** `CONCURRENCY_THREADLOCAL_SCOPED_VALUE`, `CONCURRENCY_SYNCHRONIZED_STRUCTURED`, `DEPRECATION_APPLET_API`

**Licensing note:** Java 21 free Oracle updates expire September 2026. Java 25 is free under NFTC until September 2027.

---

## modernization opportunities (any version -> 25)

| Old pattern                      | Java 25 equivalent        | Rule                                   | Effort  |
|----------------------------------|---------------------------|----------------------------------------|---------|
| Anonymous class                  | Lambda / method reference | `LANGUAGE_ANONYMOUS_CLASS_LAMBDA`      | TRIVIAL |
| `Date` / `Calendar`              | `java.time`               | `LANGUAGE_OLD_DATETIME_API`            | LOW     |
| `instanceof` + cast              | Pattern matching          | `LANGUAGE_INSTANCEOF_CAST`             | TRIVIAL |
| Mutable data class               | Record                    | `LANGUAGE_RECORD_OPPORTUNITY`          | LOW     |
| `Thread` / thread pool           | Virtual thread (Loom)     | `CONCURRENCY_THREAD_VIRTUAL`           | MEDIUM  |
| `ThreadLocal`                    | `ScopedValue`             | `CONCURRENCY_THREADLOCAL_SCOPED_VALUE` | MEDIUM  |
| `synchronized` block             | Structured concurrency    | `CONCURRENCY_SYNCHRONIZED_STRUCTURED`  | HIGH    |
| `javax.*` dependency coordinates | `jakarta.*`               | `BUILD_JAVAX_TO_JAKARTA_COORDS`        | LOW     |

---

## rule engine

### design — code-driven, not data-driven

Rules are Java classes, not database rows. Each rule is a stateless object that declares what to detect and what to
suggest. The rule engine applies them to AST nodes. Rules are version-controlled, testable, and reviewable in PRs.

### rule sets

| Class              | Category    | Rules | Boundary                 |
|--------------------|-------------|-------|--------------------------|
| `JpmsRules`        | JPMS        | 4     | 8 -> 9                   |
| `ApiRemovalRules`  | API_REMOVAL | 5     | 9 -> 11, 17 -> 21        |
| `DeprecationRules` | DEPRECATION | 3     | 11 -> 17, 21 -> 25       |
| `LanguageRules`    | LANGUAGE    | 4     | 8 -> 25 (modernization)  |
| `ConcurrencyRules` | CONCURRENCY | 3     | 21 -> 25 (modernization) |
| `BuildRules`       | BUILD       | 3     | All boundaries           |

### data model

**MigrationRule**

| Field          | Type                     | Description                                                  |
|----------------|--------------------------|--------------------------------------------------------------|
| `ruleId`       | String                   | Unique key e.g. `JPMS_SUN_IMPORT`                            |
| `category`     | RuleCategory             | JPMS, API_REMOVAL, DEPRECATION, LANGUAGE, CONCURRENCY, BUILD |
| `severity`     | Severity                 | BREAKING, DEPRECATED, MODERNIZATION                          |
| `effort`       | Effort                   | TRIVIAL, LOW, MEDIUM, HIGH, MANUAL                           |
| `introducedIn` | JavaVersion              | Version where concern is introduced                          |
| `removedIn`    | JavaVersion              | Version where concern becomes a hard break (nullable)        |
| `patterns`     | List\<DetectionPattern\> | One or more detection patterns                               |
| `fixTemplate`  | FixSuggestion            | Template fix — LLM enriches explanation                      |
| `referenceUrl` | String                   | Official JEP or migration guide link                         |

**DetectionPattern**

| Field        | Type        | Description                                               |
|--------------|-------------|-----------------------------------------------------------|
| `type`       | PatternType | AST_NODE, IMPORT, API_CALL, ANNOTATION, REFLECTION, BUILD |
| `matcher`    | String      | Pattern string — e.g. `sun.misc.*`                        |
| `nodeType`   | String      | JavaParser AST node class (AST_NODE type only)            |
| `confidence` | Float       | 0.0 to 1.0                                                |

**FixSuggestion**

| Field            | Type    | Description                                                                |
|------------------|---------|----------------------------------------------------------------------------|
| `fixType`        | FixType | IMPORT_REPLACE, API_REPLACE, REFACTOR, ADD_DEPENDENCY, MODULE_INFO, MANUAL |
| `originalCode`   | String  | The detected code snippet                                                  |
| `suggestedCode`  | String  | Replacement code or guidance                                               |
| `explanation`    | String  | LLM-generated rationale (empty until LLM enriches)                         |
| `autoApplicable` | Boolean | Safe to apply without developer review                                     |

**Finding**

| Field          | Type          | Description                                       |
|----------------|---------------|---------------------------------------------------|
| `findingId`    | String        | Unique within scan                                |
| `ruleId`       | String        | Rule that produced this finding                   |
| `severity`     | Severity      | Inherited from rule                               |
| `filePath`     | String        | Absolute path to affected file                    |
| `lineNumber`   | int           | Line where finding was detected                   |
| `originalCode` | String        | Snippet that triggered the finding                |
| `confidence`   | float         | Pattern confidence at time of match               |
| `referenceUrl` | String        | Denormalized from MigrationRule at detection time |
| `status`       | FindingStatus | OPEN, ACCEPTED, REJECTED, DEFERRED                |

**ScanResult**

| Field             | Type            | Description                            |
|-------------------|-----------------|----------------------------------------|
| `scanId`          | UUID            | Primary key                            |
| `repoPath`        | String          | Absolute path to scanned repository    |
| `detectedVersion` | JavaVersion     | Detected from pom.xml / build.gradle   |
| `targetVersion`   | JavaVersion     | Always V25                             |
| `findings`        | List\<Finding\> | All findings produced by rule engine   |
| `riskScore`       | Integer         | 0-100 aggregate risk score             |
| `phase`           | MigrationPhase  | ANALYSIS, PLANNING, FIXING, VALIDATION |

### enums

```
Severity:       BREAKING, DEPRECATED, MODERNIZATION
Effort:         TRIVIAL, LOW, MEDIUM, HIGH, MANUAL
RuleCategory:   JPMS, API_REMOVAL, DEPRECATION, LANGUAGE, CONCURRENCY, BUILD
JavaVersion:    V8, V9, V11, V17, V21, V25
MigrationPhase: ANALYSIS, PLANNING, FIXING, VALIDATION
FixType:        IMPORT_REPLACE, API_REPLACE, REFACTOR, ADD_DEPENDENCY, MODULE_INFO, MANUAL
PatternType:    AST_NODE, IMPORT, API_CALL, ANNOTATION, REFLECTION, BUILD
FindingStatus:  OPEN, ACCEPTED, REJECTED, DEFERRED
```

---

## migration phases

### Phase 1 — Analysis

- Detect source Java version from `pom.xml` / `build.gradle`
- Scan AST for migration risks using rule engine
- Produce migration report: findings, risk score, effort estimate per boundary
- Read-only — no code changes

### Phase 2 — Planning

- Plot incremental path: 8->11->17->21->25 (LTS to LTS)
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
- Track progress: 847 findings -> 203 remaining
- Generate SARIF report for CI integration

---

## tech stack

| Layer               | Technology                              | Version       | Justification                                                         |
|---------------------|-----------------------------------------|---------------|-----------------------------------------------------------------------|
| Backend             | Quarkus LTS                             | 3.33.1        | Latest LTS (March 2026), Kubernetes-native, CDI, health probes        |
| AST Analysis        | JavaParser                              | 3.28.0        | Java 1-25 support, SymbolSolver for type resolution                   |
| LLM Orchestration   | LangChain4j                             | 1.13.0        | Java-native, Ollama + Anthropic support                               |
| Embeddings          | langchain4j-embeddings-all-minilm-l6-v2 | 0.36.2        | Pure Java ONNX, 384-dim vectors — pinned (not in 1.13.0)              |
| Vector Store        | quarkus-langchain4j-pgvector            | 1.8.4         | Quarkiverse extension — latest stable (Mar 2026)                      |
| LLM — code          | codellama:13b                           | via Ollama    | Best local code reasoning on M1 Pro 32GB                              |
| LLM — prose         | llama3.1:8b                             | via Ollama    | Better natural language for report narrative                          |
| LLM — deep (opt-in) | claude-sonnet-4-6                       | Anthropic API | Complex refactors requiring deep reasoning                            |
| Frontend            | Next.js                                 | 15            | TypeScript, React, Tailwind                                           |
| Deployment          | Kubernetes + Helm                       | —             | Cloud-native from day one                                             |
| CI                  | GitHub Actions                          | —             | SARIF output, Gradle test runner                                      |
| Build JDK           | Temurin                                 | 25.0.2-tem    | Matches migration target; installed via SDKMAN                        |
| Lombok              | Lombok                                  | 1.18.42       | Java 25 support added in 1.18.40; 1.18.36 does not compile on Java 25 |

### Dependency coordinate corrections (learned during implementation)

| Wrong                                                            | Correct                                                              | Reason                                                                                    |
|------------------------------------------------------------------|----------------------------------------------------------------------|-------------------------------------------------------------------------------------------|
| `dev.langchain4j:langchain4j-pgvector:1.13.0`                    | `io.quarkiverse.langchain4j:quarkus-langchain4j-pgvector:1.8.4`      | Artifact does not exist at 1.13.0 — use Quarkiverse extension                             |
| `dev.langchain4j:langchain4j-embeddings-all-minilm-l6-v2:1.13.0` | same groupId, version `0.36.2`                                       | Last release was Nov 2024; not updated to 1.13.0                                          |
| `io.quarkus:quarkus-junit5`                                      | `io.quarkus:quarkus-junit`                                           | Renamed in Quarkus 3.31                                                                   |
| `org.projectlombok:lombok:1.18.36`                               | `org.projectlombok:lombok:1.18.42`                                   | 1.18.40+ required for Java 25 support                                                     |
| `ChatLanguageModel`                                              | `ChatModel`                                                          | Renamed in LangChain4j 1.0 GA — `ChatLanguageModel` only exists in beta artifacts         |
| `AiServices.builder().chatLanguageModel()`                       | `.chatModel()`                                                       | Setter renamed to match class rename in 1.0 GA                                            |
| `quarkus-langchain4j-bom` alongside `langchain4j-bom`            | Drop `quarkus-langchain4j-bom` entirely                              | Two `enforcedPlatform` with `strictly` constraints on the same artifacts deadlocks Gradle |
| `langchain4j-http-client-jdk` transitive                         | Exclude globally in root `build.gradle.kts` via `configurations.all` | Conflicts with Quarkiverse JAX-RS HTTP client — causes `IllegalStateException` at runtime |

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
│   ├── libs.versions.toml
│   └── wrapper/gradle-wrapper.properties
├── anneal-api/
│   ├── build.gradle.kts
│   └── src/main/java/com/rish/anneal/api/
│       ├── resource/ScanResource.java       4 endpoints + LLM enrichment + embedding
│       ├── dto/FindingDto.java              llmExplanation + llmProvider + llmModel (20 fields)
│       ├── model/LlmProvider.java           enum — OLLAMA, ANTHROPIC
│       ├── mapper/ScanMapper.java           pattern-matches LlmModel → LlmProvider + modelName
│       └── registry/RuleRegistry.java
├── anneal-core/
│   ├── build.gradle.kts
│   └── src/main/java/com/rish/anneal/core/
│       ├── model/                           Finding has referenceUrl field
│       ├── rule/                            ApiRemovalRules has ANNOTATION patterns
│       ├── engine/                          RuleEngine ANNOTATION active
│       └── scanner/
├── anneal-llm/
│   ├── build.gradle.kts
│   └── src/main/java/com/rish/anneal/llm/
│       ├── config/LlmConfig.java            @ConfigMapping nested YAML structure
│       ├── provider/LlmProviderFactory.java raw ChatModel instances (code, prose, cloud)
│       ├── service/FixEnrichmentService.java enrichAll() takes Map<ruleId, MigrationRule>
│       ├── service/EmbeddingService.java
│       ├── impl/LangChain4jEnrichmentService.java direct ChatModel.chat(system, user)
│       ├── impl/MiniLmEmbeddingService.java
│       ├── model/EnrichedFix.java           findingId + explanation + LlmModel (sealed)
│       ├── model/LlmModel.java              sealed interface — Ollama | Anthropic records
│       └── prompt/FixPrompts.java           3 system messages + version-fact injection + clean()
├── anneal-store/
│   ├── build.gradle.kts
│   └── src/main/java/com/rish/anneal/store/
│       ├── entity/FindingEntity.java        reference_url column
│       ├── entity/FindingEmbeddingEntity.java vector(384)
│       └── repository/EmbeddingRepository.java cosine search
├── anneal-ui/                               Next.js 15 — brutalist, molten orange
├── build.gradle.kts                         quarkus-bom + langchain4j-bom + global exclude
├── settings.gradle.kts
├── gradlew / gradlew.bat
├── docker-compose.yml
├── ARCHITECTURE.md
└── README.md
```

---

## LLM layer

### module: anneal-llm

**`LlmConfig`** — `@ConfigMapping(prefix = "anneal.llm")`. Nested `Ollama` and `Anthropic` interfaces matching the YAML
hierarchy exactly. Flat keys cause `SRCFG00050` config validation errors at startup.

**`LlmProviderFactory`** — `@ApplicationScoped`. Builds two `OllamaChatModel` instances at `@PostConstruct` (code
model + prose model). Cloud `AnthropicChatModel` built only if `allow-cloud-fallback: true` and API key present.
Routing:

```
MANUAL effort + cloud enabled  -> Anthropic (claude-sonnet-4-6)
BREAKING severity              -> code model (codellama:13b)
DEPRECATED / MODERNIZATION     -> prose model (llama3.1:8b)
```

**`LlmProviderFactory`** — `@ApplicationScoped`. Builds raw `ChatModel` instances at `@PostConstruct` — one per model
role (code, prose, cloud). Exposes `codeModel()`, `proseModel()`, and `cloudModel(): Optional<ChatModel>`. Cloud model
built only if `allow-cloud-fallback: true` and API key present. Raw `ChatModel` instances are used directly rather than
AiService proxies — per-finding system messages require call-level control that proxies cannot provide.

**`LangChain4jEnrichmentService`** — calls `ChatModel.chat(SystemMessage, UserMessage)` directly. System message is
selected per finding based on model role (`CODE_SYSTEM`, `PROSE_SYSTEM`, `CLOUD_SYSTEM`). `enrich()` wraps each LLM call
in try/catch — failures return `Optional.empty()`, never throw. `enrichAll()` takes `Map<ruleId, MigrationRule>` so
version facts can be injected as hard constraints into prompts. Returns `Map<findingId, EnrichedFix>` — only successful
enrichments included.

**`LlmModel`** — sealed interface with two permitted record implementations: `Ollama(String modelName)` and
`Anthropic(String modelName)`. Carried on `EnrichedFix` so the provider and model name flow to the API response without
string parsing. Pattern-matched in `ScanMapper` to populate `FindingDto.llmProvider` (enum) and `FindingDto.llmModel`
(String — config-driven, open set).

**`MiniLmEmbeddingService`** — `AllMiniLmL6V2EmbeddingModel` loaded from ONNX classpath at `@PostConstruct`. No network
call. Input text: `ruleId + severity + originalCode` (truncated to 256 chars for MiniLM token limit). Output:
`float[384]`.

**`FixPrompts`** — three system messages (`CODE_SYSTEM`, `PROSE_SYSTEM`, `CLOUD_SYSTEM`) tailored to each model's
instruction-following style. User message template injects `introducedIn` and `removedIn` from `MigrationRule` under a
clearly labelled "Version facts" section with an explicit instruction not to override them — addresses observed
`codellama:13b` version hallucination. `clean()` strips `[INST]`, `[/INST]`, `<<SYS>>`, `<</SYS>>`, `<s>`, `</s>`
artefacts — called on every model response unconditionally.

### module: anneal-store (LLM additions)

**`FindingEmbeddingEntity`** — `@Table(schema = "anneal", name = "finding_embeddings")`. One row per finding.
`embedding` column: `vector(384)`. Includes `embeddedText` for audit.

**`EmbeddingRepository`** — `save()` is idempotent (deletes before insert — safe for re-scans). `findSimilar()` uses
native SQL with pgvector `<=>` cosine operator, excludes current scan, returns N closest from past scans.

### scan flow (complete)

```
POST /api/scan
  -> validate path exists and is directory
  -> resolve source version (caller-specified or VersionDetector auto-detect)
  -> RuleRegistry.rulesFor(source, V25)
  -> index rules by ruleId: Map<String, MigrationRule>        O(1) lookup in enrichAll()
  -> CodebaseScanner.scan()
  -> ScanResultRepository.save()
  -> FixEnrichmentService.enrichAll(findings, ruleById)        LLM — failure-isolated per finding
  -> for each finding:
       EmbeddingService.embed()                                ONNX — no network
       EmbeddingRepository.save()                              pgvector — try/catch, non-critical
  -> build FindingDtos with EnrichedFix injected               ScanMapper.toFindingDto(finding, fix)
       llmExplanation = fix.explanation()
       llmProvider    = OLLAMA | ANTHROPIC (pattern-matched from fix.model())
       llmModel       = fix.model().modelName()
  -> 200 OK with ScanResponse

GET /api/scans
  -> ScanResultRepository.findAll()
  -> ScanMapper.toSummary() per entity
  -> 200 OK with List<ScanSummaryDto>

GET /api/scans/{scanId}
  -> ScanResultRepository.findByScanId()
  -> ScanResultRepository.findingsByScanId()
  -> ScanMapper.fromEntity()   — llmExplanation/llmProvider/llmModel = null, not re-enriched
  -> 200 OK with ScanResponse | 404 if not found
```

### configuration

```yaml
anneal:
  llm:
    provider: ollama
    ollama:
      base-url: http://localhost:11434
      fix-model: codellama:13b
      prose-model: llama3.1:8b
    anthropic:
      api-key: ${ANTHROPIC_API_KEY:}
      model: claude-sonnet-4-6
    allow-cloud-fallback: false
    timeout-seconds: 120
    enrichment-enabled: true
```

### design decisions

| Date       | Decision                                             | Rationale                                                                               |
|------------|------------------------------------------------------|-----------------------------------------------------------------------------------------|
| 2026-04-23 | `AiServices.builder()` over `@RegisterAiService`     | Programmatic — no Quarkus LangChain4j CDI plugin required in anneal-llm                 |
| 2026-04-23 | Per-finding embeddings, not per-file chunks          | Enrichment target is a specific code snippet, not a document corpus                     |
| 2026-04-23 | Embed: ruleId + severity + originalCode              | Enough signal for similarity; truncated to 256 chars for MiniLM token limit             |
| 2026-04-23 | `finding_embeddings` separate from `code_embeddings` | Different use cases — per-finding vs file-chunk RAG (future)                            |
| 2026-04-23 | `llmExplanation` not persisted                       | Runtime enrichment — history returns null honestly; avoids staleness when model changes |
| 2026-04-23 | Failure isolation in `enrichAll()`                   | One bad LLM call must not fail the scan response                                        |
| 2026-04-23 | `FixPrompts.clean()` strips `[INST...]` artifacts    | codellama:13b bleeds prompt continuation into responses                                 |
| 2026-04-23 | `beans.xml` in anneal-llm META-INF                   | Quarkus CDI bean discovery requires it in non-API modules                               |
| 2026-04-23 | Global exclude of `langchain4j-http-client-jdk`      | Conflicts with Quarkiverse JAX-RS client at runtime                                     |
| 2026-04-23 | `quarkus-langchain4j-ollama` extension in anneal-api | Quarkiverse deployment processor requires a registered provider at build time           |
| 2026-04-23 | `LlmConfig` nested interface structure               | Flat keys cause `SRCFG00050` config validation errors                                   |
| 2026-04-24 | Direct `ChatModel.chat(system, user)` over AiService | Per-finding system messages require call-level control — proxies are session-scoped     |
| 2026-04-24 | `LlmModel` sealed interface with Ollama/Anthropic    | Compiler-enforced exhaustive switch in ScanMapper — adding a provider is a compile error |
| 2026-04-24 | `LlmProvider` enum on `FindingDto`                   | Closed set (OLLAMA, ANTHROPIC) — enum; model name is config-driven open set — String    |
| 2026-04-24 | Version facts injected as hard constraints in prompt | Prevents codellama:13b from substituting parametric version memory with hallucinations  |
| 2026-04-24 | `enrichAll()` takes `Map<ruleId, MigrationRule>`     | Rule is required to inject introducedIn/removedIn facts — can't anchor prompt without it|
| 2026-04-24 | Three system messages per model role                 | CODE, PROSE, CLOUD respond to different instruction styles; shared user message template |
| 2026-04-24 | `CloudModelValidationIT` gated by env var            | Cloud harness is opt-in QA, not a CI gate — `@EnabledIfEnvironmentVariable` is clean    |

### model quality observations

- `codellama:13b` produces similar explanations for related findings — acceptable for local free inference
- `codellama:13b` hallucinates version numbers in prose (e.g. stating removal in Java 11 when it was Java 21) — fixed
  by injecting `introducedIn` and `removedIn` from `MigrationRule` as hard constraints in `FixPrompts.userMessage()`.
  The "Version facts" section explicitly instructs the model not to substitute its own version knowledge.
- `affectsVersion` from deterministic detection is always authoritative; prose is supplementary context only
- `claude-sonnet-4-6` via cloud fallback expected to produce significantly higher quality explanations — validation
  harness available in `CloudModelValidationIT` (opt-in, requires `ANTHROPIC_API_KEY`)

---

## LLM strategy

### model roles

| Role                    | Task                                | Model                                 |
|-------------------------|-------------------------------------|---------------------------------------|
| Detection               | AST traversal, rule matching        | None — fully deterministic            |
| Fix explanation — code  | BREAKING severity findings          | `codellama:13b` (default)             |
| Fix explanation — prose | DEPRECATED / MODERNIZATION findings | `llama3.1:8b` (default)               |
| Deep reasoning          | MANUAL effort findings              | `claude-sonnet-4-6` — explicit opt-in |

### supported providers

| Provider         | Models                         | Use case                          |
|------------------|--------------------------------|-----------------------------------|
| Ollama (default) | `codellama:13b`, `llama3.1:8b` | Local, private, free              |
| Anthropic        | `claude-sonnet-4-6`            | Deep reasoning, complex refactors |

`allow-cloud-fallback: false` is the default — code never leaves the machine without explicit user consent.

---

## risk score

### formula

Each finding contributes a weighted score based on severity, confidence, and effort:

```
findingScore = severityWeight x confidence x effortMultiplier
```

**Severity weights:**

```
BREAKING       = 10
DEPRECATED     =  5
MODERNIZATION  =  1
```

**Effort multipliers:**

```
MANUAL   = 1.5
HIGH     = 1.3
MEDIUM   = 1.1
LOW      = 1.0
TRIVIAL  = 0.8
```

**Aggregate:**

```
rawScore  = sum of all findingScores
riskScore = min(100, rawScore)
```

### risk bands

| Score  | Band     | Meaning                                                          |
|--------|----------|------------------------------------------------------------------|
| 0-20   | LOW      | Mostly modernization opportunities, no blockers                  |
| 21-50  | MEDIUM   | Some deprecated APIs, addressable incrementally                  |
| 51-80  | HIGH     | Multiple breaking changes — plan carefully                       |
| 81-100 | CRITICAL | JPMS violations or mass API removals — must fix before migrating |

### examples

| Finding                          | Severity      | Confidence | Effort | Score |
|----------------------------------|---------------|------------|--------|-------|
| `JPMS_SUN_IMPORT`                | BREAKING      | 1.0        | HIGH   | 13.0  |
| `JPMS_ILLEGAL_REFLECTIVE_ACCESS` | BREAKING      | 0.7        | HIGH   | 9.1   |
| `API_JAXB_REMOVED`               | BREAKING      | 1.0        | LOW    | 10.0  |
| `DEPRECATION_FINALIZE`           | DEPRECATED    | 0.9        | MEDIUM | 4.95  |
| `LANGUAGE_RECORD_OPPORTUNITY`    | MODERNIZATION | 0.5        | LOW    | 0.5   |

A project with 5 JPMS violations hits CRITICAL immediately — the ceiling is intentional.

### implementation

`RiskScoreCalculator` in `anneal-core/engine/` — stateless, deterministic, no LLM involvement.

---

## scanner layer

### classes

| Class              | Package               | Responsibility                                                              |
|--------------------|-----------------------|-----------------------------------------------------------------------------|
| `RuleEngine`       | `anneal-core/engine`  | Applies rules to a parsed `CompilationUnit` — stateless, deterministic      |
| `CodebaseScanner`  | `anneal-core/scanner` | Walks repo, parses `.java` files, coordinates rule engine and build scanner |
| `BuildFileScanner` | `anneal-core/scanner` | Text-based scanning of `pom.xml`, `build.gradle`, `build.gradle.kts`        |

### pattern dispatch

`RuleEngine` dispatches on `PatternType`:

| PatternType | Handler             | Notes                                                         |
|-------------|---------------------|---------------------------------------------------------------|
| IMPORT      | `matchImport()`     | Wildcard and exact match support                              |
| API_CALL    | `matchApiCall()`    | Extracts method name from `java.lang.Thread#stop()` format    |
| AST_NODE    | `matchAstNode()`    | MethodDeclaration and ObjectCreationExpr supported            |
| REFLECTION  | `matchReflection()` | Matches method call by name                                   |
| ANNOTATION  | `matchAnnotation()` | Simple name + FQN + trailing segment match via AnnotationExpr |
| BUILD       | `BuildFileScanner`  | Separate scanner — not handled by RuleEngine                  |

### ANNOTATION pattern

`matchAnnotation()` uses JavaParser's `AnnotationExpr` visitor. Matches simple name, FQN, or trailing segment — catches
annotation usage with and without an import statement. Applied in `ApiRemovalRules.javaxAnnotationRemoved()` for
`@PostConstruct` (0.9), `@PreDestroy` (0.9), `@Resource` (0.7 — lower confidence, exists in other namespaces).

### design decisions

- `target/` and `build/` directories excluded from scan
- JavaParser configured with `ReflectionTypeSolver` + `JavaParserTypeSolver` for type resolution
- Language level set to `JAVA_21` — highest stable level JavaParser supports
- `BuildFileScanner` uses text-based line scanning — simpler and faster than XML parsing
- Parse errors are logged as warnings and skipped — scan continues on malformed files
- `CodebaseScanner` is stateless — safe to reuse across multiple scans
- `referenceUrl` denormalized onto `Finding` in `RuleEngine.buildFinding()` — rule is in scope at detection time; mapper
  stays stateless

---

## build system

### Gradle 9.4.1 — Kotlin DSL

Migrated from Maven to Gradle mid-project. A Java modernization tool that recommends Gradle to its users should be built
with Gradle. Dogfooding.

### structure

| File                            | Purpose                                                             |
|---------------------------------|---------------------------------------------------------------------|
| `gradle/libs.versions.toml`     | Version catalog — single source of truth                            |
| `settings.gradle.kts`           | Root settings — declares all submodules                             |
| `build.gradle.kts`              | Root — quarkus-bom, langchain4j-bom, global http-client-jdk exclude |
| `anneal-core/build.gradle.kts`  | Core deps only — JavaParser                                         |
| `anneal-store/build.gradle.kts` | Panache, Flyway, quarkus-langchain4j-pgvector                       |
| `anneal-llm/build.gradle.kts`   | LangChain4j core, Ollama, Anthropic, embeddings (NOT pgvector)      |
| `anneal-api/build.gradle.kts`   | Quarkus plugin, health, OpenAPI, quarkus-langchain4j-ollama         |

### BOM strategy

`quarkus-langchain4j-bom` was removed from root build. It locked LangChain4j to 1.11.0 via `strictly` constraints — two
`enforcedPlatform` blocks with conflicting `strictly` versions deadlock Gradle resolution. Root build declares only
`quarkus-bom` and `langchain4j-bom:1.13.0`.

```kotlin
dependencies {
    implementation(enforcedPlatform("io.quarkus.platform:quarkus-bom:3.33.1"))
    implementation(enforcedPlatform("dev.langchain4j:langchain4j-bom:1.13.0"))
}

configurations.all {
    exclude(group = "dev.langchain4j", module = "langchain4j-http-client-jdk")
}
```

### version catalog

```toml
[versions]
quarkus = "3.33.1"
langchain4j = "1.13.0"
langchain4j-embeddings = "0.36.2"
langchain4j-pgvector = "1.8.4"
javaparser = "3.28.0"
lombok = "1.18.42"
```

### Gradle wrapper

Always use `./gradlew` — never the global `gradle` command.

```bash
./gradlew compileJava
./gradlew test
./gradlew :anneal-api:quarkusDev
```

---

## REST layer

### endpoints

| Method | Path                  | Description                                                     |
|--------|-----------------------|-----------------------------------------------------------------|
| GET    | `/api/health`         | Liveness check                                                  |
| POST   | `/api/scan`           | Scan a Java repository — returns findings with LLM explanations |
| GET    | `/api/scans`          | List all past scans, most recent first                          |
| GET    | `/api/scans/{scanId}` | Get specific scan with all findings                             |

### classes

| Class                    | Package                   | Responsibility                                                            |
|--------------------------|---------------------------|---------------------------------------------------------------------------|
| `ScanResource`           | `anneal-api/resource`     | 4 endpoints — scan flow includes LLM enrichment + embedding orchestration |
| `ScanRequest`            | `anneal-api/dto`          | Request record — `repoPath` + optional `sourceVersion`                    |
| `ScanResponse`           | `anneal-api/dto`          | Response — findings, risk score, boundary scores                          |
| `ScanSummaryDto`         | `anneal-api/dto`          | Lightweight list view — no findings                                       |
| `FindingDto`             | `anneal-api/dto`          | Individual finding — `referenceUrl` + `llmExplanation`                    |
| `ScanMapper`             | `anneal-api/mapper`       | Stateless. Overloaded `toFindingDto(finding, explanation)` for LLM wiring |
| `ScanResultEntity`       | `anneal-store/entity`     | `anneal.scan_results` table                                               |
| `FindingEntity`          | `anneal-store/entity`     | `anneal.findings` table — includes `reference_url`                        |
| `FindingEmbeddingEntity` | `anneal-store/entity`     | `anneal.finding_embeddings` — `vector(384)`                               |
| `ScanResultRepository`   | `anneal-store/repository` | Persist and retrieve scans and findings                                   |
| `EmbeddingRepository`    | `anneal-store/repository` | Persist and cosine-search finding embeddings                              |

### design decisions

- All DTOs are Java 25 records — no Lombok, no boilerplate
- `@NotBlank` on `ScanRequest.repoPath` — Quarkus returns 400 automatically
- Findings sorted by severity then confidence descending — most critical first
- `referenceUrl` denormalized onto `Finding` at detection time — mapper stays stateless
- `llmExplanation` injected at response build time — not persisted
- History view returns `llmExplanation: null` — not re-enriched on retrieval
- `beans.xml` required in `anneal-store` and `anneal-llm` META-INF for CDI bean discovery
- Sequences in `public` schema — Hibernate resolves without schema prefix

---

## persistence

### schema: anneal

| Table                | Purpose                                                         |
|----------------------|-----------------------------------------------------------------|
| `scan_results`       | One row per scan — metadata, risk score, phase                  |
| `findings`           | One row per finding — all rule match data, `reference_url`      |
| `finding_embeddings` | One row per finding — 384-dim vector, `embedded_text` for audit |

> `code_embeddings` was scaffolded in V1 for file-chunk RAG — reserved for future use, not actively written to.

### Flyway migrations

All migrations in `anneal-store/src/main/resources/db/migration/`. Configured in `anneal-api/application.yml` with
`baseline-on-migrate: true`.

| Version | File                                    | Description                                                           |
|---------|-----------------------------------------|-----------------------------------------------------------------------|
| V1      | `V1__init.sql`                          | Creates `anneal` schema, `scan_results`, `findings` tables            |
| V2      | `V2__add_sequences.sql`                 | Adds `scan_results_seq`, `findings_seq`                               |
| V3      | `V3__add_reference_url_to_findings.sql` | Adds `reference_url VARCHAR(512)` — nullable                          |
| V4      | `V4__create_finding_embeddings.sql`     | Creates `finding_embeddings` with `vector(384)`, IVFFlat cosine index |
| V5      | `V5__add_finding_embeddings_seq.sql`    | Adds `finding_embeddings_seq` — required by Hibernate                 |

---

## testing

### test classes

| Class                     | Module        | Type        | Tests                                                                             |
|---------------------------|---------------|-------------|-----------------------------------------------------------------------------------|
| `RiskScoreCalculatorTest` | `anneal-core` | Unit        | 14 — formula, bands, per-boundary, test-legacy score                              |
| `RuleEngineTest`          | `anneal-core` | Unit        | 9 — import, wildcard, API call, reflection, AST node, version filtering           |
| `VersionDetectorTest`     | `anneal-core` | Unit        | 9 — Maven release/source/legacy, Gradle toolchain/sourceCompatibility, edge cases |
| `ScanResourceTest`        | `anneal-api`  | Integration | 6 — health, 400s, valid scan, legacy findings, list, 404                          |

**Total: 38 tests, all passing**

### test design decisions

- `RiskScoreCalculatorTest` includes a test mirroring the exact test-legacy output (score 66) — regression anchor
- `VersionDetectorTest` uses `@TempDir` — no real file system state
- `RuleEngineTest` uses `StaticJavaParser.parse()` directly — fast, no file I/O
- `ScanResourceTest` uses Quarkus dev services (`pgvector/pgvector:pg16`) — real postgres, no H2 mocking
- `FindingStatus` is a nested enum inside `Finding.java` — referenced as `Finding.FindingStatus.OPEN`

### running tests

```bash
./gradlew test
./gradlew :anneal-core:test
./gradlew :anneal-api:test
```

---

## frontend

### tech stack

| Layer           | Technology               | Version         |
|-----------------|--------------------------|-----------------|
| Framework       | Next.js                  | 15              |
| Language        | TypeScript               | 5               |
| Styling         | CSS variables + Tailwind | —               |
| Font            | IBM Plex Mono            | via @fontsource |
| Package manager | npm                      | —               |

### design system

| Token          | Value     | Usage                                     |
|----------------|-----------|-------------------------------------------|
| `--bg`         | `#0a0a0a` | Page background                           |
| `--surface`    | `#111111` | Card / input background                   |
| `--border`     | `#1e1e1e` | Borders, dividers                         |
| `--foreground` | `#e0e0e0` | Primary text                              |
| `--accent`     | `#FF6B35` | Molten orange — buttons, highlights, logo |
| `--breaking`   | `#e53e3e` | BREAKING severity                         |
| `--warning`    | `#f0b429` | DEPRECATED severity                       |
| `--success`    | `#4caf7d` | MODERNIZATION severity                    |

**Aesthetic:** Brutalist dark — IBM Plex Mono throughout, raw borders, no rounded corners, high contrast. Consistent
with `codebase-chat` and `master-data-ui` — deliberate portfolio signature.

### components

| Component     | Responsibility                                                                         |
|---------------|----------------------------------------------------------------------------------------|
| `Header`      | Sticky header — logo, subtitle, target version                                         |
| `ScanPanel`   | Repo path input, version selector, scan button, status message                         |
| `RiskScore`   | Score display, risk band, per-boundary breakdown with progress bars                    |
| `FindingCard` | Expandable finding — severity badge, original code, suggested fix, accept/reject/defer |

### pages

| Page | Path | Description               |
|------|------|---------------------------|
| Home | `/`  | Scan input + results view |

### decisions

| Date       | Decision                                      | Rationale                                                                 |
|------------|-----------------------------------------------|---------------------------------------------------------------------------|
| 2026-04-21 | Brutalist theme with molten orange `#FF6B35`  | Matches anneal metallurgy metaphor; consistent portfolio signature        |
| 2026-04-21 | IBM Plex Mono throughout                      | Developer tool aesthetic; monospace signals precision                     |
| 2026-04-21 | CSS variables over Tailwind for design tokens | Direct control; easier to override per component                          |
| 2026-04-21 | `@fontsource/ibm-plex-mono`                   | Self-hosted font — no Google Fonts dependency                             |
| 2026-04-21 | Accept/reject/defer state is local only       | Backend PATCH endpoint not yet wired — deferred to Phase 3                |
| 2026-04-24 | `llmExplanation` wired into FindingCard       | Orange left-rail block between suggested fix and action buttons           |
| 2026-04-24 | `llmProvider` + `llmModel` in FindingDto      | Provider is enum (closed set); model name is String (config-driven)       |
| 2026-04-24 | "via {model}" attribution in FindingCard      | Transparency — developer sees which model produced the explanation        |
| 2026-04-24 | No attribution label when llmModel is null    | History retrieval returns null — no apology text, just silence            |

---

## CI/CD

### GitHub Actions — `.github/workflows/ci.yml`

Two jobs — `test` then `build`.

**test job:**

- Spins up `pgvector/pgvector:pg16` as a service
- Sets up Java 25 Temurin
- Runs `./gradlew test` with env vars pointing at the CI postgres
- Uploads test reports as artifacts on failure

**build job:**

- Runs after `test` passes
- Runs `./gradlew build -x test`
- Generates `llms-full.txt` from README + ARCHITECTURE.md (gitignored, CI artifact)
- Uploads `llms-full.txt` as a build artifact

### decisions

| Date       | Decision                                              | Rationale                                                          |
|------------|-------------------------------------------------------|--------------------------------------------------------------------|
| 2026-04-22 | pgvector/pgvector:pg16 as CI service                  | Same image as dev — vector extension available                     |
| 2026-04-22 | Sequences in V2 Flyway migration                      | V1 checksum mismatch if modified after applied                     |
| 2026-04-22 | `QUARKUS_DATASOURCE_DEVSERVICES_ENABLED: false` in CI | CI provides real postgres service                                  |
| 2026-04-22 | `if: always()` on test report upload                  | Reports accessible on both pass and fail                           |
| 2026-04-22 | All Flyway migrations in `anneal-store`               | Persistence owns schema — not the API layer                        |
| 2026-04-22 | `baseline-on-migrate: true`                           | Flyway sees non-empty `public` schema (pgvector extension objects) |
| 2026-04-24 | `llms-full.txt` generated in CI, gitignored           | Always in sync with README + ARCHITECTURE.md; never stale in repo  |
| 2026-04-24 | `llms.txt` checked in, `llms-full.txt` gitignored     | llms.txt is hand-curated; llms-full.txt is generated               |

---

## current status

| Layer                       | Status                                                                         |
|-----------------------------|--------------------------------------------------------------------------------|
| Domain model                | ✅ Complete — 12 model classes, all enums                                       |
| Rule engine                 | ✅ Complete — 22 rules, 6 categories, 8->25 coverage, ANNOTATION pattern active |
| Risk calculator             | ✅ Complete — weighted formula, per-boundary breakdown                          |
| Scanner                     | ✅ Complete — AST, build file, version detection                                |
| REST API                    | ✅ Complete — 4 endpoints, persistence, validation                              |
| Persistence                 | ✅ Complete — Panache entities, Flyway V1-V5, pgvector tables                   |
| Frontend                    | ✅ Complete — brutalist UI, scan panel, risk score, finding cards               |
| Tests                       | ✅ Complete — 38 tests, all passing locally and in CI                           |
| CI                          | ✅ Complete — green on GitHub Actions, llms-full.txt generated                  |
| LLM layer                   | ✅ Complete — fix enrichment, ONNX embeddings, Ollama local, Anthropic opt-in   |
| `referenceUrl` on Finding   | ✅ Complete — denormalized at detection, V3 migration, flows to API             |
| FindingCard LLM explanation | ✅ Complete — llmExplanation + llmProvider + llmModel wired into frontend       |
| `LlmModel` sealed type      | ✅ Complete — Ollama/Anthropic records, pattern-matched in ScanMapper           |
| Prompt version anchoring    | ✅ Complete — version facts injected as hard constraints, hallucination fixed   |
| Cloud validation harness    | ✅ Complete — CloudModelValidationIT opt-in, gated by ANTHROPIC_API_KEY         |
| llms.txt                    | ✅ Complete — checked in at repo root                                           |
| History view                | 🔲 Pending — list past scans in UI                                             |
| Finding status PATCH        | 🔲 Pending — accept/reject/defer persistence                                   |
| README                      | 🔲 Pending                                                                     |

---

## decisions log

| Date       | Decision                                             | Rationale                                                   |
|------------|------------------------------------------------------|-------------------------------------------------------------|
| 2026-04-19 | Pure Java stack — no Python                          | Architectural statement; single deployable unit on the JVM  |
| 2026-04-19 | Quarkus over Spring Boot                             | Kubernetes-native, lighter footprint                        |
| 2026-04-19 | pgvector over Qdrant/Weaviate                        | Right scale; operational simplicity wins                    |
| 2026-04-19 | codellama:13b as primary LLM                         | Best local code reasoning on M1 Pro 32GB                    |
| 2026-04-19 | llama3.1:8b for prose                                | Better natural language than codellama                      |
| 2026-04-19 | Co-pilot model — no auto-apply                       | Trust requires transparency; developer always decides       |
| 2026-04-19 | LTS-to-LTS incremental path                          | Nobody migrates 8->25 in one commit                         |
| 2026-04-19 | Target Java 25, not Java 21                          | Java 21 free updates expire September 2026                  |
| 2026-04-19 | Rule engine — code-driven                            | Rules are logic not data; testable, version-controlled      |
| 2026-04-19 | Detection is LLM-free                                | Deterministic; faster and more reliable                     |
| 2026-04-19 | `allow-cloud-fallback: false` default                | Code never leaves machine without consent                   |
| 2026-04-19 | RuleRegistry in anneal-api, not anneal-core          | anneal-core stays pure Java; CDI lives in the Quarkus layer |
| 2026-04-19 | Lombok 1.18.42                                       | 1.18.40+ required for Java 25                               |
| 2026-04-19 | Pinned embedding artifact to 0.36.2                  | Not released at 1.13.0                                      |
| 2026-04-20 | DTOs as Java 25 records                              | No Lombok, no boilerplate                                   |
| 2026-04-20 | ScanMapper as static methods                         | No state, no CDI — pure transformation                      |
| 2026-04-20 | ScanSummaryDto for list view                         | Avoids N+1 loading of findings                              |
| 2026-04-22 | Migrate Maven -> Gradle                              | Dogfooding — anneal recommends Gradle                       |
| 2026-04-22 | Kotlin DSL over Groovy DSL                           | Type-safe, IDE-friendly                                     |
| 2026-04-22 | All Flyway migrations in anneal-store                | Persistence owns schema                                     |
| 2026-04-23 | `referenceUrl` denormalized onto Finding             | Rule in scope at detection time; mapper stays stateless     |
| 2026-04-23 | ANNOTATION pattern implemented                       | Catches javax annotation usage without import statement     |
| 2026-04-23 | `finding_embeddings` separate from `code_embeddings` | Different use cases — per-finding vs file-chunk RAG         |
| 2026-04-23 | `llmExplanation` not persisted                       | Runtime enrichment; history returns null honestly           |
| 2026-04-23 | Drop `quarkus-langchain4j-bom` from root build       | Two enforcedPlatform strictly constraints deadlocks Gradle  |
| 2026-04-23 | Global exclude of `langchain4j-http-client-jdk`      | Conflicts with Quarkiverse JAX-RS HTTP client at runtime    |
| 2026-04-23 | `FixPrompts.clean()` strips `[INST]` artifacts       | codellama:13b bleeds prompt continuation into responses     |
| 2026-04-23 | `LlmConfig` nested interface structure               | Flat keys cause SRCFG00050 config validation errors         |
| 2026-04-24 | Direct `ChatModel.chat()` over AiService proxies     | Per-finding system messages need call-level control         |
| 2026-04-24 | `LlmModel` sealed interface                          | Exhaustive switch in ScanMapper — new provider = compile error |
| 2026-04-24 | `LlmProvider` enum, `llmModel` String on FindingDto  | Provider is closed set; model name is config-driven         |
| 2026-04-24 | Version facts injected as hard constraints in prompt | Prevents codellama:13b version hallucination                |
| 2026-04-24 | `enrichAll()` takes `Map<ruleId, MigrationRule>`     | Rule required to inject introducedIn/removedIn into prompt  |
| 2026-04-24 | `CloudModelValidationIT` gated by env var            | Opt-in QA harness — not a CI gate                           |
| 2026-04-24 | `llms.txt` checked in, `llms-full.txt` gitignored    | Curated vs generated — different ownership models           |

---

## open questions

- Finding status PATCH endpoint — accept/reject/defer persistence
- History view — surface past scans in frontend
- Cloud model validation output — run `CloudModelValidationIT` and evaluate prose quality delta between codellama and claude-sonnet-4-6 on the test-legacy fixture
- README
