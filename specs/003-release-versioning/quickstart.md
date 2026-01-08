# Quickstart: Release Versioning & Build Pipeline

**Feature**: 003-release-versioning  
**Date**: 2026-01-08

## Quick Commands

```bash
# Build unsigned release APK locally
./gradlew assembleRelease

# Check current version
grep "VERSION_" gradle.properties

# Create a release (triggers CI)
git tag v1.0.0
git push origin v1.0.0
```

## How to Bump Version

Edit `gradle.properties`:

```properties
# Current version: 1.0.0
VERSION_MAJOR=1
VERSION_MINOR=0
VERSION_PATCH=0
```

| Change Type | What to Bump | Example |
|-------------|--------------|---------|
| Bug fix | VERSION_PATCH | 1.0.0 → 1.0.1 |
| New feature | VERSION_MINOR (reset PATCH) | 1.0.1 → 1.1.0 |
| Breaking change | VERSION_MAJOR (reset MINOR, PATCH) | 1.1.0 → 2.0.0 |

## How to Create a Release

### 1. Bump the version
```bash
# Edit gradle.properties with new version
vim gradle.properties
```

### 2. Commit the version change
```bash
git add gradle.properties
git commit -m "chore: Bump version to 1.1.0"
```

### 3. Create and push a tag
```bash
git tag v1.1.0
git push origin main
git push origin v1.1.0
```

### 4. Monitor the release
- Go to GitHub Actions → Release workflow
- Download APK from workflow artifacts
- Check GitHub Releases page for published release

## How to Set Up Signing (First Time)

### Generate a Keystore
```bash
keytool -genkey -v \
  -keystore release-keystore.jks \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -alias atv-release
```

### Add GitHub Secrets

1. Base64-encode the keystore:
   ```bash
   base64 -i release-keystore.jks -o keystore-base64.txt
   ```

2. Go to GitHub repo → Settings → Secrets → Actions

3. Add these secrets:
   | Secret Name | Value |
   |-------------|-------|
   | RELEASE_KEYSTORE_BASE64 | (contents of keystore-base64.txt) |
   | RELEASE_KEYSTORE_PASSWORD | (your keystore password) |
   | RELEASE_KEY_ALIAS | atv-release |
   | RELEASE_KEY_PASSWORD | (your key password) |

### Local Signing (Optional)
```bash
export KEYSTORE_PATH=/path/to/release-keystore.jks
export KEYSTORE_PASSWORD=your_password
export KEY_ALIAS=atv-release
export KEY_PASSWORD=your_key_password

./gradlew assembleRelease
```

## Output Locations

| Output | Path |
|--------|------|
| Release APK | `app/build/outputs/apk/release/atv-{version}-release.apk` |
| Mapping file | `app/build/outputs/mapping/release/mapping.txt` |
| CI Artifact | GitHub Actions → workflow run → Artifacts |

## Troubleshooting

### APK is unsigned
- **Expected**: If no signing secrets configured
- **To fix**: Set up GitHub Secrets (see above)

### versionCode didn't change
- **Cause**: Forgot to change VERSION_* in gradle.properties
- **To fix**: Bump the appropriate version component

### CI release not triggered
- **Cause**: Tag doesn't match `v*` pattern
- **To fix**: Use format `v1.2.3` (lowercase v, semantic version)

### Signing failed in CI
- **Cause**: Secrets misconfigured
- **To fix**: Verify all 4 secrets are set correctly
- **Debug**: Check if base64 encoding has extra newlines

## Version History

| Version | versionCode | Notes |
|---------|-------------|-------|
| 1.0.0 | 10000 | Initial release |
