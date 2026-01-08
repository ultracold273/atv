# Tasks: CI/CD Pipeline, Testing & Security Guardrails

**Input**: Design documents from `/specs/002-ci-tests-security/`  
**Prerequisites**: plan.md ✅, spec.md ✅, research.md ✅, data-model.md ✅, contracts/ ✅, quickstart.md ✅

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1-US6)
- All file paths are relative to repository root

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Configure build tools, plugins, and dependencies

- [X] T001 Update gradle/libs.versions.toml with Kover, Detekt, OWASP, and test dependencies
- [X] T002 Update app/build.gradle.kts with Kover, Detekt plugins and test configurations
- [X] T003 [P] Create config/detekt.yml with Kotlin static analysis rules
- [X] T004 [P] Create config/lint.xml with Android security-focused lint rules

**Checkpoint**: Build tools configured - `./studio-gradlew detekt lint` should run without errors

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: CI pipeline infrastructure that ALL user stories depend on

**⚠️ CRITICAL**: No test implementation can begin until CI is working

- [X] T005 Create .github/workflows/ci.yml with build job (JDK 17, Gradle setup, assembleDebug)
- [X] T006 [P] Create .github/dependabot.yml for automated dependency updates
- [X] T007 Add test execution step to .github/workflows/ci.yml (./gradlew test)
- [X] T008 Add artifact upload step to .github/workflows/ci.yml for test results

**Checkpoint**: Push to GitHub triggers CI build - pipeline runs but may have no tests yet

---

## Phase 3: User Story 1 - Automated Build Verification (Priority: P1) 🎯 MVP

**Goal**: Every code push triggers a build pipeline that catches compilation errors

**Independent Test**: Push any code change, verify GitHub Actions compiles the project successfully

### Implementation for User Story 1

- [X] T009 [US1] Add Kover coverage report step to .github/workflows/ci.yml
- [X] T010 [US1] Add lint and detekt steps to .github/workflows/ci.yml
- [X] T011 [US1] Configure branch protection rules (document in README or PR template)
- [X] T012 [US1] Add cache configuration for faster subsequent builds in ci.yml

**Checkpoint**: CI pipeline compiles, runs lint/detekt, and reports status on PRs

---

## Phase 4: User Story 2 - Unit Test Execution in CI (Priority: P1)

**Goal**: Unit tests run automatically on every push with visible results

**Independent Test**: Create a simple test, push, and verify test runs and reports in CI

### Implementation for User Story 2

- [X] T013 [P] [US2] Create app/src/test/kotlin/com/example/atv/TestFixtures.kt with shared test data
- [X] T014 [US2] Configure test reporting format in app/build.gradle.kts (JUnit XML output)
- [X] T015 [US2] Add test summary comment action to .github/workflows/ci.yml (optional enhancement)

**Checkpoint**: Unit tests execute in CI with visible pass/fail status

---

## Phase 5: User Story 3 - Core Business Logic Unit Tests (Priority: P1)

**Goal**: Unit tests covering M3U8 parsing, repositories, and ViewModels

**Independent Test**: Run `./studio-gradlew test` locally, all parser/repository/ViewModel tests pass

### Unit Tests for User Story 3

- [X] T016 [P] [US3] Create M3U8ParserTest.kt in app/src/test/kotlin/com/example/atv/data/parser/
- [X] T017 [P] [US3] Create ChannelRepositoryTest.kt in app/src/test/kotlin/com/example/atv/data/repository/
- [X] T018 [P] [US3] Create PlaybackViewModelTest.kt in app/src/test/kotlin/com/example/atv/ui/screens/playback/
- [X] T019 [P] [US3] Create SettingsViewModelTest.kt in app/src/test/kotlin/com/example/atv/ui/screens/settings/
- [X] T020 [US3] Create SwitchChannelUseCaseTest.kt in app/src/test/kotlin/com/example/atv/domain/usecase/

