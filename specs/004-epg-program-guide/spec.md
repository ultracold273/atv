# Feature Specification: EPG Program Guide

**Feature Branch**: `004-epg-program-guide`
**Created**: 2026-06-07
**Status**: Draft
**Input**: Show the programs each channel is playing — a new EPG (Electronic Program Guide) surface in the app, plus now-playing / up-next program info on the channel info banner every time a channel is switched.

## Overview

This feature adds two user-visible EPG surfaces to ATV:

1. A **program info block on the channel info banner** that appears (bottom-center) alongside the existing channel number/name (top-left) every time a new channel is landed, showing the current program and the next program. Both blocks share the banner's visibility lifecycle and 3-second auto-hide.
2. A **side-by-side EPG panel** inside the channel list overlay that shows the focused channel's schedule for yesterday, today, or tomorrow, browsable with D-pad.

A small data-layer abstraction (`EpgProvider`) sits behind both surfaces so the UI doesn't know which operator's EPG it's displaying. One provider implementation ships with this spec: the China Telecom (CTC) provider that talks to the operator's EPG endpoint described in `IPTV_AUTH_PROTOCOL.md` and reverse-engineered in `iptv_client.py`.

**This spec deliberately defers** the CTC authentication UI, credential storage, and channel-code import to a follow-up spec (`005-ctc-iptv-import`). Without that follow-up, the EPG feature ships wired end-to-end but every channel shows an "EPG not available" empty state because no channel has a `channelCode`. This split lets us land and test the UI surface and provider plumbing in isolation, then light it up in 005.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - See What's Playing on Channel Switch (Priority: P1)

As a TV viewer, when I switch channels, I want to see what's currently airing and what's on next, so I can decide whether to stay on the channel without waiting for a program to identify itself.

**Why this priority**: This is the primary user-facing value of the feature. Channel surfing is the most frequent user action, and program context turns "what is this channel?" into "what's on right now and is it worth staying for?" The information must appear with the existing channel banner without adding any new interactions.

**Independent Test**: With EPG enabled and a channel that has program data, switch to that channel and observe the bottom-center program block appear together with the top-left channel banner, then both auto-hide together after 3 seconds.

**Acceptance Scenarios**:

1. **Given** EPG is enabled and the focused channel has program data, **When** I switch to that channel, **Then** the top-left banner shows channel number/name as today AND a bottom-center block shows the current program (title + time slot + visual progress) and the next program (title + start time).
2. **Given** EPG is enabled but the channel has no program data, **When** I switch to that channel, **Then** the top-left banner shows as today and the bottom-center block does not appear.
3. **Given** EPG is disabled in settings, **When** I switch channels, **Then** the banner behaves exactly as it does without this feature — no bottom block, no network calls.
4. **Given** the banner is visible with program info, **When** the 3-second auto-hide elapses, **Then** both the top-left and bottom-center blocks disappear together.
5. **Given** the banner is visible, **When** I switch to a different channel before the banner auto-hides, **Then** the banner content updates to the new channel and the auto-hide timer restarts.
6. **Given** EPG data is still being fetched when the 3-second window elapses, **Then** the banner auto-hides as scheduled and does not re-appear when the data arrives.

---

### User Story 2 - Browse Other Channels' Schedules (Priority: P2)

As a TV viewer, I want to open the channel list and see what's airing on each channel as I move my focus through the list, so I can pick a channel based on what's actually on, not just by name.

**Why this priority**: Browsing the schedule is the natural extension of "what's on right now" — it lets users plan ahead without leaving the app. Less frequent than channel-switch banner reads, but the place where the EPG surface earns its keep.

**Independent Test**: With EPG enabled, open the channel list overlay, move focus down the channel list, and verify the right-side panel updates to show the focused channel's program schedule with smooth focus transfer between the channel column and the EPG panel.

**Acceptance Scenarios**:

