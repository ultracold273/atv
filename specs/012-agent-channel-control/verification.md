# Agent Channel Control Verification

Verified on 2026-10-31 on branch `codex/012-agent-channel-control`.

## Scope

Implemented exactly `GET /api/v1/channels` and `POST /api/v1/playback/channel`. The agent selects the channel number; ATV validates the catalog version and acknowledges dispatch with `202`. No playback-status, search, pairing, or management endpoint was added.

## Results

| Check | Result |
|-------|--------|
| Debug unit suite | 299 discovered, 298 passed, 1 pre-existing skipped authenticator golden-vector test; no failures/errors |
| JVM control-core line coverage | 341/355 lines, 96.06%, from Kover's source-file counters |
| Android TV smoke suite | 18 passed, including existing playback/setup/import/settings tests and the new control/storage/database cases |
| Python controller tests | 6 passed, including certificate mismatch before sending credentials and preservation of the selected catalog version |
| Debug app and Android-test APK assembly | Passed |
| Android Lint | Passed with zero errors; existing/non-fatal warnings remain |
| Detekt | Passed |
| Release R8 minification | Passed, including optional Netty dependency handling |
| Whitespace / patch validation | `git diff --check` passed |

Core coverage includes the API, authorization gate, manager, server/handler, DTOs, catalog versioning, and switch coordinator. It does not claim whole-app or combined Android/JVM coverage. Keystore, encrypted preferences, UI, and lifecycle integration were additionally exercised on the emulator.

The Android run used a newly created, isolated Android TV API 31 arm64 emulator with a fake player and in-memory Room database. Instrumented control listeners bound only to emulator loopback. No existing AVD user data, physical TV, operator credentials, or live IPTV streams were used or modified.

## Behavior Verified

- Complete ordered metadata catalogs, empty catalogs, duplicate names, stable versions, and private stream URL omission.
- Successful dispatch; malformed input; authorization failures; unknown/stale numbers; unavailable playback; concurrent requests; request deadlines; and client disconnect cancellation.
- Real Android Keystore TLS with out-of-band certificate pinning, non-exportable private keys, encrypted settings persistence, and token rotation/revocation.
- Switching through the existing playback workflow updates the channel display and last-watched preference, dismisses the number pad, and retains normal late stream-error UI behavior.
- Listener shutdown on disable/background, restart when ATV is reopened, and recovery after a port-binding failure.
- D-pad access to settings, the remote-control switch, each connection-details field, and dialog closure. Switching while full-screen settings is active returns `PLAYBACK_UNAVAILABLE`.
- A deliberately rejected Room insert rolls back playlist deletion and preserves the previous catalog/version.

## Performance and Dependencies

On the isolated emulator, 40 warm request pairs against a 1,000-channel fixture measured **8 ms catalog p95** and **7 ms switch-dispatch p95**. The first TLS connection was warmed before measurement. These are loopback/dispatch timings, not voice recognition, LAN reliability, stream startup, or a hardware-TV benchmark.

Netty's eight resolved runtime JARs total **3,363,153 bytes** before DEX conversion and APK compression. The final debug APK measured **50,922,455 bytes** (approximately 48.6 MiB). An isolated before/after APK-size delta was not measured.

The pinned Netty version is **4.1.139.Final**, with Apache-2.0 licensing verified from its Maven parent POM. An OSV query for each of the eight resolved modules returned no advisory entries at verification time. HTTP/2, multipart/decompression handlers, native transports, Log4j/SLF4J, and BlockHound integrations are not enabled. License notices are retained in packaging.

The JVM suite uses the actual HTTPS listener on local ephemeral ports. Android tests cover the production Keystore/JSSE path, including Android's pre-hashed ECDSA signing callback.

## Reproduction

```bash
./studio-gradlew testDebugUnitTest koverXmlReportDebug detekt assembleDebug assembleDebugAndroidTest
python3 -B -m unittest discover -s scripts/tests -v
./studio-gradlew lintDebug minifyReleaseWithR8 --no-daemon --max-workers=1
bash scripts/android-smoke-classes.sh
```

Run the listed instrumented classes on a dedicated Android TV emulator. The configured smoke list includes the functional control tests; the device-specific latency fixture can be run separately as `com.example.atv.RemoteControlSmokeTest#thousandChannelWarmRequestsMeetLatencyTarget`.

Lint was run separately with one worker after an incremental Kotlin/FIR analysis failure during a combined build. The fresh serialized invocation completed successfully; no lint checks were disabled. Existing pairing/number-pad helper complexity and formatting findings were fixed without changing their behavior so Detekt also passes.
