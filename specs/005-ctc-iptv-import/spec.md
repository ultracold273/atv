# Feature Specification: CTC IPTV Channel Import

**Feature Branch**: `005-ctc-iptv-import`
**Created**: 2026-06-17
**Status**: Draft
**Input**: Light up the dormant EPG feature shipped in spec 004 by adding the China Telecom (CTC) login UI, encrypted credential storage, the "Test connection & import channels" action, and the Room schema change that promotes `Channel.channelCode` from a temporary extension property to a real data-class field.

## Overview

Spec 004 shipped the entire EPG UI and the CTC provider stack but left `CtcEpgProvider.isConfigured` permanently false, so no user ever saw program data. Spec 005 is the wire-up: a Settings sub-screen where the user enters their CTC credentials, encrypted storage at rest, a one-button "Test & import" flow that authenticates, fetches the operator channel list, replaces the local playlist, and flips `isConfigured` to true. From the user's perspective this is the moment the EPG feature actually appears.

The work also retires the temporary `Channel.channelCode: String?` extension property introduced in spec 004 by replacing it with a real Room-backed field on `Channel`, and removes the privacy-guardrail `TODO(005)` markers from `EpgNetworkModule` (the hardcoded operator URL is moved behind a user-entered preference).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Enter CTC Credentials and Import Channels (Priority: P1)

As a CTC IPTV subscriber, I want to enter my account credentials in Settings and have the app import my channel lineup with EPG metadata, so that I see now-playing program info on every channel switch and can browse schedules in the channel list overlay.

**Why this priority**: This is the only user-facing surface that activates the EPG feature shipped in spec 004. Without it, the feature is invisible to every user.

**Independent Test**: Enter valid CTC credentials in Settings → tap "Test connection & import channels" → verify channels appear, EPG banner shows now/next on next channel switch, and the EPG panel renders schedules in the channel list overlay.

**Acceptance Scenarios**:

1. **Given** I am on the Settings screen with no CTC credentials configured, **When** I navigate to "IPTV setup", **Then** I see a form with six fields (User ID, Password, STB ID, IP, MAC, Auth server URL) and a "Test connection & import channels" button. The STB ID, IP, MAC, and auth server URL are pre-filled with sensible auto-generated defaults; User ID and Password are empty.
2. **Given** I am on the IPTV setup screen, **When** I enter my User ID and Password and tap "Test connection & import channels", **Then** the status block shows "Logging in…" then "Fetching channels…" then "Imported N channels", and on success my channel list is replaced with the operator-provided lineup.
3. **Given** I have just imported channels successfully, **When** I return to the playback screen and switch channels, **Then** the bottom-center program block appears alongside the top-left channel banner with the current and next programs for the new channel.
4. **Given** I have just imported channels successfully, **When** I open the channel list overlay, **Then** the side-by-side EPG panel renders with schedules for each focused channel.
5. **Given** I enter an invalid User ID or Password, **When** I tap "Test connection & import channels", **Then** the status block shows "Login failed: <reason>" and my existing channels (M3U8 or previously imported) are NOT replaced.
6. **Given** login succeeds but the channel-list endpoint returns no channels, **When** the import completes, **Then** the status block shows "No channels returned by the server" and my existing channels are NOT replaced.
7. **Given** I tap "Test connection & import channels" while the password field is empty, **When** the form validates, **Then** the button is disabled (visibly grayed) and tapping does nothing.

---

### User Story 2 - Auto Re-Login on App Launch (Priority: P2)

As a user who has previously imported my CTC channels, I want the app to re-authenticate and refresh my channel list on every app launch, so that operator-side channel additions/removals propagate without me having to remember to manually re-import.

**Why this priority**: Channel lineups change over time on the operator side. Without auto re-login the user's local list drifts; first-class lifeness is part of "it just works" for a subscriber product.

