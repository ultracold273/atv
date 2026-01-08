# Research: CI/CD, Testing & Security for Android TV App

**Feature**: 002-ci-tests-security  
**Date**: 2026-01-08  
**Status**: Complete

## Executive Summary

This document captures research findings for implementing CI/CD, unit testing, and security scanning for the ATV Android TV IPTV player project. The tech stack leverages existing project dependencies where possible and adds industry-standard tools for testing and analysis.

---

## 1. GitHub Actions for Android CI/CD

### Decision: Single Workflow with Gradle Setup Action

**Rationale**: The `gradle/actions/setup-gradle@v4` action provides intelligent caching out-of-the-box, handles Gradle wrapper validation, and integrates with GitHub's dependency graph.

**Configuration**:
- **JDK**: Eclipse Temurin JDK 17 (required for AGP 8.7.3+)
- **Runner**: `ubuntu-latest` (sufficient for build/test; no emulator needed in CI)
- **Caching**: Automatic via setup-gradle action (caches ~/.gradle/caches, build outputs)

**Key Actions**:
| Action | Version | Purpose |
|--------|---------|---------|
| actions/checkout | v4 | Clone repository |
| actions/setup-java | v4 | Install JDK 17 Temurin |
| gradle/actions/setup-gradle | v4 | Setup Gradle with caching |
| actions/upload-artifact | v4 | Upload test reports |

**Workflow Triggers**:
- push to all branches
- pull_request to main/develop branches

**Alternatives Considered**:
- **Self-hosted runners**: Overkill for this project size, adds maintenance burden
- **Matrix builds**: Not needed since we only build debug variant in CI

---

## 2. Unit Testing Framework

### Decision: JUnit 5 + MockK + Turbine (Already Configured)

The project already has these dependencies in libs.versions.toml:
- junit5 = "5.11.3" - Modern test framework with better Kotlin support
- mockk = "1.13.14" - Kotlin-native mocking, handles final classes/coroutines
- turbine = "1.2.0" - Flow testing with elegant test { } API

**Additional Dependencies Needed**:
| Library | Version | Purpose |
|---------|---------|---------|
| kotlinx-coroutines-test | 1.10.1 | runTest, TestDispatcher, advanceUntilIdle() |
| room-testing | 2.8.4 | In-memory database for repository tests |
| hilt-android-testing | 2.54 | Test injection support |

**Test Structure**:
```
app/src/test/kotlin/com/example/atv/
├── data/
│   ├── parser/M3U8ParserTest.kt
│   └── repository/ChannelRepositoryTest.kt
├── domain/
│   └── usecase/SwitchChannelUseCaseTest.kt
└── ui/
    ├── playback/PlaybackViewModelTest.kt
    └── settings/SettingsViewModelTest.kt
```

**Alternatives Considered**:
- **JUnit 4**: Legacy, lacks nested tests, worse Kotlin integration
- **Mockito-Kotlin**: Requires open classes or plugins; MockK handles Kotlin better

---

## 3. Code Coverage

### Decision: Kover (Kotlin-native Coverage Tool)

**Rationale**: Kover is developed by JetBrains specifically for Kotlin. It correctly measures inline functions and handles Kotlin-specific constructs better than JaCoCo.

**Configuration**:
- **Plugin**: org.jetbrains.kotlinx.kover version 0.9.1
- **Report Formats**: HTML (local viewing), XML (CI upload)
- **Threshold**: 80% warning (aligned with constitution)

**Exclusions** (generated code):
- Hilt: *_Factory*, *_HiltModules*, *Hilt_*
- Room: *_Impl*, *_Dao_Impl*
- Compose: *ComposableSingletons*
- BuildConfig, Manifest

**Alternatives Considered**:
- **JaCoCo**: Industry standard but struggles with Kotlin inline functions
- **Codecov/Coveralls**: Can be added later for PR comments; not MVP

---

## 4. Static Analysis

### Decision: Android Lint + Detekt

**Android Lint**:
- Built-in, no additional dependency
- Configure via lint.xml at project root
- Focus on security checks: SetJavaScriptEnabled, HardcodedDebugMode, AllowBackup

**Detekt**:
- **Plugin**: io.gitlab.arturbosch.detekt version 1.23.7
- **Config**: detekt.yml with custom rules
- **Severity**: Warning only (non-blocking per spec clarification)

