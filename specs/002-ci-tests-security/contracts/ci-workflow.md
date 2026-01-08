# CI Workflow Contract

## GitHub Actions Workflow Schema

This document defines the expected structure and behavior of the CI workflow.

### Workflow Triggers

```yaml
on:
  push:
    branches: [main, develop, 'feature/**']
  pull_request:
    branches: [main, develop]
  schedule:
    - cron: '0 6 * * 1'  # Weekly security scan (Monday 6 AM UTC)
```

### Jobs Contract

#### Job: `build`

**Purpose**: Compile and run unit tests

| Step | Command | Expected Outcome |
|------|---------|------------------|
| Checkout | `actions/checkout@v4` | Repository cloned |
| Setup JDK | `actions/setup-java@v4` | JDK 17 Temurin available |
| Setup Gradle | `gradle/actions/setup-gradle@v4` | Gradle wrapper cached |
| Build | `./gradlew assembleDebug` | APK generated |
| Test | `./gradlew test` | Unit tests pass |
| Coverage | `./gradlew koverHtmlReport` | Report in `app/build/reports/kover/` |

**Artifacts**:
- `test-results`: `app/build/test-results/`
- `coverage-report`: `app/build/reports/kover/`

#### Job: `lint`

**Purpose**: Static analysis

| Step | Command | Expected Outcome |
|------|---------|------------------|
| Detekt | `./gradlew detekt` | Analysis report generated |
| Lint | `./gradlew lint` | Android lint report generated |

**Continue on Error**: `true` (non-blocking)

**Artifacts**:
- `detekt-report`: `app/build/reports/detekt/`
- `lint-report`: `app/build/reports/lint/`

#### Job: `security`

**Purpose**: Dependency vulnerability scan

**Runs on**: `schedule` and `workflow_dispatch` only

| Step | Command | Expected Outcome |
|------|---------|------------------|
| OWASP Check | `./gradlew dependencyCheckAnalyze` | Vulnerability report |

**Failure Threshold**: `failBuildOnCVSS = 9.0` (Critical only)

**Artifacts**:
- `security-report`: `build/reports/dependency-check-report.html`

### Environment Variables

```yaml
env:
  JAVA_VERSION: '17'
  GRADLE_OPTS: '-Dorg.gradle.daemon=false'
```

### Caching Strategy

```yaml
cache:
  paths:
    - ~/.gradle/caches
    - ~/.gradle/wrapper
  key: gradle-${{ hashFiles('**/*.gradle*', '**/gradle-wrapper.properties') }}
```

### Status Checks

| Check | Required | Blocking |
|-------|----------|----------|
| build | ✓ | ✓ |
| lint | ✓ | ✗ |
| security | ✗ | ✓ (CVSS ≥ 9.0) |

### Outputs

```yaml
outputs:
  coverage_percentage:
    description: 'Code coverage percentage'
    value: ${{ steps.coverage.outputs.percentage }}
  critical_vulnerabilities:
    description: 'Number of critical CVEs found'
    value: ${{ steps.security.outputs.critical_count }}
```
