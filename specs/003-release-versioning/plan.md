# Implementation Plan: Release Versioning & Build Pipeline

**Branch**: `003-release-versioning` | **Date**: 2026-01-08 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/003-release-versioning/spec.md`

## Summary

Implement semantic versioning (MAJOR.MINOR.PATCH) with automated versionCode calculation, versioned APK naming, CI release workflow triggered by version tags, and secure release signing via GitHub Secrets.

## Technical Context

**Language/Version**: Kotlin 2.1.0, Gradle 8.14.3, Android Gradle Plugin 8.7.3  
**Primary Dependencies**: Android Gradle Plugin (APK naming, signing), GitHub Actions  
**Storage**: N/A  
**Testing**: Manual verification (APK inspection, CI workflow execution)  
**Target Platform**: Android TV (API 29+)  
**Project Type**: Mobile Android  
**Performance Goals**: CI release build < 10 minutes  
**Constraints**: Signing secrets must never be logged or exposed  
**Scale/Scope**: Single APK output, semantic versioning up to 214.74.83 (Int max)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I. Code Quality | ✅ PASS | Build configuration follows Gradle DSL conventions |
| II. Security Baseline | ✅ PASS | Signing secrets via env vars, never committed |
| III. Best Practice | ✅ PASS | Semantic versioning follows industry standard |
| IV. Testing Standards | ⚠️ N/A | Build configuration, verified manually |
| V. UX Consistency | ⚠️ N/A | Developer tooling, not user-facing |
| VI. Performance | ✅ PASS | CI workflow target < 10 minutes |

**Gate Result**: PASS - No violations requiring justification

## Project Structure

### Documentation (this feature)

```text
specs/003-release-versioning/
├── plan.md              # This file
├── research.md          # Phase 0: Gradle signing, APK naming research
├── data-model.md        # Phase 1: Version entity, signing config
├── quickstart.md        # Phase 1: How to bump version, create release
├── contracts/           # Phase 1: N/A (no API contracts)
└── tasks.md             # Phase 2: Implementation tasks
```

### Source Code (repository root)

```text
# Android mobile project structure
gradle.properties              # Version configuration (MAJOR/MINOR/PATCH)
app/
├── build.gradle.kts           # Versioning logic, signing config, APK naming
└── build/outputs/apk/release/ # Generated: atv-{version}-release.apk

.github/workflows/
├── ci.yml                     # Existing CI (build, test, lint)
└── release.yml                # NEW: Release workflow for tagged commits

docs/
└── SIGNING.md                 # NEW: Signing setup documentation
```

**Structure Decision**: Extend existing Android project structure. Version config in gradle.properties (single source of truth). Release workflow as separate GitHub Actions file.

## Phase 0: Research

### Research Tasks

1. **R-01**: Gradle APK naming - How to customize output filename with version
2. **R-02**: Signing configuration - Environment variable approach for CI
3. **R-03**: GitHub Actions - Tag-triggered workflow, secret management
4. **R-04**: versionCode calculation - Formula for semantic version to integer

### Key Findings (Pre-filled from known patterns)

- **APK Naming**: Use `setProperty("archivesBaseName", "atv-$version")` in defaultConfig
- **Signing**: Create signingConfigs block, read from `System.getenv()`, apply conditionally
- **GitHub Secrets**: Base64-encode keystore, decode in workflow, clean up after build
- **versionCode**: Formula `MAJOR * 10000 + MINOR * 100 + PATCH` supports up to 214.74.83

## Phase 1: Design

### Data Model

**Version** (gradle.properties):
```properties
VERSION_MAJOR=1
VERSION_MINOR=0
VERSION_PATCH=0
```

**Computed Values** (build.gradle.kts):
- `versionCode`: Int = MAJOR * 10000 + MINOR * 100 + PATCH
- `versionName`: String = "$MAJOR.$MINOR.$PATCH"
- `archivesBaseName`: String = "atv-$versionName"

**Signing Configuration**:
| Environment Variable | Purpose |
|---------------------|---------|
| KEYSTORE_PATH | Path to .jks file |
| KEYSTORE_PASSWORD | Keystore password |
| KEY_ALIAS | Signing key alias |
| KEY_PASSWORD | Key password |

**GitHub Secrets**:
| Secret | Purpose |
|--------|---------|
| RELEASE_KEYSTORE_BASE64 | Base64-encoded keystore |
| RELEASE_KEYSTORE_PASSWORD | Keystore password |
| RELEASE_KEY_ALIAS | Key alias |
| RELEASE_KEY_PASSWORD | Key password |

### Contracts

N/A - No API contracts for build configuration feature.

## Phase 2: Task Breakdown

See [tasks.md](tasks.md) for detailed implementation tasks.

### Task Summary by User Story

| User Story | Tasks | Files Modified/Created |
|------------|-------|----------------------|
| US1: Versioned APK | T001-T003 | gradle.properties, app/build.gradle.kts |
| US2: Semantic Versioning | T004-T005 | gradle.properties, app/build.gradle.kts |
| US3: CI Release | T006-T009 | .github/workflows/release.yml |
| US4: Release Signing | T010-T013 | app/build.gradle.kts, docs/SIGNING.md |

### Estimated Effort

- **Phase 1 (US1+US2)**: ~1 hour - Versioning and APK naming
- **Phase 2 (US3+US4)**: ~2 hours - CI workflow and signing
- **Total**: ~3 hours

## Complexity Tracking

No violations - standard Android/Gradle patterns used.