**Independent Test**: Import channels successfully, force-stop the app, restart it, and verify the EPG surfaces still work without any manual action — and that a (silent) network request was made during startup.

**Acceptance Scenarios**:

1. **Given** I have CTC credentials stored, **When** I launch the app, **Then** a background re-login + channel refresh runs once during startup, without blocking the UI.
2. **Given** the auto re-login succeeds, **When** I switch channels after launch, **Then** the EPG banner works exactly as if I had just imported manually.
3. **Given** the auto re-login fails (network down, server returned an error, credentials no longer valid), **When** I open the Settings screen, **Then** the IPTV setup row shows a small subtitle "Last sync failed: <reason>" and my previously-imported channels remain usable.
4. **Given** I have NO CTC credentials stored, **When** I launch the app, **Then** NO auto re-login fires and the app behaves identically to a fresh install with only M3U8 channels.
5. **Given** my previously-imported channels are still in Room, **When** auto re-login fails, **Then** the EPG surfaces continue rendering against the cached `channel_code` values until the next successful re-import.

---

### User Story 3 - Clear CTC Credentials (Priority: P2)

As a user who no longer wants to use CTC EPG (e.g., switching providers or for privacy reasons), I want to clear my stored credentials and have the app revert to M3U8-only behavior.

**Why this priority**: Without a way to clear credentials, users can't opt out without uninstalling. Required by basic privacy hygiene.

**Independent Test**: Import successfully, then tap "Clear IPTV credentials" in Settings, confirm, and verify (a) the credential fields revert to defaults, (b) `isConfigured` flips false, (c) the EPG surfaces disappear, and (d) the auth-server URL stops being contactable on next launch.

**Acceptance Scenarios**:

