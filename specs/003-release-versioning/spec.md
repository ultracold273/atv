# Feature Specification: Release Versioning & Build Pipeline

**Feature Branch**: `003-release-versioning`  
**Created**: 2026-01-08  
**Status**: Draft  
**Input**: User description: "Implement versioning strategy and release build pipeline with semantic versioning and proper APK naming"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Generate Versioned Release APK (Priority: P1) 🎯 MVP

As a developer, I want the build system to automatically generate a properly versioned release APK so that I can distribute builds with clear version identification.

**Why this priority**: This is the core functionality - without versioned release builds, there's no way to track what version users are running or manage releases professionally.

**Independent Test**: Run `./gradlew assembleRelease`, verify APK is generated with version name in filename (e.g., `atv-1.0.0-release.apk`)

**Acceptance Scenarios**:

1. **Given** the project is configured with semantic versioning, **When** `./gradlew assembleRelease` is executed, **Then** a release APK is generated at `app/build/outputs/apk/release/` with filename pattern `atv-{versionName}-release.apk`
2. **Given** a release build is generated, **When** checking the APK properties, **Then** the versionName and versionCode are correctly embedded in the APK manifest
3. **Given** the version is defined in a single source of truth, **When** building the project, **Then** both versionCode and versionName are derived from that configuration

---

### User Story 2 - Semantic Version Management (Priority: P1)

As a developer, I want to manage version numbers using semantic versioning (MAJOR.MINOR.PATCH) so that version progression is clear and follows industry standards.

**Why this priority**: Semantic versioning is essential for communicating changes to users and maintaining compatibility expectations.

**Independent Test**: Update version configuration, rebuild, verify new version appears in APK filename and manifest

**Acceptance Scenarios**:

1. **Given** version is set to "1.2.3", **When** building the project, **Then** versionName is "1.2.3" and versionCode is calculated automatically
2. **Given** MAJOR=1, MINOR=2, PATCH=3, **When** versionCode is computed, **Then** versionCode follows a consistent formula (e.g., MAJOR*10000 + MINOR*100 + PATCH = 10203)
3. **Given** version components are defined separately, **When** viewing build configuration, **Then** version is easy to update by changing individual components

---

### User Story 3 - CI Release Build Artifacts (Priority: P2)

As a developer, I want CI to automatically build and archive release APKs for tagged commits so that releases are reproducible and available for download.

**Why this priority**: Automated release builds ensure consistency and eliminate manual build errors during releases.

**Independent Test**: Push a version tag (e.g., `v1.0.0`), verify CI builds and uploads release APK artifact

**Acceptance Scenarios**:

1. **Given** a commit is tagged with `v*` pattern (e.g., `v1.0.0`), **When** CI pipeline runs, **Then** release APK is built and uploaded as GitHub artifact
2. **Given** CI builds a release, **When** viewing workflow artifacts, **Then** the APK filename includes the version number from the tag
3. **Given** a release workflow succeeds, **When** checking build outputs, **Then** APK is signed (or ready for signing) for distribution

---

### User Story 4 - Release Signing Configuration (Priority: P2)

As a developer, I want release builds to be configured for signing so that APKs can be distributed through official channels.

**Why this priority**: Signed APKs are required for Google Play and sideloading on devices with signature verification.

**Independent Test**: Configure signing, build release, verify APK is signed with specified keystore

**Acceptance Scenarios**:

1. **Given** signing configuration exists in Gradle, **When** building release, **Then** APK is signed using the configured keystore
2. **Given** signing credentials are stored as environment variables/secrets, **When** CI builds release, **Then** signing succeeds without exposing credentials in logs
3. **Given** no signing configuration is present locally, **When** building release, **Then** build succeeds with unsigned APK (for development purposes)

---

### Edge Cases

- What happens when version components exceed expected ranges (e.g., PATCH > 99)?
- How does system handle missing signing keystore during local release builds?
- What happens if CI cannot access signing secrets?
- How does system handle version conflicts during merge?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Build system MUST support semantic versioning format (MAJOR.MINOR.PATCH)
- **FR-002**: Build system MUST generate release APK with version in filename pattern: `atv-{versionName}-release.apk`
- **FR-003**: Version configuration MUST be centralized in a single source of truth (gradle.properties or version.properties)
- **FR-004**: versionCode MUST be automatically calculated from semantic version components
- **FR-005**: CI pipeline MUST build release APK on version tags matching `v*` pattern
- **FR-006**: CI pipeline MUST upload release APK as downloadable artifact
- **FR-007**: Release builds MUST support signing configuration via environment variables
- **FR-008**: Local release builds MUST succeed without signing (unsigned APK) when keystore is not configured

### Key Entities

- **Version**: Semantic version with MAJOR, MINOR, PATCH components
- **Release APK**: Signed or unsigned Android application package with embedded version metadata
- **Signing Configuration**: Keystore, key alias, and passwords for APK signing

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Release APK filename includes version number (verifiable by file inspection)
- **SC-002**: versionCode increments predictably across version bumps (verifiable by APK analysis)
- **SC-003**: CI release workflow completes in under 10 minutes for tagged commits
- **SC-004**: Developers can bump version by editing a single file (version configuration)
- **SC-005**: Release APKs are reproducible - same tag produces identical unsigned APK content

## Assumptions

- Android Gradle Plugin supports custom APK naming via `setProperty("archivesBaseName", ...)`
- GitHub Actions can securely store signing secrets as encrypted secrets
- Project will use debug signing for local development and CI signing for releases
- Initial version starts at 1.0.0 (current configuration)
