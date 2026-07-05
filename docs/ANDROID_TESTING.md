# Android Testing

## Seeded Smoke Tests

The Android TV smoke tests launch `MainActivity` on an emulator, seed an
in-memory Room database, replace persistent preferences with an in-memory fake,
and replace the real media player with a fake `AtvPlayerController`.

- `PlaybackSmokeTest`: seeded channels launch playback, switch channel, open
  the channel list, select a listed channel, switch by number pad, and open the
  quick settings menu.
- `SetupSmokeTest`: empty channel state launches the setup screen, shows the
  channel source entry point, and opens the channel source screen.
- `ChannelNavigationTest`, `SettingsFlowTest`, and `PlaylistLoadingTest` replace
  the old quarantined legacy suites with seeded robot-driven coverage.
- `MockServerPlaylistImportSmokeTest`: imports an M3U8 playlist from
  `MockWebServer`, persists channels, and never contacts a live playlist URL.
- `MockServerProxyImportSmokeTest`: imports Home Proxy channels from
  `MockWebServer`, including bearer-token request verification.
- `PlaybackViewModelCtcImportIntegrationTest`: covers Direct CTC import with a
  `MockWebServer` login/channel-fetch transcript.

The test is intentionally not a visual video playback demo. It validates the
real Activity, Compose navigation tree, D-pad handling, and playback UI state
without depending on network streams, codecs, or ExoPlayer readiness.

Run locally with an Android TV emulator attached:

```bash
./studio-gradlew app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class="$(bash scripts/android-smoke-classes.sh)"
```

The smoke suite membership lives in:

```text
config/android-smoke-classes.txt
```

The CI workflow runs the same classes on a headless Android TV emulator and
uploads connected-test reports from:

```text
app/build/outputs/androidTest-results/connected/
app/build/reports/androidTests/connected/
app/build/reports/android-smoke-diagnostics/
```

CI captures logcat after each smoke run and captures a screenshot when the
instrumented smoke command fails.

Future media integration tests can use a real player plus a controlled stream
source or mock server. Keep those separate from this smoke so UI refactors get a
fast, deterministic signal.

## TODO / Next PRs

- Real-player media integration smoke: add a separate test only after a
  controlled stream source exists. Keep it separate from the fast UI smoke gate
  because it covers codecs/player readiness and will be slower/flakier by
  nature.