1. **Given** I have CTC credentials stored, **When** I tap "Clear IPTV credentials" in the IPTV setup screen, **Then** a confirmation dialog appears.
2. **Given** the confirmation dialog is open, **When** I confirm, **Then** all six credential fields are wiped from encrypted storage and re-render as defaults (auto-generated STB/IP/MAC, empty UserID/Password).
3. **Given** I have just cleared credentials, **When** I return to playback, **Then** the EPG surfaces are hidden (banner shows only top-left channel info; channel list overlay has no side panel).
4. **Given** I have just cleared credentials, **When** the next app launch fires, **Then** NO auto re-login attempt is made.
5. **Given** I have imported CTC channels (which replaced my M3U8 playlist), **When** I clear credentials, **Then** the imported channels remain in the DB (they were the active playlist; clearing credentials doesn't auto-revert the playlist). The user can use the existing "Load New Playlist" flow to re-import M3U8.

---

### Edge Cases

- **First-open device defaults**: when the IPTV setup screen opens with no stored credentials, STB ID is generated as a random 32-hex string (cryptographic-grade randomness), IP is read from the device's current LAN interface (best-effort; falls back to `192.0.2.1` documentation range if no LAN is detected), and MAC is generated in the RFC 7042 documentation range (`00:00:5E:00:53:xx`). User can override every field.
- **Credential entry on a TV remote**: all six fields use standard Compose `TextField` with Android TV's leanback IME. No custom number pad; the existing playback number pad is for channel selection only. Soft keyboard appearance/dismissal is the platform's responsibility.
- **Password field masking**: the password field uses `PasswordVisualTransformation()` so the value is hidden from over-the-shoulder readers. Saved value is round-trippable (encrypt → decrypt).
- **Form validation before import**: User ID and Password must be non-blank, STB ID must be exactly 32 characters, IP/MAC must be non-blank, Auth server URL must parse as a valid HTTP(S) URL. The "Test & import" button is disabled until all five pass; field-level inline error messages indicate why.
- **Login failure mid-flow**: any failure from `CtcAuthClient.login()` (network, parse, auth) surfaces as a `LoginFailure(reason)` result. Existing channels remain untouched; `isConfigured` stays at whatever it was before (so an active EPG session doesn't break if a fresh login attempt fails).
- **Empty channel list returned**: if `frameset_builder.jsp` returns a successful response with zero channels, we treat that as a soft failure (`NoChannelsReturned`) — existing channels NOT replaced, no `isConfigured` flip, status surfaces an explicit message.
- **Migration from spec 004's extension to a real field**: existing `Channel` rows in Room have no `channel_code` value (column doesn't exist yet). The `Migration(1, 2)` adds the column with NULL default. M3U8-loaded channels keep working with `channel_code = null`, exactly as they did under the 004 extension property.
- **Concurrent "Test & import" taps**: if the user mashes the button, only the first invocation runs; subsequent calls are no-ops while a fetch is in flight (state machine: `Idle → InProgress → Success|Failure → Idle`).
- **Auto re-login during active playback**: the bootstrap runs in `applicationScope` (not `viewModelScope`); even if it's still in flight when the user starts watching a channel, it cannot interrupt playback. On completion it replaces channels via Room's transactional `savePlaylistChannels` — `ChannelRepository.getAllChannels()` Flow emits the new list and the UI updates seamlessly.
- **EncryptedSharedPreferences key rotation**: the underlying AndroidX Security key is bound to the device. If the user clears Keystore data, decryption fails and the credential store treats the data as missing — the user re-enters credentials on next setup screen visit.

## Requirements *(mandatory)*

### Functional Requirements

**Credential Management**

- **FR-001**: The system MUST provide an "IPTV setup" sub-screen reachable from the Settings screen via a new menu row labeled "IPTV setup" placed between "Show program guide" and "Clear All Data".
- **FR-002**: The IPTV setup screen MUST expose six editable fields: User ID, Password, STB ID, Local IP, Local MAC, and Auth server URL.
- **FR-003**: The Password field MUST mask its value via `PasswordVisualTransformation` and MUST be stored encrypted.
- **FR-004**: All six credential fields MUST be stored at rest in `EncryptedSharedPreferences` (AndroidX Security library). Plain DataStore storage is NOT acceptable for any of these fields.
- **FR-005**: When the IPTV setup screen opens for the first time (no stored credentials), the STB ID, IP, MAC, and Auth server URL fields MUST be pre-filled with auto-generated defaults; User ID and Password MUST be empty.
- **FR-006**: Auto-generated STB ID MUST be a 32-character random hex string from a `SecureRandom` source.
- **FR-007**: Auto-generated IP MUST be read from the device's current non-loopback IPv4 LAN interface; if no LAN is available, the system MUST fall back to `192.0.2.1` (RFC 5737 documentation range).
- **FR-008**: Auto-generated MAC MUST use the RFC 7042 documentation range `00:00:5E:00:53:xx` with a random last byte.
- **FR-009**: The system MUST provide a "Clear IPTV credentials" action on the IPTV setup screen; activating it MUST show a confirmation dialog; confirming MUST wipe all six stored values from encrypted storage.

**Test & Import Action**

- **FR-010**: The IPTV setup screen MUST provide a "Test connection & import channels" button.
- **FR-011**: The button MUST be disabled until form validation passes: User ID non-blank, Password non-blank, STB ID exactly 32 characters, IP non-blank, MAC non-blank, Auth server URL parses as a valid HTTP(S) URL.
- **FR-012**: When activated, the import action MUST first run `CtcAuthClient.login()` with the supplied credentials; on success it MUST fetch the operator channel list (via the `frameset_builder.jsp` endpoint), map each entry to the domain `Channel` model (preserving `channel_code` from the operator-supplied `ChannelID`), and persist the result via `ChannelRepository.savePlaylistChannels(channels)`.
- **FR-013**: The status block on the IPTV setup screen MUST surface one of these states in real time: `Idle | Logging in… | Fetching channels… | Imported N channels | Login failed: <reason> | No channels returned | Network error: <reason>`.
- **FR-014**: On a successful import, the system MUST flip `CtcEpgProvider.isConfigured` to `true`. On any failure, `isConfigured` MUST remain at its current value (no flip).
- **FR-015**: On a successful import, the system MUST replace the playlist-loaded channels transactionally via the existing `ChannelRepository.savePlaylistChannels(channels)` (which deletes rows where `is_manually_added = 0` and inserts the new list). Manually-added channels (`is_manually_added = 1`) MUST be preserved — they are user-owned data per spec 001 FR-017 and the user can delete them via the existing channel management UI.
- **FR-016**: On a failed import (login, fetch, or empty-list), existing channels MUST NOT be modified.
- **FR-017**: Concurrent activations of "Test & import" MUST be coalesced — only the first invocation runs; subsequent taps are no-ops while a fetch is in flight.

**Auto Re-Login**

- **FR-018**: When the application launches AND credentials are stored AND the credentials are complete (per FR-011 validation), the system MUST asynchronously re-authenticate and re-fetch the channel list once, in a coroutine scoped to the Application (not any ViewModel).
- **FR-019**: The auto re-login MUST NOT block any UI; it MUST run on a background thread.
- **FR-020**: On a successful auto re-login, the system MUST update the channel list (transactional replace, same as FR-015) and flip `isConfigured` to `true`.
- **FR-021**: On a failed auto re-login, the system MUST log the failure via Timber, MUST NOT modify the existing channel list, MUST NOT flip `isConfigured` (it may already be true from a previous session — leave it; it may also be false, in which case the EPG surfaces remain hidden), and MUST surface the failure reason via a `Last sync failed: …` subtitle on the IPTV setup row the next time the user opens Settings.
- **FR-022**: When NO credentials are stored, NO auto re-login MUST fire, no IPTV-related network requests MUST occur, and the EPG surfaces MUST stay hidden exactly as in the pre-005 state (where `isConfigured = false`).

**Schema Migration**

- **FR-023**: The `Channel` data class MUST gain a real `val channelCode: String? = null` field, replacing the temporary extension property introduced in spec 004.
- **FR-024**: The `ChannelEntity` Room entity MUST gain a `@ColumnInfo(name = "channel_code") val channelCode: String? = null` field.
- **FR-025**: The Room database version MUST be incremented from 1 to 2.
- **FR-026**: A `Migration(1, 2)` MUST be registered that executes `ALTER TABLE channels ADD COLUMN channel_code TEXT` (nullable, no default). Existing rows MUST keep their data; their `channel_code` MUST become NULL.
- **FR-027**: The `ChannelEpgExtensions.kt` file (containing the temporary extension property) MUST be deleted. All call sites continue to compile because the new data-class field has the same name and nullability.
- **FR-028**: `fallbackToDestructiveMigration()` MUST NOT be used. Existing user data MUST survive the migration.

**Privacy Guardrail Follow-Through**

- **FR-029**: The hardcoded `itv.jsinfo.net:8298` auth server URL in `EpgNetworkModule.provideAuthServerUrl` MUST be removed. The `@Named("authServerUrl")` provider MUST instead read the current value from `IptvCredentialsStore`; when no value is stored, the provider MUST return an explicit sentinel (e.g., empty string), and consumers MUST treat that as "not configured" identically to `isConfigured = false`.
- **FR-030**: The sentinel `DeviceProfile` provider in `EpgNetworkModule` MUST be removed in favor of reading from `IptvCredentialsStore`.
- **FR-031**: After spec 005, NO operator-specific URL or device identifier MUST appear in committed source code outside test fixtures (which already use RFC documentation ranges per spec 004 review).

**Observability**

- **FR-032**: The system MUST log auto re-login attempts and outcomes via Timber at debug level in debug builds (per the spec 001 logging policy FR-038–FR-040).
- **FR-033**: The system MUST NOT log decrypted credentials, JSESSIONID values, authenticator hex, or any other sensitive material at any log level.

### Key Entities

- **IptvCredentials**: Tuple of six user-entered values (UserID, Password, STB ID, IP, MAC, Auth server URL). Stored encrypted; equality is value-based; has an `isComplete` derived flag matching FR-011 validation rules.
- **IptvCredentialsStore**: Interface exposing `observe(): Flow<IptvCredentials?>`, `suspend save(creds)`, `suspend clear()`. Implementation wraps `EncryptedSharedPreferences`.
- **DeviceDefaultsProvider**: Pure helper that generates a starting `IptvCredentials` with auto-filled STB/IP/MAC and empty UserID/Password.
- **ImportResult**: Sealed class for the "Test & import" action — `Success(importedCount: Int)`, `LoginFailure(reason)`, `FetchFailure(reason)`, `NoChannelsReturned`.
- **ImportCtcChannelsUseCase**: Orchestrates login → fetch → map → save → flip `isConfigured`. Single source of truth for the import flow; shared between the manual "Test & import" button and the auto re-login bootstrapper.
- **IptvSessionBootstrapper**: Application-scoped singleton that fires once at startup if credentials exist.
- **CtcChannelFetcher**: New CTC wire-protocol unit alongside `CtcAuthClient` and `CtcEpgProvider`. Ports the `frameset_builder.jsp` step from the python reference.
- **Channel** (modified): Existing domain model gains a real `channelCode: String?` field.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A user with valid CTC credentials can complete first-time setup (open Settings → IPTV setup → enter UserID & Password → tap Test & import) in under 60 seconds on a TV remote (excluding their own typing speed).
- **SC-002**: A successful import populates the channel list and flips `isConfigured`; the next channel switch in playback renders the bottom-center program block within 500ms (existing SC-001 from spec 004 still applies).
- **SC-003**: Re-launching the app after a successful import results in zero user-visible setup steps before EPG surfaces work — auto re-login fires in background within 2 seconds of `onCreate`.
- **SC-004**: A user with NO CTC credentials sees zero behavioral change from before 005 — same M3U8 flow, no EPG surfaces, no IPTV-related network traffic. Verified by network telemetry / unit assertion that no `CtcAuthClient.login()` is called.
- **SC-005**: The Room migration from v1 to v2 preserves 100% of existing channel rows. Verified by an instrumented migration test using Room's `MigrationTestHelper`.
- **SC-006**: All stored credential fields, including UserID, are encrypted at rest. Verified by inspecting the on-disk `SharedPreferences` XML and asserting no plaintext credential appears.
- **SC-007**: NO operator-specific URL or device identifier appears in committed source code outside `app/src/test/`. Verified by a grep in CI (or at minimum during code review).
- **SC-008**: 100% of new user-facing strings in the IPTV setup screen are localized via `strings.xml` (per spec 001 FR-037), with Chinese translations alongside.

## Assumptions

- Users in possession of valid CTC credentials know their UserID and Password but do NOT know their STB ID, IP, or MAC. Auto-generated defaults are acceptable for first-time use; advanced users can override.
- The CTC operator endpoint (`itv.jsinfo.net:8298` by default) remains reachable from the user's network. Network failures are surfaced clearly but no offline fallback exists beyond the cached channel list.
- The 3DES authenticator port (already shipped in spec 004) is byte-correct against the python reference. If the as-yet-ungated golden 3DES fixture test is enabled in 005 and fails, that's a spec-004 bug to fix here.
- Android TV devices in scope have working `NetworkInterface.getNetworkInterfaces()` enumeration for LAN IP detection. Devices without LAN connectivity fall back to the documentation IP without error.
- `EncryptedSharedPreferences` and the Android Keystore are reliable on Android 10+ (the project's minSdk). Devices with broken Keystore implementations (rare; usually vendor-specific) will see decryption failures treated as "no credentials" — they re-enter on next visit.
- The user accepts that initial import REPLACES their M3U8 playlist (matching the spec 001 single-playlist convention). A future spec may add multi-playlist support; not in scope here.

## Out of Scope (this spec)

- **Alternative EPG providers** (XMLTV file import, other operators). The `EpgProvider` abstraction admits them but no second implementation ships in 005.
- **Program details screen** (`onClick` on a program row in `EpgPanel`).
- **Time-shift / replay actions** (`isReplayable` is preserved as data; no UI action wires to it).
- **QR-code pairing for credential entry** (TV → phone soft-keyboard handoff). Considered during brainstorming and rejected as scope-disproportionate for 005.
- **Periodic background re-login** beyond the per-launch refresh. Session-expired mid-stream re-login is already handled by the spec-004 `SessionExpiredException` path.
- **Multi-account / profile switching**. One set of credentials per device.
- **Reverting from CTC channels back to M3U8 automatically when credentials are cleared**. The user must re-load their M3U8 file via the existing "Load New Playlist" flow.
- **A "remember me / don't auto-login" toggle**. Auto re-login is unconditional when credentials are present.

## Dependencies

- **`001-iptv-player`**: base playback, channel list, channel info banner, Settings infrastructure, DataStore preferences, i18n via `strings.xml`. The new IPTV setup screen extends `SettingsScreen`'s existing entry list and navigation.
- **`004-epg-program-guide`**: the EPG UI surfaces, the `EpgProvider` interface, `CtcAuthClient`, `CtcEpgProvider`, and the temporary `Channel.channelCode` extension property that this spec retires. Spec 005's `Channel.channelCode` real field is a drop-in replacement: same name, same nullability — call sites in `PlaybackViewModel` and `EpgPanel` compile unchanged after the extension file is deleted.
- **`iptv_client.py`** (in `~/Documents/itv-reverse/`): reference Python implementation. Spec 005 ports the `frameset_builder.jsp` and `get_channel_info_mapping.jsp` steps not exercised in 004 (only login + `prevue_list.jsp` were ported there).
- **AndroidX Security library** (`androidx.security:security-crypto`): adds `EncryptedSharedPreferences` and `MasterKey`. New dependency in `libs.versions.toml`.

## Technical Decisions

- **`EncryptedSharedPreferences` over a hand-rolled Keystore solution.** Chosen for: standard library, ~100 lines to wire in, encrypts each value with AES-256 GCM under a key bound to Android Keystore. Hand-rolling adds failure modes (key generation races, encoding decisions) with no benefit.
- **`Channel.channelCode` as a nullable real field, not a separate "EpgChannel" model.** Same name and nullability as the spec 004 extension so call sites need no change. Keeps the domain model concise; CTC and M3U8 channels live in the same table.
- **One use case, two callers.** `ImportCtcChannelsUseCase` is invoked by both the manual "Test & import" button and the `IptvSessionBootstrapper` auto-relogin. Single import path, single set of edge cases to test.
- **Application-scoped bootstrap, not ViewModel-scoped.** ViewModel scope dies when the user leaves Settings or Playback; the bootstrap must outlive any screen.
- **Real `Migration(1, 2)`, no `fallbackToDestructiveMigration()`.** Existing user data survives. This is the project's first migration; the pattern established here is reusable.
- **No new "IPTV enabled" master toggle.** The spec 004 "Show program guide" toggle already gates UI visibility. Storing credentials AND that toggle being on is sufficient; adding a third "use IPTV" flag would be redundant state.
- **Soft keyboard for all credential fields**, no custom number-pad reuse. Two input modes (number pad for channel selection, soft keyboard for credentials) reflect their different roles — channel selection is a transient action; credentials are a one-time setup. Mixing them is confusing.
- **Sentinel auth-server URL → user-entered preference.** Closes the spec 004 `TODO(005)` privacy guardrail. The provider falls back to an empty string when no credentials are stored, and `isConfigured` blocks any actual request.