**Key Detekt Rules**:
- complexity - Cyclomatic complexity, long methods
- style - Naming conventions, magic numbers
- potential-bugs - Null safety, unreachable code
- performance - Inefficient patterns

**Alternatives Considered**:
- **ktlint**: Formatting-only; Detekt includes formatting + more
- **SonarQube**: Overkill for project size, requires server setup

---

## 5. Security Scanning

### Decision: Dependabot + OWASP Dependency-Check

**Dependabot** (Primary):
- Native GitHub integration, zero configuration
- Automatic PRs for vulnerable dependencies
- Covers: npm, Maven/Gradle, GitHub Actions

**OWASP Dependency-Check** (Secondary - in CI):
- **Plugin**: org.owasp.dependencycheck version 12.0.1
- Scans against NVD (National Vulnerability Database)
- Fail on Critical severity (per spec), warn on High

**Configuration**:
- .github/dependabot.yml for automatic updates
- Gradle plugin for CI scanning with threshold configuration

**Alternatives Considered**:
- **Snyk**: Excellent but requires account/API key
- **GitHub Advanced Security**: Paid feature for private repos
- **Trivy**: Better for container scanning, less Android-focused

---

## 6. E2E/Instrumented Testing (Local Only)

### Decision: Compose Testing Framework

**Rationale**: Native support for Compose UI, works with Compose for TV, integrates with JUnit 4 test runner required by Android instrumentation.

**Dependencies**:
| Library | Purpose |
|---------|---------|
| compose-ui-test-junit4 | Compose test rules and assertions |
| compose-ui-test-manifest | Debug manifest for testing |
| androidx-test-runner | Android test orchestration |
| androidx-test-rules | Activity scenarios |

**TV-Specific Testing**:
- D-pad navigation: performKeyInput { pressKey(Key.DirectionDown) }
- Focus testing: assertIsFocused(), assertIsNotFocused()
- Long-press: performKeyInput { pressKey(Key.DpadCenter, 1000) }

**Test Structure**:
```
app/src/androidTest/kotlin/com/example/atv/
├── PlaylistLoadingTest.kt
├── ChannelNavigationTest.kt
└── SettingsFlowTest.kt
```

---

## 7. Summary: Dependencies to Add

### Versions (libs.versions.toml)
```toml
# Testing - additional
coroutinesTest = "1.10.1"
androidxTestRunner = "1.6.2"
androidxTestRules = "1.6.1"

# Static Analysis
detekt = "1.23.7"
kover = "0.9.1"

# Security
owaspDependencyCheck = "12.0.1"
```

### Libraries (libs.versions.toml)
```toml
# Testing
coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutinesTest" }
room-testing = { module = "androidx.room:room-testing", version.ref = "room" }
hilt-android-testing = { module = "com.google.dagger:hilt-android-testing", version.ref = "hilt" }

# Android Test
androidx-test-runner = { module = "androidx.test:runner", version.ref = "androidxTestRunner" }
androidx-test-rules = { module = "androidx.test:rules", version.ref = "androidxTestRules" }
compose-ui-test-junit4 = { module = "androidx.compose.ui:ui-test-junit4" }
compose-ui-test-manifest = { module = "androidx.compose.ui:ui-test-manifest" }
```

### Plugins (libs.versions.toml)
```toml
detekt = { id = "io.gitlab.arturbosch.detekt", version.ref = "detekt" }
kover = { id = "org.jetbrains.kotlinx.kover", version.ref = "kover" }
owasp-dependencycheck = { id = "org.owasp.dependencycheck", version.ref = "owaspDependencyCheck" }
```

---

## 8. Key Decisions Summary

| Area | Decision | Rationale |
|------|----------|-----------|
| CI Platform | GitHub Actions | Native integration, free tier sufficient |
| Test Framework | JUnit 5 + MockK | Already configured, Kotlin-native |
| Coverage | Kover | Kotlin-optimized, JetBrains maintained |
| Kotlin Analysis | Detekt | Comprehensive, configurable |
| Security Scan | Dependabot + OWASP | Zero-config + deep scanning |
| E2E Framework | Compose Testing | Native Compose support |
