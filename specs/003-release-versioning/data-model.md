# Data Model: Release Versioning & Build Pipeline

**Feature**: 003-release-versioning  
**Date**: 2026-01-08  
**Status**: Complete

## Entities

### Version Configuration

**Location**: `gradle.properties`

| Property | Type | Description | Example |
|----------|------|-------------|---------|
| VERSION_MAJOR | Integer | Major version (breaking changes) | 1 |
| VERSION_MINOR | Integer | Minor version (new features) | 0 |
| VERSION_PATCH | Integer | Patch version (bug fixes) | 0 |

**Constraints**:
- VERSION_MAJOR: 0-214
- VERSION_MINOR: 0-74  
- VERSION_PATCH: 0-83
- All must be non-negative integers

### Computed Version Values

**Location**: `app/build.gradle.kts` (derived at build time)

| Value | Type | Formula | Example |
|-------|------|---------|---------|
| versionCode | Int | MAJOR * 10000 + MINOR * 100 + PATCH | 10000 |
| versionName | String | "$MAJOR.$MINOR.$PATCH" | "1.0.0" |
| archivesBaseName | String | "atv-$versionName" | "atv-1.0.0" |

### Signing Configuration

**Location**: Environment variables / GitHub Secrets

| Environment Variable | GitHub Secret | Type | Purpose |
|---------------------|---------------|------|---------|
| KEYSTORE_PATH | (derived from RELEASE_KEYSTORE_BASE64) | String | Path to .jks keystore file |
| KEYSTORE_PASSWORD | RELEASE_KEYSTORE_PASSWORD | String | Password to access keystore |
| KEY_ALIAS | RELEASE_KEY_ALIAS | String | Alias of signing key |
| KEY_PASSWORD | RELEASE_KEY_PASSWORD | String | Password for signing key |

**Constraints**:
- All 4 values must be present for signed builds
- KEYSTORE_PATH must point to valid .jks file
- Secrets must never be logged or committed

## State Transitions

### Version Bump Flow

```
Current: 1.0.0 (versionCode: 10000)
    │
    ├── Patch bump (bug fix)
    │   └── 1.0.1 (versionCode: 10001)
    │
    ├── Minor bump (new feature)
    │   └── 1.1.0 (versionCode: 10100)
    │
    └── Major bump (breaking change)
        └── 2.0.0 (versionCode: 20000)
```

### Build Output States

```
Build Configuration
    │
    ├── No signing config
    │   └── Output: atv-{version}-release-unsigned.apk
    │
    └── Signing config present
        └── Output: atv-{version}-release.apk (signed)
```

## Relationships

```
gradle.properties
    │
    └── defines VERSION_MAJOR, VERSION_MINOR, VERSION_PATCH
            │
            └── read by app/build.gradle.kts
                    │
                    ├── computes versionCode, versionName
                    │
                    ├── sets archivesBaseName for APK naming
                    │
                    └── configures signingConfigs (from env vars)
                            │
                            └── applied to release buildType
                                    │
                                    └── generates APK
                                            │
                                            ├── (local) unsigned APK
                                            │
                                            └── (CI) signed APK via secrets
```

## Validation Rules

| Rule | Validation | Error Message |
|------|-----------|---------------|
| Version format | All components are integers | "Version component must be integer" |
| Version range | MAJOR ≤ 214, MINOR ≤ 74, PATCH ≤ 83 | "Version exceeds maximum supported range" |
| Keystore exists | File at KEYSTORE_PATH exists | Build continues unsigned |
| Secrets complete | All 4 signing env vars present | Build continues unsigned |

## Output Artifacts

| Artifact | Path | Retention |
|----------|------|-----------|
| Release APK | `app/build/outputs/apk/release/atv-{version}-release[-unsigned].apk` | Local: until clean, CI: 90 days |
| Mapping file | `app/build/outputs/mapping/release/mapping.txt` | Same as APK |
| Build metadata | `app/build/outputs/apk/release/output-metadata.json` | Same as APK |