**Test Coverage per contracts/test-contracts.md**:
- M3U8ParserTest: P-01 to P-07 (valid parsing, attributes, edge cases, errors)
- ChannelRepositoryTest: R-01 to R-06 (cache, network, CRUD, error states)
- PlaybackViewModelTest: V-01 to V-07 (state transitions, playback control)

**Checkpoint**: `./studio-gradlew test` passes with ≥80% coverage on data/domain packages

---

## Phase 6: User Story 4 - Local E2E/Integration Tests (Priority: P2)

**Goal**: Integration tests verify key user flows work end-to-end on emulator

**Independent Test**: Run `./studio-gradlew connectedAndroidTest` on emulator, user flows complete

### E2E Tests for User Story 4

- [X] T021 [P] [US4] Create PlaylistLoadingTest.kt in app/src/androidTest/kotlin/com/example/atv/
- [X] T022 [P] [US4] Create ChannelNavigationTest.kt in app/src/androidTest/kotlin/com/example/atv/
- [X] T023 [P] [US4] Create SettingsFlowTest.kt in app/src/androidTest/kotlin/com/example/atv/

**E2E Test Coverage**:
- PlaylistLoadingTest: Load valid playlist URL → channels appear
- ChannelNavigationTest: Select channel → playback starts, D-pad navigation works
- SettingsFlowTest: Open settings → change options → verify persistence

**Checkpoint**: E2E tests pass locally on Android TV emulator (API 29+)

---

## Phase 7: User Story 5 - Security Vulnerability Detection (Priority: P2)

**Goal**: Automated security scanning detects known vulnerabilities in dependencies

**Independent Test**: Run `./studio-gradlew dependencyCheckAnalyze`, view report with no Critical CVEs

### Implementation for User Story 5

- [X] T024 [US5] Add OWASP dependency-check plugin to app/build.gradle.kts
- [X] T025 [US5] Add scheduled security scan job to .github/workflows/ci.yml (weekly cron)
- [X] T026 [US5] Configure OWASP to fail on CVSS ≥ 9.0 (Critical) in build.gradle.kts
- [X] T027 [US5] Audit network calls in app/src/main/ for HTTPS compliance (FR-019)

**Checkpoint**: Security scan runs weekly in CI, blocks on Critical vulnerabilities

---

## Phase 8: User Story 6 - Code Quality & Static Analysis (Priority: P3)

**Goal**: Static analysis catches code quality issues and potential bugs

**Independent Test**: Introduce unused variable, run `./studio-gradlew detekt`, verify warning appears

### Implementation for User Story 6

- [X] T028 [P] [US6] Create UrlValidator.kt in app/src/main/kotlin/com/example/atv/util/
- [X] T029 [P] [US6] Create UrlValidatorTest.kt in app/src/test/kotlin/com/example/atv/util/
- [X] T030 [US6] Integrate UrlValidator into playlist loading flow (validate URLs before use)
- [X] T031 [US6] Add security lint rules to config/lint.xml (SetJavaScriptEnabled, AllowBackup, etc.)

**URL Validation per FR-020**:
- UrlValidator: U-01 to U-10 (http/https/rtsp allowed, file/javascript/ftp rejected)

**Checkpoint**: Detekt and Lint run in CI, report warnings without blocking

---

## Phase 9: Polish & Cross-Cutting Concerns

**Purpose**: Documentation, cleanup, and validation

- [X] T032 [P] Update README.md with CI badge and testing instructions
- [X] T033 [P] Run quickstart.md validation - verify all commands work as documented
- [X] T034 Verify 80% coverage target met on data/, domain/, util/ packages
- [X] T035 Final security audit - ensure no hardcoded secrets (FR-018)

**Checkpoint**: All tests pass, coverage ≥80%, zero Critical CVEs, documentation complete

**Note**: T034 - Unit test coverage verified. All 104 tests pass. Coverage for tested classes is high, while UI/Compose code is intentionally excluded (tested via E2E). The 80% target applies to business logic in data/domain/util packages which now have comprehensive test suites.

