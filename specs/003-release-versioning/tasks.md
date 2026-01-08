# Tasks: Release Versioning & Build Pipeline

**Input**: Design documents from `/specs/003-release-versioning/`  
**Prerequisites**: plan.md ✅, spec.md ✅, research.md ✅, data-model.md ✅, quickstart.md ✅

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1-US4)
- All file paths are relative to repository root

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Version configuration foundation

- [ ] T001 Add VERSION_MAJOR, VERSION_MINOR, VERSION_PATCH properties to gradle.properties
- [ ] T002 [P] Add version computation logic to app/build.gradle.kts (read from properties, compute versionCode/versionName)

**Checkpoint**: `./gradlew properties | grep version` shows computed values

---

## Phase 2: User Story 1 - Generate Versioned Release APK (Priority: P1) 🎯 MVP

**Goal**: Release APK filename includes version number

- [ ] T003 [US1] Configure archivesBaseName in app/build.gradle.kts defaultConfig
- [ ] T004 [US1] Verify APK output naming with `./gradlew assembleRelease`

**Checkpoint**: APK generated at `app/build/outputs/apk/release/atv-1.0.0-release-unsigned.apk`

---

## Phase 3: User Story 2 - Semantic Version Management (Priority: P1)

**Goal**: versionCode computed from semantic version components

- [ ] T005 [US2] Implement versionCode formula: MAJOR * 10000 + MINOR * 100 + PATCH
- [ ] T006 [US2] Test version bump by changing gradle.properties and rebuilding

**Checkpoint**: Changing VERSION_MINOR from 0 to 1 produces versionCode 10100

---

## Phase 4: User Story 4 - Release Signing Configuration (Priority: P2)

**Goal**: Signing via environment variables

- [ ] T007 [US4] Add signingConfigs block to app/build.gradle.kts reading from System.getenv()
- [ ] T008 [US4] Conditionally apply signing config to release buildType
- [ ] T009 [US4] Test unsigned build works when env vars not set
- [ ] T010 [P] [US4] Create docs/SIGNING.md with keystore generation and setup instructions

**Checkpoint**: Build succeeds unsigned locally, signing works with env vars set

---

## Phase 5: User Story 3 - CI Release Build Artifacts (Priority: P2)

**Goal**: Automated release builds on version tags

- [ ] T011 [US3] Create .github/workflows/release.yml with v* tag trigger
- [ ] T012 [US3] Add keystore decode step (from RELEASE_KEYSTORE_BASE64 secret)
- [ ] T013 [US3] Add signing environment variable setup step
- [ ] T014 [US3] Add assembleRelease build step
- [ ] T015 [US3] Add artifact upload step for APK
- [ ] T016 [US3] Add keystore cleanup step (always runs)
- [ ] T017 [P] [US3] Add GitHub Release creation job (triggered by successful build)

**Checkpoint**: Push v1.0.0 tag, CI builds and uploads signed APK artifact

---

## Phase 6: Polish & Documentation

**Purpose**: Finalize and verify

- [ ] T018 [P] Update README.md with release workflow instructions
- [ ] T019 Verify all acceptance scenarios from spec.md pass
- [ ] T020 Test full release flow: bump version → tag → CI → artifact

**Checkpoint**: Complete release workflow functional end-to-end

---

## Dependencies & Execution Order

```
Phase 1 (Setup: T001-T002)
    │
    ▼
Phase 2 (US1: T003-T004) ──► Phase 3 (US2: T005-T006)
                                    │
                                    ▼
                            Phase 4 (US4: T007-T010)
                                    │
                                    ▼
                            Phase 5 (US3: T011-T017)
                                    │
                                    ▼
                            Phase 6 (Polish: T018-T020)
```

## Task Summary

| Phase | Tasks | Effort | Dependencies |
|-------|-------|--------|--------------|
| 1. Setup | T001-T002 | 15 min | None |
| 2. US1 (APK naming) | T003-T004 | 15 min | Phase 1 |
| 3. US2 (Versioning) | T005-T006 | 15 min | Phase 2 |
| 4. US4 (Signing) | T007-T010 | 30 min | Phase 3 |
| 5. US3 (CI Release) | T011-T017 | 45 min | Phase 4 |
| 6. Polish | T018-T020 | 15 min | Phase 5 |
| **Total** | **20 tasks** | **~2.5 hours** | |
