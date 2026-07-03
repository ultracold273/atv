# Android Testing

## Seeded Playback Smoke

`PlaybackSmokeTest` is the first Android TV E2E safety check. It launches
`MainActivity` on an emulator, seeds an in-memory Room database, replaces
persistent preferences with an in-memory fake, and replaces the real media
player with a fake `AtvPlayerController`.

The test is intentionally not a visual video playback demo. It validates the
real Activity, Compose navigation tree, D-pad handling, and playback UI state
without depending on network streams, codecs, or ExoPlayer readiness.

Run locally with an Android TV emulator attached:

```bash
./studio-gradlew app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.example.atv.PlaybackSmokeTest
```

The CI workflow runs the same class on a headless Android TV emulator and
uploads connected-test reports from:

```text
app/build/outputs/androidTest-results/connected/
app/build/reports/androidTests/connected/
```

Future media integration tests can use a real player plus a controlled stream
source or mock server. Keep those separate from this smoke so UI refactors get a
fast, deterministic signal.
