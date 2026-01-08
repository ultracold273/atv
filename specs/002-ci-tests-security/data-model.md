# Data Model

**Feature**: 002-ci-tests-security  
**Last Updated**: 2026-01-08

## Overview

This feature is primarily **infrastructure/tooling** rather than data-driven. It doesn't introduce new runtime entities but defines configuration schemas and test contracts.

---

## Configuration Entities

### 1. CI Workflow Configuration

**File**: `.github/workflows/ci.yml`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| name | string | ✓ | Workflow display name |
| on | object | ✓ | Trigger events (push, pull_request) |
| jobs | object | ✓ | Job definitions |
| env | object | | Environment variables |

**Jobs Structure**:
- `build`: Compile and unit test
- `lint`: Static analysis (Detekt + Android Lint)
- `security`: OWASP dependency check (scheduled)

### 2. Dependabot Configuration

**File**: `.github/dependabot.yml`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| version | number | ✓ | Schema version (2) |
| updates | array | ✓ | Update configurations |
| updates[].package-ecosystem | string | ✓ | "gradle" |
| updates[].directory | string | ✓ | "/" |
| updates[].schedule.interval | string | ✓ | "weekly" |
| updates[].open-pull-requests-limit | number | | Default: 5 |

### 3. Detekt Configuration

**File**: `config/detekt.yml`

| Section | Purpose |
|---------|---------|
| build | Exit codes and report paths |
| style | Code style rules |
| complexity | Cyclomatic complexity thresholds |
| potential-bugs | Bug detection rules |
| performance | Performance anti-patterns |
| exceptions | Exception handling rules |

### 4. Coverage Configuration (Kover)

**Embedded in**: `app/build.gradle.kts`

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| includes | list | all | Packages to include |
| excludes | list | none | Packages to exclude |
| minBound | int | 60 | Warning threshold % |
| onCheck | boolean | false | Fail build on violation |

---

## Test Contracts

### Unit Test Files

Each test file follows naming convention: `{ClassName}Test.kt`

| Test File | SUT | Key Behaviors |
|-----------|-----|---------------|
| `M3U8ParserTest.kt` | M3U8Parser | Parse valid/invalid M3U8, extract attributes |
| `ChannelRepositoryTest.kt` | ChannelRepository | CRUD operations, caching |
| `PlaybackViewModelTest.kt` | PlaybackViewModel | State transitions, error handling |
| `UrlValidatorTest.kt` | (new) UrlValidator | Scheme validation (http/https/rtsp) |

### Test Assertions Schema

```kotlin
// Standard test structure
data class TestCase(
    val name: String,           // Test description
    val given: Any,             // Input/setup
    val `when`: () -> Unit,     // Action
    val then: (Any) -> Boolean  // Assertion
)
```

---

## Security Entities

### Vulnerability Report Schema

**File**: `build/reports/dependency-check-report.json`

| Field | Type | Description |
|-------|------|-------------|
| dependencies | array | Scanned dependencies |
| dependencies[].vulnerabilities | array | CVEs found |
| vulnerabilities[].severity | string | CRITICAL, HIGH, MEDIUM, LOW |
| vulnerabilities[].cvssScore | float | 0.0 - 10.0 |
| vulnerabilities[].cve | string | CVE identifier |

### Blocking Criteria

| Severity | CVSS Range | Action |
|----------|------------|--------|
| CRITICAL | 9.0 - 10.0 | Block merge |
| HIGH | 7.0 - 8.9 | Warning |
| MEDIUM | 4.0 - 6.9 | Log only |
| LOW | 0.1 - 3.9 | Log only |

---

## State Transitions

### CI Pipeline States

```
[idle] → [triggered] → [building] → [testing] → [analyzing] → [pass/fail]
                                                      ↓
                                               [security_scan] (weekly)
```

### Test Execution States

```
[not_run] → [running] → [passed]
                ↓
            [failed] → [reported]
```

---

## Relationships

```
┌─────────────────┐
│   ci.yml        │ orchestrates
├─────────────────┤────────────────┐
│ triggers tests  │                │
└────────┬────────┘                │
         │                         ▼
┌────────▼────────┐      ┌─────────────────┐
│  Unit Tests     │      │  Static Analysis │
│ (JUnit/MockK)   │      │ (Detekt/Lint)    │
└────────┬────────┘      └────────┬────────┘
         │                        │
         ▼                        ▼
┌─────────────────────────────────────────┐
│          Coverage Report (Kover)         │
└─────────────────────────────────────────┘
```

---

## Validation Rules

### URL Validation (FR-16)

```kotlin
val ALLOWED_SCHEMES = setOf("http", "https", "rtsp")

fun isValidUrl(url: String): Boolean {
    val scheme = URI(url).scheme?.lowercase()
    return scheme in ALLOWED_SCHEMES
}
```

### Test Naming Convention

```kotlin
// Pattern: should_expectedBehavior_when_condition
// Or: `should expected behavior when condition`

// Examples:
fun `should parse valid m3u8 when extended format`() { }
fun `should throw exception when url scheme invalid`() { }
```
