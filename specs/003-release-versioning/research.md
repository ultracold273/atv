# Research: Release Versioning & Build Pipeline

**Feature**: 003-release-versioning  
**Date**: 2026-01-08  
**Status**: Complete

## R-01: Gradle APK Naming

### Question
How to customize Android APK output filename to include version number?

### Research Findings

**Android Gradle Plugin 8.x approach**:
```kotlin
android {
    defaultConfig {
        setProperty("archivesBaseName", "atv-$versionName")
    }
}
```

This generates: `atv-1.0.0-release.apk` (or `atv-1.0.0-release-unsigned.apk` if unsigned)

**Alternative (deprecated in AGP 8.x)**:
```kotlin
// Old approach - no longer recommended
applicationVariants.all {
    outputs.all {
        outputFileName = "atv-$versionName-$buildType.apk"
    }
}
```

### Decision
Use `setProperty("archivesBaseName", ...)` in defaultConfig - supported in AGP 8.7.3

---

## R-02: Signing Configuration

### Question
How to configure release signing with environment variables for CI?

### Research Findings

**Gradle DSL approach**:
```kotlin
signingConfigs {
    create("release") {
        val keystorePath = System.getenv("KEYSTORE_PATH")
        val keystorePassword = System.getenv("KEYSTORE_PASSWORD")
        val keyAliasValue = System.getenv("KEY_ALIAS")
        val keyPasswordValue = System.getenv("KEY_PASSWORD")

        if (keystorePath != null && keystorePassword != null && 
            keyAliasValue != null && keyPasswordValue != null) {
            storeFile = file(keystorePath)
            storePassword = keystorePassword
            keyAlias = keyAliasValue
            keyPassword = keyPasswordValue
        }
    }
}

buildTypes {
    release {
        val releaseSigningConfig = signingConfigs.findByName("release")
        if (releaseSigningConfig?.storeFile != null) {
            signingConfig = releaseSigningConfig
        }
    }
}
```

**Key insight**: Conditionally apply signing only when all env vars present. This allows:
- Unsigned builds when no signing config (local dev without keystore)
- Signed builds in CI with secrets configured

### Decision
Use conditional signing configuration. Build succeeds unsigned when env vars missing.

---

## R-03: GitHub Actions Tag-Triggered Workflow

### Question
How to trigger GitHub Actions on version tags and handle signing secrets?

### Research Findings

**Workflow trigger**:
```yaml
on:
  push:
    tags:
      - 'v*'  # Matches v1.0.0, v1.2.3-beta, etc.
```

**Secret handling for keystore**:
```yaml
- name: Decode keystore
  run: |
    echo "${{ secrets.RELEASE_KEYSTORE_BASE64 }}" | base64 -d > ${{ runner.temp }}/release-keystore.jks
    echo "KEYSTORE_PATH=${{ runner.temp }}/release-keystore.jks" >> $GITHUB_ENV

- name: Set signing environment variables
  run: |
    echo "KEYSTORE_PASSWORD=${{ secrets.RELEASE_KEYSTORE_PASSWORD }}" >> $GITHUB_ENV
    echo "KEY_ALIAS=${{ secrets.RELEASE_KEY_ALIAS }}" >> $GITHUB_ENV
    echo "KEY_PASSWORD=${{ secrets.RELEASE_KEY_PASSWORD }}" >> $GITHUB_ENV

# Cleanup after build
- name: Cleanup keystore
  if: always()
  run: rm -f ${{ runner.temp }}/release-keystore.jks
```

**Security considerations**:
- Secrets are masked in logs automatically
- Use `${{ runner.temp }}` for temporary files
- Always cleanup keystore file after build
- Check if secrets exist before attempting to decode

### Decision
Separate release.yml workflow triggered by v* tags. Decode keystore from base64 secret, set env vars, cleanup after.

---

## R-04: versionCode Calculation

### Question
What formula to use for converting semantic version to Android versionCode?

### Research Findings

**Android requirements**:
- versionCode must be positive integer
- Must increase with each release
- Maximum value: 2,147,483,647 (Int.MAX_VALUE)

**Common formulas**:

| Formula | Max Version | Example: 1.2.3 |
|---------|-------------|----------------|
| MAJOR*10000 + MINOR*100 + PATCH | 214.74.83 | 10203 |
| MAJOR*1000000 + MINOR*1000 + PATCH | 2147.483.647 | 1002003 |
| MAJOR*100000 + MINOR*1000 + PATCH | 21474.83.647 | 102003 |

**Trade-offs**:
- Larger multipliers = more version headroom but bigger jumps
- MAJOR*10000 + MINOR*100 + PATCH is common and readable
- Supports versions up to 214.74.83 which is sufficient

**Edge case handling**:
```kotlin
// Validate at build time
require(versionMajor <= 214) { "MAJOR version exceeds maximum (214)" }
require(versionMinor <= 74) { "MINOR version exceeds maximum (74)" }
require(versionPatch <= 83) { "PATCH version exceeds maximum (83)" }
```

### Decision
Use `MAJOR * 10000 + MINOR * 100 + PATCH`. Simple, readable, sufficient range.

---

## Summary of Decisions

| Research Item | Decision |
|---------------|----------|
| APK Naming | `setProperty("archivesBaseName", "atv-$versionName")` |
| Signing Config | Conditional via `System.getenv()`, unsigned fallback |
| CI Trigger | Tag pattern `v*` in separate release.yml |
| versionCode | `MAJOR * 10000 + MINOR * 100 + PATCH` |

## References

- [Android Gradle Plugin DSL](https://developer.android.com/reference/tools/gradle-api)
- [GitHub Actions Encrypted Secrets](https://docs.github.com/en/actions/security-guides/encrypted-secrets)
- [Semantic Versioning](https://semver.org/)
