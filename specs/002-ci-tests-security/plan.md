# Implementation Plan: CI/CD Pipeline, Testing & Security Guardrails

**Branch**: `002-ci-tests-security` | **Date**: 2026-01-08 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/002-ci-tests-security/spec.md`

## Summary

Implement automated CI/CD pipeline with GitHub Actions, comprehensive unit test suite covering core business logic (M3U8 parser, repositories, ViewModels), local E2E tests for critical user flows, and security scanning using Dependabot + OWASP dependency-check. Uses Kover for code coverage and Detekt for Kotlin static analysis.

## Technical Context

**Language/Version**: Kotlin 2.1.0, Java 17  
**Primary Dependencies**: JUnit 5.11.3, MockK 1.13.14, Turbine 1.2.0, Kover 0.9.1, Detekt 1.23.7  
**Storage**: Room 2.8.4 (existing), in-memory DB for tests  
**Testing**: JUnit 5 (unit), Compose Testing (E2E local), Kover (coverage)  
**Target Platform**: Android TV API 29+, GitHub Actions ubuntu-latest  
**Project Type**: Android mobile (single module)  
**Performance Goals**: CI pipeline < 15 minutes, unit tests < 2 minutes  
**Constraints**: No emulator in CI (E2E local only), coverage warning-only  
**Scale/Scope**: ~44 Kotlin source files, targeting 80% coverage on data/domain packages

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I. Code Quality | ✅ Pass | Detekt enforces style, lint checks quality |
| II. Security Baseline | ✅ Pass | OWASP scans CVEs, input validation required |
| III. Best Practice Adherence | ✅ Pass | Following standard Android testing patterns |
| IV. Testing Standards | ✅ Aligned | 80% coverage target (aligned with constitution) |
| V. User Experience Consistency | N/A | Developer-facing feature |
| VI. Performance Requirements | ✅ Pass | CI < 15 min meets expectations |

**Constitution Alignment**: Coverage threshold set to 80% per constitution requirements. Warning-only mode allows gradual adoption without blocking builds.

## Project Structure

### Documentation (this feature)

```text
specs/002-ci-tests-security/
├── spec.md              # Feature specification
├── plan.md              # This file
├── research.md          # Phase 0 research output
├── data-model.md        # N/A (no new data entities)
├── quickstart.md        # Developer testing guide
├── contracts/           # N/A (no APIs)
└── tasks.md             # Phase 2 output
```

### Source Code (repository root)

```text
.github/
├── workflows/
│   └── ci.yml           # Main CI pipeline
└── dependabot.yml       # Dependency update config

app/
├── build.gradle.kts     # Updated with test deps, Kover, Detekt
├── src/
│   ├── main/            # Existing source (unchanged)
│   ├── test/            # NEW: Unit tests
│   │   └── kotlin/com/example/atv/
│   │       ├── data/
│   │       │   ├── parser/M3U8ParserTest.kt
│   │       │   └── repository/ChannelRepositoryTest.kt
│   │       ├── domain/
│   │       │   └── usecase/SwitchChannelUseCaseTest.kt
│   │       └── ui/
│   │           ├── playback/PlaybackViewModelTest.kt
│   │           └── settings/SettingsViewModelTest.kt
│   └── androidTest/     # NEW: E2E tests (local only)
│       └── kotlin/com/example/atv/
│           ├── PlaylistLoadingTest.kt
│           ├── ChannelNavigationTest.kt
│           └── SettingsFlowTest.kt

config/
├── detekt.yml           # Detekt configuration
└── lint.xml             # Android Lint configuration

