# Android Testing

## Seeded Smoke Tests

The Android TV smoke tests launch `MainActivity` on an emulator, seed an
in-memory Room database, replace persistent preferences with an in-memory fake,
and replace the real media player with a fake `AtvPlayerController`.

- `PlaybackSmokeTest`: seeded channels launch playback, switch channel, and open
  the channel list.
- `SetupSmokeTest`: empty channel state launches the setup screen and shows the
  channel source entry point.

The test is intentionally not a visual video playback demo. It validates the
real Activity, Compose navigation tree, D-pad handling, and playback UI state
without depending on network streams, codecs, or ExoPlayer readiness.

Run locally with an Android TV emulator attached:

```bash
./studio-gradlew app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.example.atv.PlaybackSmokeTest,com.example.atv.SetupSmokeTest
```

The CI workflow runs the same classes on a headless Android TV emulator and
uploads connected-test reports from:

```text
app/build/outputs/androidTest-results/connected/
app/build/reports/androidTests/connected/
```

Future media integration tests can use a real player plus a controlled stream
source or mock server. Keep those separate from this smoke so UI refactors get a
fast, deterministic signal.