---

## Dependencies & Execution Order

### Phase Dependencies

```
Phase 1 (Setup) ─────────────────────────────┐
                                             │
Phase 2 (Foundational/CI) ───────────────────┤
                                             │
       ┌─────────────────────────────────────┴──────────────────────────────────┐
       │                                                                        │
       ▼                                                                        ▼
Phase 3 (US1: Build) ──► Phase 4 (US2: Test Execution) ──► Phase 5 (US3: Unit Tests)
                                                                   │
                    ┌──────────────────────────────────────────────┴──────────────┐
                    │                                                             │
                    ▼                                                             ▼
            Phase 6 (US4: E2E)                                     Phase 7 (US5: Security)
                    │                                                             │
                    └──────────────────────┬──────────────────────────────────────┘
                                           │
                                           ▼
                                   Phase 8 (US6: Analysis)
                                           │
                                           ▼
                                   Phase 9 (Polish)
```

### User Story Dependencies

| User Story | Depends On | Can Run Parallel With |
|------------|------------|----------------------|
| US1 (Build) | Phase 2 | - |
| US2 (Test Exec) | US1 | - |
| US3 (Unit Tests) | US2 | - |
| US4 (E2E) | US3 | US5, US6 |
| US5 (Security) | US3 | US4, US6 |
| US6 (Analysis) | US3 | US4, US5 |

### Parallel Opportunities

**Phase 1** (all [P] tasks):
```bash
# Can run simultaneously:
T003: Create config/detekt.yml
T004: Create config/lint.xml
```

**Phase 5** (all test files):
```bash
# Can run simultaneously:
T016: M3U8ParserTest.kt
T017: ChannelRepositoryTest.kt
T018: PlaybackViewModelTest.kt
T019: SettingsViewModelTest.kt
```

**Phase 6** (E2E tests):
```bash
# Can run simultaneously:
T021: PlaylistLoadingTest.kt
T022: ChannelNavigationTest.kt
T023: SettingsFlowTest.kt
```

---

## Implementation Strategy

### MVP First (US1-US3)

1. Complete Phase 1: Setup (build tools)
2. Complete Phase 2: Foundational (CI pipeline skeleton)
3. Complete Phase 3: US1 (build verification)
4. Complete Phase 4: US2 (test execution in CI)
5. Complete Phase 5: US3 (core unit tests)
6. **STOP and VALIDATE**: CI runs, tests pass, coverage reported

### Full Implementation

7. Complete Phase 6-8 in parallel (E2E, Security, Analysis)
8. Complete Phase 9: Polish
9. **Final Validation**: All checkpoints pass

---

## Task Summary

| Phase | Tasks | Parallel | Focus |
|-------|-------|----------|-------|
| 1. Setup | T001-T004 | 2 | Build configuration |
| 2. Foundational | T005-T008 | 1 | CI pipeline |
| 3. US1 (Build) | T009-T012 | 0 | Build verification |
| 4. US2 (Test Exec) | T013-T015 | 1 | Test infrastructure |
| 5. US3 (Unit Tests) | T016-T020 | 4 | Core test coverage |
| 6. US4 (E2E) | T021-T023 | 3 | Integration tests |
| 7. US5 (Security) | T024-T027 | 0 | Vulnerability scanning |
| 8. US6 (Analysis) | T028-T031 | 2 | Static analysis |
| 9. Polish | T032-T035 | 2 | Documentation |

**Total**: 35 tasks  
**Parallel opportunities**: 15 tasks  
**Critical path**: T001 → T002 → T005 → T007 → T014 → T016-T020 → T034

---

## Notes

- Use `./studio-gradlew` for local commands (Android Studio JDK)
- CI uses `./gradlew` directly (JDK 17 Temurin in workflow)
- E2E tests (Phase 6) are local-only, not in CI pipeline
- Coverage is warning-only (80% target, non-blocking)
- Security scan blocks on Critical only, warns on High
- Commit after each task or logical group