1. **Given** EPG is enabled, **When** I open the channel list overlay, **Then** the overlay shows the channel list on the left and a side-by-side EPG panel on the right.
2. **Given** the channel list is open and EPG is enabled, **When** I focus a channel, **Then** the EPG panel shows that channel's schedule for the currently selected date (default: today).
3. **Given** the EPG panel is showing programs, **When** I press D-pad RIGHT from a channel row, **Then** focus moves into the EPG panel (either the date tabs or the program list).
4. **Given** focus is inside the EPG panel, **When** I press D-pad LEFT, **Then** focus returns to the previously focused channel in the channel column.
5. **Given** focus is on a channel in the channel column, **When** I press D-pad LEFT, **Then** the channel list overlay dismisses (preserving today's behavior).
6. **Given** the EPG panel has date tabs (Yesterday / Today / Tomorrow), **When** I focus a date tab and press OK, **Then** the panel re-fetches and displays programs for that date.
7. **Given** the program currently airing is in the displayed schedule, **When** the panel renders, **Then** that program is visually highlighted as "now playing".
8. **Given** I rapidly scroll D-pad DOWN through 50 channels, **When** scrolling stops, **Then** the EPG panel shows the schedule for the channel where focus landed (not for every intermediate channel) and no visible flicker or stale data appears.
9. **Given** EPG is disabled in settings, **When** I open the channel list overlay, **Then** the overlay shows only the channel list with no EPG panel, identical to today's behavior.
10. **Given** the focused channel has no `channelCode` (and therefore no EPG source), **When** the panel renders, **Then** it shows "EPG not available for this channel" instead of program rows.

---

### User Story 3 - Control Whether EPG Is Shown (Priority: P2)

As a TV viewer, I want to enable or disable the EPG features so I can opt out if I don't have an EPG source configured or simply prefer the simpler banner.

**Why this priority**: EPG is opt-in by design because (a) the only provider in 004 requires CTC credentials shipped in 005, and (b) some users may prefer the minimal banner. The toggle must be findable in settings and have an immediate effect without requiring a restart.

**Independent Test**: Open Settings, toggle "Show program guide" off, return to playback, switch channels and verify the banner has no program block; toggle back on, verify the program block reappears on the next channel switch.

**Acceptance Scenarios**:

1. **Given** I am on the Settings screen, **Then** I see a "Show program guide" switch with a brief description.
2. **Given** the "Show program guide" switch is off, **When** I switch channels, **Then** no bottom banner block appears and no EPG network requests are made.
3. **Given** the "Show program guide" switch is off, **When** I open the channel list overlay, **Then** no EPG panel appears next to the channel list.
4. **Given** the "Show program guide" switch is on but no EPG provider is configured (no credentials available in 004 alone), **Then** the EPG surfaces stay hidden as if the switch were off, and the toggle remains visible in settings so the user can change it freely.
5. **Given** I toggle "Show program guide" off while the EPG panel is open, **Then** the channel list overlay re-renders without the EPG column and any cached EPG data is cleared.
6. **Given** the toggle's persisted value, **When** I restart the app, **Then** the toggle retains its previous state.

---

### Edge Cases

- **Device clock incorrect**: The "now playing" highlight is computed from `System.currentTimeMillis()` against program start/end times. If the device clock is significantly off, the wrong program will be highlighted. We accept this — correcting user clocks is out of scope and the upstream API also keys off wall time.
- **EPG timestamps and time zone**: Upstream timestamps from the CTC endpoint are interpreted in the device's local time zone. International providers added later may use UTC and will parse differently within their own provider implementation.
- **Network failure mid-fetch**: One silent retry after 1.5s. If still failing, the banner shows the top block only and the EPG panel shows "Unable to load programs" inline. No modal, no toast, no error sound.
- **Malformed server response**: Logged via Timber and treated identically to a network failure — silent retry once, then inline error in the panel.
- **Programs crossing midnight**: A program that starts at 23:30 today and ends at 00:30 tomorrow appears in today's tab (keyed by start time falling within `[localMidnight, localMidnight + 24h)`).
- **Empty schedule for a date**: The panel shows "No programs scheduled for this date." The banner shows the top block only.
- **App backgrounded mid-fetch**: In-flight fetches are cancelled when the ViewModel scope ends (existing lifecycle behavior). Cache persists for the next foreground.
- **Cache staleness**: Per-channel-per-date entries expire after 60 seconds. The next focus on a stale entry triggers a fresh fetch with a brief loading state in the panel (the banner does not show stale "now playing" during refetch — it transitions to no-program first).
- **Rapid D-pad scrolling**: A 250ms debounce coalesces rapid focus changes into one fetch per "scroll burst." In-flight fetches for earlier focuses are cancelled on each new focus.
- **Multiple channels sharing a channel code**: Cache hit serves the same programs to both. Suboptimal but not incorrect; not worth defending against.

## Requirements *(mandatory)*

### Functional Requirements

**Channel Info Banner (Now Playing / Up Next)**

- **FR-001**: The system MUST display the current and next program below the channel banner (bottom-center) whenever a channel is switched and EPG is enabled and program data is available for that channel.
- **FR-002**: The current-program block MUST display the program title, the time slot (start–end), and a visual progress indicator representing elapsed program time relative to its start/end.
- **FR-003**: The next-program block MUST display the next program's title and its start time.
- **FR-004**: The bottom-center program block MUST share the banner's existing visibility lifecycle: it appears with the top-left banner, auto-hides after 3 seconds together with the top-left banner, and restarts the timer on every new channel switch.
- **FR-005**: When EPG data is not available for the focused channel (no `channelCode`, provider unconfigured, or fetch failed), the system MUST hide the bottom-center block entirely and render the top-left banner exactly as it does today.
- **FR-006**: When EPG data arrives after the banner's auto-hide has elapsed, the system MUST NOT re-display the banner.

**EPG Panel (Channel List Overlay)**

- **FR-007**: When EPG is enabled, the channel list overlay MUST render as two regions: a channel column on the left (preserving current width and behavior) and an EPG panel on the right.
- **FR-008**: The EPG panel MUST display the schedule for the channel that currently has focus in the channel column.
- **FR-009**: The EPG panel MUST provide three date tabs labelled "Yesterday", "Today", and "Tomorrow". "Today" MUST be selected every time the channel list overlay opens, regardless of which tab was last selected in a prior overlay session.
- **FR-010**: D-pad RIGHT from a channel row in the channel column MUST move focus into the EPG panel.
- **FR-011**: D-pad LEFT from inside the EPG panel MUST return focus to the previously focused channel in the channel column.
- **FR-012**: D-pad LEFT from a channel row in the channel column MUST dismiss the overlay (preserving today's behavior). The system MUST disambiguate "dismiss overlay" from "move focus back from EPG" by checking which region currently has focus.
- **FR-013**: The system MUST highlight the program currently airing within the displayed schedule. "Currently airing" is determined by the device's wall-clock time falling within a program's `[start, end)` window, NOT by the server-reported `isLive` flag (which is preserved as data but not used for this highlight).
- **FR-014**: When the focused channel has no EPG data available, the EPG panel MUST display "EPG not available for this channel" and not attempt to fetch.
- **FR-015**: When EPG fetching fails after retry, the EPG panel MUST display "Unable to load programs" inline without showing a modal or toast.
- **FR-016**: When EPG is disabled, the channel list overlay MUST behave exactly as it does today (channel column only, no EPG panel, no fetches).

**Provider Abstraction**

- **FR-017**: The system MUST define an `EpgProvider` interface in the domain layer that the UI consumes without knowing which operator implementation backs it.
- **FR-018**: The system MUST ship one provider implementation in 004: a China Telecom (CTC) provider that authenticates to and fetches programs from the CTC EPG endpoint as documented in `IPTV_AUTH_PROTOCOL.md`.
- **FR-019**: The provider interface MUST expose an `isConfigured` observable signal that is false until credentials are available (always false in 004 alone, intended to flip true in 005). When false, the UI MUST hide all EPG surfaces regardless of the user-facing toggle's position.
- **FR-020**: Provider error returns MUST be data (Result-style), not thrown exceptions, so consumers handle them as ordinary state transitions.

**Settings**

- **FR-021**: The system MUST provide a user-facing toggle "Show program guide" in the Settings screen, with a short description explaining its purpose.
- **FR-022**: The "Show program guide" toggle MUST default to off.
- **FR-023**: The toggle's value MUST persist across app restarts.
- **FR-024**: Toggling EPG off MUST take effect immediately: hide the bottom banner block, hide the EPG panel if open, clear any cached EPG data, and prevent further network requests until re-enabled.
- **FR-025**: Toggling EPG on MUST take effect immediately and trigger a fetch for the currently active channel (if a provider is configured).

**Performance & Behavior**

- **FR-026**: A 250ms debounce MUST be applied to channel-focus changes in the EPG panel so that rapid D-pad scrolling produces at most one fetch per scroll burst.
- **FR-027**: When a new channel focus event occurs while a previous fetch is still in flight, the previous fetch MUST be cancelled.
- **FR-028**: Successful fetches MUST be cached per (channel, date) for 60 seconds; subsequent reads within the TTL MUST serve from cache without a network call.
- **FR-029**: Failed fetches MUST trigger one silent retry after 1.5 seconds before surfacing an error.
- **FR-030**: The system MUST NOT issue any EPG network request when the toggle is off OR the provider is not configured.

**Observability**

- **FR-031**: The system MUST log EPG fetches and their outcomes via Timber at debug level in debug builds (per `001-iptv-player`'s FR-038–FR-040 logging policy).
- **FR-032**: The system MUST NOT log credentials, raw authenticator strings, session cookies, or any other sensitive material.

### Key Entities

- **Program**: A single program slot — opaque program code, title, start time, end time, isLive flag (server-reported), isReplayable flag. Time fields are time-zone-aware instants normalized at the provider boundary.
- **EpgProvider**: An interface exposing `fetchPrograms(channelCode, dateOffset)` returning a Result, plus an `isConfigured` observable.
- **EpgPanelState**: The state consumed by the EPG panel UI — focused channel, selected date offset, programs list, loading flag, error flag.
- **UserPreferences** (extended): Existing entity gains one new field, `epgEnabled` (Boolean, default false).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: When a channel is switched and program data is cached, the bottom-center program block appears together with the channel banner within 500ms of the channel becoming active.
- **SC-002**: When a channel is switched and program data must be fetched, the program block appears within 3 seconds on a healthy network (faster cases are common; this is the worst-case acceptance bound). If the fetch exceeds 3 seconds the banner has already auto-hidden and the program block does not appear (no late pop-in).
- **SC-003**: A user can open the channel list, scroll to any channel, and see that channel's EPG within 500ms on cache hit or 3 seconds on cache miss.
- **SC-004**: A user can complete a focus transfer between the channel column and the EPG panel in a single D-pad press, with the previously focused channel restored on return.
- **SC-005**: Disabling EPG in settings results in zero EPG-related network requests, verifiable by inspecting network telemetry (or a packet capture in test).
- **SC-006**: With EPG enabled, rapidly scrolling D-pad through 100 channels in 5 seconds results in at most 5 EPG fetches (one per ~1-second scroll burst at the 250ms debounce).
- **SC-007**: When EPG is disabled or the provider is unconfigured, the channel info banner and channel list overlay behave identically to their behavior before this spec (regression-tested by existing UI tests).
- **SC-008**: 100% of user-facing EPG strings (toggle label, description, empty-state messages, error messages, date tab labels) are localized via `strings.xml` per `001-iptv-player`'s FR-037.

## Assumptions

- Channels imported from sources without an EPG mapping (M3U8 in 004) have no `channelCode`. The bottom block and EPG panel for those channels render the "EPG not available" state.
- The CTC EPG endpoint returns timestamps interpretable in the device's local time zone. International / XMLTV providers added later will encode this assumption in their own provider implementation.
- The user accepts that EPG data is only as accurate as the upstream operator publishes; the app does not validate or correct schedule data.
- Device wall clock is approximately correct (within a few minutes). Severely incorrect clocks will mis-highlight "now playing" but will not crash or corrupt state.

## Out of Scope (this spec)

The following are explicitly deferred to follow-up specs:

- **CTC login UI and credential storage** — User ID, password, STB profile, IP, MAC entry fields; secure storage in EncryptedSharedPreferences or Keystore. Deferred to `005-ctc-iptv-import`.
- **"Test connection and import channels" action** — authenticating to CTC, fetching the channel list, populating Room with channels and their `channelCode`. Deferred to `005-ctc-iptv-import`.
- **Adding `channelCode` to `ChannelEntity`** — the Room schema change required to associate channels with EPG records. Deferred to `005-ctc-iptv-import` so this spec's PR has zero database-migration risk.
- **Alternative EPG providers** — XMLTV file import, other operators' protocols. The `EpgProvider` abstraction is designed to admit these without UI changes, but no second implementation ships in 004.
- **Program details screen** — pressing OK on a program in the panel does nothing in 004. A full program details view (description, cast, record action) is a future feature.
- **Time-shift / replay actions** — `isReplayable` is preserved in the `Program` model but no UI uses it yet. Replay/DVR navigation is a future feature.
- **EPG week view** — the spec covers Yesterday / Today / Tomorrow only, matching what the CTC API reliably returns.
- **Pre-fetching adjacent channels' EPG** — like FCC pre-buffers streams, we could pre-fetch program data for neighbors. Out of scope until measured to be needed.
- **Cross-feature interaction with FCC** — FCC is in flight on `feature/fcc`. Interaction testing happens when both have landed; nothing in this spec depends on FCC.

## Dependencies

- **`001-iptv-player`** — base playback, channel list, channel info banner, settings infrastructure, DataStore-backed preferences, i18n via `strings.xml`. This spec extends those surfaces.
- **`IPTV_AUTH_PROTOCOL.md`** — wire-format protocol for the CTC EPG endpoint. The CTC provider implementation follows this document exactly; deviations must be reflected back in the doc.
- **`iptv_client.py`** (in `~/Documents/itv-reverse/`) — reference Python implementation used to capture test fixtures (3DES authenticator golden values, login transcripts, `prevue_list.jsp` responses). Not redistributed; used only as a fixture source.

## Technical Decisions

- **Provider interface in `domain/repository/`** following the project's existing dependency-inversion pattern (interface in domain, impl in data).
- **CTC provider implementation in `data/epg/`** as a feature-cohesive package, alongside the auth client it depends on. Distinct from `data/repository/` because the auth client is plumbing rather than a repository.
- **`Program` timestamps stored as `kotlinx.datetime.Instant`** (or `java.time.Instant`) — parsed at the provider boundary; the rest of the app never handles upstream timestamp strings.
- **Cache: in-memory `ConcurrentHashMap`** with 60-second TTL and a bound of ~100 (channel, date) entries (LRU). No persistent EPG cache in 004 — schedule freshness matters more than offline access for now.
- **Networking: shared `OkHttpClient`** (already a transitive dep via Media3) with a per-provider `CookieJar` for session management. No new networking library introduced.
- **Threading: `Dispatchers.IO` for fetches and parsing**; UI state always emitted on the main dispatcher via existing StateFlow plumbing.
- **Testing: `MockWebServer`** for provider tests, `MockK` + Turbine for ViewModel tests, captured Python-side fixtures for the 3DES authenticator byte-exact assertion.
- **Localization: all new user-facing strings in `strings.xml`** with Chinese Simplified translations alongside, per `001-iptv-player`'s i18n requirements.
