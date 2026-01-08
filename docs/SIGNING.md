# Release Signing Setup

This document explains how to configure release signing for the ATV Android TV app.

## Overview

Release APKs can be signed using a keystore. The signing configuration supports:
- **Local signing**: For developers building release APKs locally
- **CI signing**: For automated builds via GitHub Actions

## Generate a Signing Key

If you don't have a keystore, generate one:

```bash
keytool -genkey -v \
  -keystore release-keystore.jks \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -alias atv-release
```

You'll be prompted for:
- **Keystore password**: Password to access the keystore file
- **Key alias**: Name for your signing key (use `atv-release`)
- **Key password**: Password for the specific key
- **Distinguished Name**: Your name, organization, location, etc.

⚠️ **IMPORTANT**: Store your keystore securely and back it up! If you lose it, you cannot update your app on Google Play.

## Local Signing

For local development, set environment variables before building:

```bash
export KEYSTORE_PATH=/path/to/your/release-keystore.jks
export KEYSTORE_PASSWORD=your_keystore_password
export KEY_ALIAS=atv-release
export KEY_PASSWORD=your_key_password

./gradlew assembleRelease
```

Or create a `local.properties` file (gitignored):

```properties
# DO NOT COMMIT THIS FILE
signing.keystore.path=/path/to/release-keystore.jks
signing.keystore.password=your_keystore_password
signing.key.alias=atv-release
signing.key.password=your_key_password
```

## CI Signing (GitHub Actions)

### Step 1: Encode Your Keystore

Convert your keystore to base64:

```bash
base64 -i release-keystore.jks -o keystore-base64.txt
```

### Step 2: Add GitHub Secrets

Go to your repository → Settings → Secrets and variables → Actions → New repository secret

Add these secrets:

| Secret Name | Value |
|-------------|-------|
| `RELEASE_KEYSTORE_BASE64` | Contents of `keystore-base64.txt` |
| `RELEASE_KEYSTORE_PASSWORD` | Your keystore password |
| `RELEASE_KEY_ALIAS` | `atv-release` (or your alias) |
| `RELEASE_KEY_PASSWORD` | Your key password |

### Step 3: Trigger a Release

Create and push a version tag:

```bash
git tag v1.0.0
git push origin v1.0.0
```

The release workflow will automatically:
1. Decode the keystore from secrets
2. Build and sign the release APK
3. Upload the APK as a GitHub artifact
4. Create a GitHub Release with the APK attached

## Building Without Signing

If no signing configuration is present, the build will succeed with an unsigned APK. This is useful for:
- Local development testing
- CI builds when secrets aren't configured
- Debug/test builds

```bash
# Build unsigned release APK
./gradlew assembleRelease
```

## Verifying Signed APKs

Check if an APK is signed:

```bash
# Using apksigner (Android SDK)
apksigner verify --print-certs app/build/outputs/apk/release/atv-1.0.0-release.apk

# Using jarsigner (JDK)
jarsigner -verify -verbose -certs app/build/outputs/apk/release/atv-1.0.0-release.apk
```

## Security Best Practices

1. **Never commit** keystores or passwords to version control
2. **Use different keys** for debug and release builds
3. **Back up** your release keystore securely (encrypted cloud storage, safe deposit box)
4. **Rotate secrets** periodically (GitHub allows updating secrets)
5. **Use environment variables** instead of hardcoding paths/passwords
6. **Restrict access** to GitHub repository secrets to trusted team members

## Troubleshooting

### APK not signed in CI
- Verify all 4 secrets are set correctly in GitHub
- Check workflow logs for "Decode keystore" step
- Ensure base64 encoding is correct (no extra newlines)

### Invalid keystore format
- Re-generate base64 encoding
- Ensure the original `.jks` file isn't corrupted
- Try: `base64 -i release-keystore.jks | tr -d '\n' > keystore-base64.txt`

### Key not found in keystore
- Verify `KEY_ALIAS` matches the alias used during key generation
- List aliases: `keytool -list -v -keystore release-keystore.jks`