gradle/
└── libs.versions.toml   # Updated with new dependencies
```

**Structure Decision**: Single Android module structure maintained. Tests added in standard `src/test` and `src/androidTest` directories. Config files at project root.

## Phases Overview

### Phase 0: Research ✅ Complete
- See [research.md](research.md) for technology decisions
- Key choices: GitHub Actions, JUnit 5, Kover, Detekt, OWASP

### Phase 1: Design & Configuration

**Deliverables**:
1. Updated `libs.versions.toml` with test dependencies
2. Updated `app/build.gradle.kts` with plugins and test configs
3. `detekt.yml` configuration file
4. `lint.xml` configuration file
5. `quickstart.md` - developer testing guide

### Phase 2: Implementation Tasks

**Task Groups**:
1. **CI Pipeline** (FR-001 to FR-007)
   - GitHub Actions workflow file
   - Dependabot configuration
   - OWASP dependency-check integration

2. **Unit Tests** (FR-008 to FR-013)
   - M3U8Parser tests
   - ChannelRepository tests
   - ViewModel tests
   - UseCase tests

3. **E2E Tests** (FR-014 to FR-016)
   - Playlist loading test
   - Channel navigation test
   - Settings flow test

4. **Security Validation** (FR-017 to FR-021)
   - URL validation implementation
   - Security lint rules
   - Secrets audit

## Dependencies & Prerequisites

| Dependency | Type | Status |
|------------|------|--------|
| GitHub repository | External | ✅ Exists |
| JUnit 5 | Library | ✅ In version catalog |
| MockK | Library | ✅ In version catalog |
| Turbine | Library | ✅ In version catalog |
| Kover | Plugin | 🔄 To add |
| Detekt | Plugin | 🔄 To add |
| OWASP dep-check | Plugin | 🔄 To add |
| kotlinx-coroutines-test | Library | 🔄 To add |

## Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| OWASP scan slow in CI | Medium | Low | Cache NVD database, run weekly not per-push |
| Flaky Compose tests | Medium | Medium | Keep E2E local-only, focus on unit tests |
| Coverage hard to reach | Low | Low | Advisory warning, not blocking |
| Gradle cache misses | Low | Medium | Use setup-gradle action with good defaults |

## Success Metrics

| Metric | Target | Measurement |
|--------|--------|-------------|
| CI pipeline time | < 15 min | GitHub Actions run time |
| Unit test coverage | ≥ 80% | Kover report on data/domain |
| Security vulnerabilities | 0 Critical | OWASP report |
| Build success rate | > 95% | GitHub Actions history |
---

## Post-Design Constitution Check

### Re-evaluation After Phase 1

| Principle | Status | Justification |
|-----------|--------|---------------|
| I. Code Quality | ✅ SUPPORTED | Detekt enforces cyclomatic complexity ≤10, naming conventions, DRY detection |
| II. Security Baseline | ✅ SUPPORTED | OWASP scans for CVEs, URL scheme validation blocks file:/javascript:, Dependabot monitors dependencies |
| III. Best Practice Adherence | ✅ SUPPORTED | Lint rules enforce SOLID indicators, error handling patterns documented in test contracts |
| IV. Testing Standards | ✅ ALIGNED | 80% coverage target per constitution requirement |
| V. UX Consistency | ✅ N/A | Infrastructure feature, no UI changes |
| VI. Performance | ✅ SUPPORTED | CI time target <15min, async security scans, caching strategy |

**Note**: Coverage threshold is 80% per constitution. Warning-only mode (non-blocking) approved by user to allow gradual adoption on existing codebase with ~0% coverage.

### Quality Gates Alignment

| Constitution Gate | Implementation |
|-------------------|----------------|
| Static Analysis | ✅ Detekt + Android Lint (warn) |
| Security Scan | ✅ OWASP + Dependabot (Critical blocks) |
| Test Suite | ✅ Unit tests (must pass) |
| Code Review | ✅ Branch protection (separate config) |
| Performance Check | ✅ CI time monitoring |

---

## Artifacts Generated

| Artifact | Path | Status |
|----------|------|--------|
| Research | `specs/002-ci-tests-security/research.md` | ✅ Complete |
| Data Model | `specs/002-ci-tests-security/data-model.md` | ✅ Complete |
| CI Workflow Contract | `specs/002-ci-tests-security/contracts/ci-workflow.md` | ✅ Complete |
| Test Contracts | `specs/002-ci-tests-security/contracts/test-contracts.md` | ✅ Complete |
| Quickstart | `specs/002-ci-tests-security/quickstart.md` | ✅ Complete |
| Agent Context | `.github/agents/copilot-instructions.md` | ✅ Updated |