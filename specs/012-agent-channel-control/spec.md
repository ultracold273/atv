# Feature Specification: Agent Channel Control

**Feature Branch**: `codex/012-agent-channel-control`

**Created**: 2026-10-31

**Status**: Implemented; pending review

**Input**: Let an external agent retrieve the complete ATV channel catalog, resolve the viewer's request itself, and switch ATV to the selected channel number. Limit v1 to these two operations; omit the playback-status interface and app-side channel-name resolution.

## Overview

ATV will expose a small, authenticated local-network control API while the app is in the foreground. An external agent retrieves the channel catalog, interprets a request such as "I want to watch CCTV 1", and submits the number of a channel actually present in that catalog. ATV validates the request and hands it to the same playback workflow used by its remote-control UI.

The API acknowledges a switch request without reporting whether the stream subsequently starts playing. A successful response means the request was handed to playback, not that playback succeeded. Stream loading and errors remain visible through the existing TV UI. This distinction makes a third, playback-status interface unnecessary for the agreed v1 scope.

## Scope and Responsibilities

| Component | Responsibility |
|-----------|----------------|
| External agent | Understand speech/text, choose among the returned channels, handle ambiguous names or user preferences, and decide when to refresh its catalog. |
| ATV | Return the complete current channel catalog, validate a selected number against its catalog version, authorize the caller, and dispatch the switch. |
| Viewer / existing playback UI | Observe actual playback, buffering, and stream errors. |

The only v1 application API operations are:

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/v1/channels` | Return all current channels and their catalog version. |
| POST | `/api/v1/playback/channel` | Request switching to a channel number from that version. |

## User Scenarios & Testing

### User Story 1 - Retrieve the Channel Catalog (Priority: P1)

As an agent developer, I want all available channel numbers and descriptive metadata so my agent can select a channel without ATV implementing language understanding.

**Independent Test**: Seed a catalog with non-contiguous numbers, duplicate names, and a missing group; request the catalog with valid authorization and verify every channel is returned once in numeric order.

**Acceptance Scenarios**:

1. **Given** a catalog containing channel 17 named "CCTV-1 综合", **When** the agent retrieves the catalog, **Then** the response includes that exact number and name together with the catalog version.
2. **Given** no configured channels, **When** the catalog is requested, **Then** ATV returns a successful response with an empty channel array and a valid version.
3. **Given** two channels with the same name, **When** the catalog is requested, **Then** both records are returned with their distinct numbers; ATV does not choose between them.
4. **Given** unchanged channel data, **When** the catalog is retrieved repeatedly, **Then** its version remains unchanged.

### User Story 2 - Request a Switch by Number (Priority: P1)

As an agent developer, I want to submit the number chosen from the catalog so ATV can perform the same channel switch as a local viewer.

**Independent Test**: With the playback screen ready and a fake player, retrieve a catalog and request one of its numbers; verify the selected channel reaches the existing playback workflow and the response acknowledges dispatch without claiming playback success.

**Acceptance Scenarios**:

1. **Given** a current catalog and an active playback screen, **When** the agent submits a listed number with that catalog version, **Then** ATV dispatches the switch and returns `202 Accepted`.
2. **Given** a dispatched switch, **When** the stream later fails, **Then** the existing TV error UI handles the failure; the earlier response does not imply a successful stream or expose an operation-status resource.
3. **Given** a channel number has been reassigned after import, **When** a request carries the old version, **Then** ATV returns `409 CATALOG_CHANGED` and does not switch channels.
4. **Given** the current version but a number absent from its catalog, **When** the request is submitted, **Then** ATV returns `404 CHANNEL_NOT_FOUND` without changing playback.
5. **Given** settings or source setup is open instead of the playback screen, **When** a valid switch request arrives, **Then** ATV returns `503 PLAYBACK_UNAVAILABLE` and does not navigate away or queue a later switch.

### User Story 3 - Enable and Disable Local Control (Priority: P1)

As the person configuring the TV, I want to explicitly enable local control and provision a controller so normal viewing remains usable without external services.

**Independent Test**: Enable control through the TV settings, configure a test client using the displayed connection details, exercise authorized and unauthorized requests, rotate the credential, and disable control.

**Acceptance Scenarios**:

1. **Given** a fresh install, **When** ATV starts, **Then** the control listener is disabled until enabled in settings.
2. **Given** an enabled listener, **When** a request has a missing or invalid credential, **Then** it returns `401 UNAUTHORIZED` before accessing the catalog or changing playback.
3. **Given** an authorized controller, **When** the owner rotates the control credential, **Then** subsequent requests with the previous credential fail authorization.
4. **Given** an enabled listener, **When** ATV leaves the foreground or control is disabled, **Then** it stops accepting requests and does not retain work for a later app launch.
5. **Given** the selected port cannot be bound, **When** control is enabled, **Then** settings displays an actionable error and ordinary TV playback remains available.

### Edge Cases

- Channel deletion, editing, import, or clear-all occurs between catalog retrieval and a switch.
- Import replacement must not expose the temporary gap between deleting and inserting playlist records.
- The app leaves the foreground, the playback screen is disposed, or the credential is rotated during request handling.
- Malformed JSON, a missing version, a fractional/negative/overflowing channel number, or an oversized request body arrives.
- Multiple controllers or the physical remote request switches close together.
- The connection drops after dispatch; the client cannot determine actual playback from v1 and must not describe a timeout as a definite failure or success.
- The device's LAN address changes or the listener cannot start; connection details must reflect the current reachable address.

## Functional Requirements

### Catalog

- **FR-001**: ATV MUST expose exactly the two application API operations listed above. Catalog retrieval MUST return the full catalog, without name search, filtering, pagination, or alias resolution in v1.
- **FR-002**: Each returned channel MUST contain `number`, `name`, and nullable `groupTitle`. Numbers MUST be unique positive integers and records MUST be sorted by number ascending. Preserve configured names and groups without inferring aliases or merging records.
- **FR-003**: The catalog response MUST include a non-empty opaque `catalogVersion` of at most 128 characters, including for an empty catalog. The version MUST be stable for identical catalog contents and change when the stored number-to-channel mapping, descriptive metadata, or underlying stream URL changes.
- **FR-004**: A response's records and version MUST describe one atomic snapshot. Playlist replacement MUST be atomic for readers. A versioned switch MUST validate and resolve its channel consistently with catalog mutations, so an import cannot silently redirect a validated number to a different channel before dispatch.
- **FR-005**: The response MUST expose selection metadata only. Stream URLs, IPTV credentials, Home Proxy tokens, and control credentials MUST NOT appear in catalog responses or errors.

### Switch Request and Acknowledgement

- **FR-006**: The switch body MUST contain only `number` and `catalogVersion`. A number MUST be an integer from 1 through 2147483647; a version MUST be a non-blank string of at most 128 characters. Invalid or unknown fields MUST produce `400 INVALID_REQUEST`.
- **FR-007**: After authorization and input validation, ATV MUST check the version before resolving the number. A stale version MUST return `409 CATALOG_CHANGED`; an absent number in the current version MUST return `404 CHANNEL_NOT_FOUND`. Neither case may change playback.
- **FR-008**: A valid request MUST reuse the local channel-switch workflow, including current source-mode stream URL resolution, selected-channel UI state, last-watched channel persistence, channel information, and EPG updates. The network handler MUST NOT call ExoPlayer directly or duplicate those rules.
- **FR-009**: ATV MUST return `202 Accepted` only after a currently available playback handler accepts and dispatches the switch. The acknowledgement MUST echo the requested number and accepted catalog version. It MUST NOT claim that media is playing, wait for the first frame, or create a pollable operation.
- **FR-010**: Failures detected before dispatch MUST return a structured error. Stream failures after dispatch MUST continue through the existing playback error UI. No playback-state fields, status endpoint, callbacks, event stream, or WebSocket updates are part of v1.
- **FR-011**: Switching MUST require the playback screen to be active and ready. Otherwise return `503 PLAYBACK_UNAVAILABLE`. Remote acceptance MUST dismiss the channel picker/number pad and refresh normal channel information without changing full-screen settings or setup navigation.
- **FR-012**: Channel dispatch MUST be serialized with local controls. Excess concurrent remote switch requests MUST receive `503 CONTROL_BUSY` rather than accumulate an unbounded or persistent queue. Requests MUST NOT be replayed automatically after restart or foreground restoration. Repeated requests do not carry an exactly-once or playback-completion guarantee.

### Listener, Authorization, and TV Settings

- **FR-013**: Local control MUST be opt-in and disabled by default. When enabled, bind a LAN-reachable listener on port `8765` while ATV is in the foreground. Stop it on background/exit or disablement; restart on foreground entry only if still enabled. Binding failures MUST be shown in settings without disrupting ordinary playback.
- **FR-014**: Connection setup MUST happen through TV settings, without adding an enrollment API. Show the current base URL, a locally revealable controller credential, and the server's certificate/public-key fingerprint. V1 uses one dedicated control credential per ATV installation; rotating it invalidates previous clients. It MUST be separate from Home Proxy and provider credentials.
- **FR-015**: Both operations MUST require `Authorization: Bearer <control-token>`. Generate the token using a cryptographically secure source with at least 256 bits of entropy; encrypt persistent secrets at rest and redact them from logs. Authorization and foreground availability MUST be rechecked before a switch is dispatched if either changed during processing.
- **FR-016**: Use HTTPS for the LAN API to encrypt the controller credential in transit, in accordance with the project constitution. Support an installation-specific certificate and out-of-band trust/pinning using the details shown on the TV. Clients MUST verify that server identity before sending credentials; production setup MUST NOT depend on disabling verification without pinning.
- **FR-017**: Socket operations, TLS, serialization, and database reads MUST NOT block the UI thread. Bound connection count and request processing time; limit switch request bodies to 4 KiB. Return `413 PAYLOAD_TOO_LARGE` for larger bodies and `415 UNSUPPORTED_MEDIA_TYPE` for non-JSON switch bodies.
- **FR-018**: Settings and connection/error feedback MUST be operable with the D-pad and localized in English and Chinese. Existing playback, source setup, and remote navigation MUST remain usable when control is disabled or fails.
- **FR-019**: Keep the current Android API baseline for this feature. Use existing `INTERNET` permission; account for the local-network runtime permission when a future target SDK upgrade makes it mandatory. Foreground-only v1 MUST NOT introduce a background keepalive service, wake lock, or television wake-up capability.

## API Contract

The base URL is `https://<tv-lan-address>:8765`. Both operations require the bearer credential and return JSON with `Cache-Control: no-store`. Credentials are never supplied as URL parameters. Application error responses share the envelope below and contain no internal exception details.

### GET /api/v1/channels

No query parameters or request body are supported. Example `200 OK` response:

```json
{
  "catalogVersion": "catalog-v1-example",
  "channels": [
    { "number": 17, "name": "CCTV-1 综合", "groupTitle": "央视" },
    { "number": 21, "name": "CCTV-13 新闻", "groupTitle": null }
  ]
}
```

The version is an equality token, not a channel identifier or playback state. An agent may retain a snapshot for selection and must retrieve a fresh one after `CATALOG_CHANGED`.

### POST /api/v1/playback/channel

Send `Content-Type: application/json`. Example request:

```json
{
  "number": 17,
  "catalogVersion": "catalog-v1-example"
}
```

Example `202 Accepted` response, after dispatch to playback:

```json
{
  "status": "accepted",
  "number": 17,
  "catalogVersion": "catalog-v1-example"
}
```

An external assistant may say "Requested CCTV-1" or "正在切换到央视一套" based on this response. It cannot say that playback has been confirmed. There is no operation ID, status URL, or follow-up polling requirement.

### Errors

Example `409 Conflict` response:

```json
{
  "error": {
    "code": "CATALOG_CHANGED",
    "message": "Retrieve the channel catalog again before requesting a switch."
  }
}
```

| HTTP status | Error code | Meaning / caller action |
|-------------|------------|-------------------------|
| 400 | `INVALID_REQUEST` | Fix malformed JSON, unsupported fields/parameters, or invalid values. |
| 401 | `UNAUTHORIZED` | Missing/invalid token; provision current connection credentials. |
| 404 | `CHANNEL_NOT_FOUND` | Number is absent from the submitted current catalog. |
| 409 | `CATALOG_CHANGED` | Retrieve the complete catalog and select again. |
| 413 | `PAYLOAD_TOO_LARGE` | Reduce the switch body to at most 4 KiB. |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | Send a JSON switch body. |
| 503 | `PLAYBACK_UNAVAILABLE` | ATV is reachable, but its playback screen is not ready. |
| 503 | `CONTROL_BUSY` | Another switch is being dispatched; no new work was queued. |
| 500 | `INTERNAL_ERROR` | Unexpected pre-dispatch failure; sanitized message only. |

Unknown paths return `404 NOT_FOUND`; unsupported methods on known paths return `405 METHOD_NOT_ALLOWED` with an appropriate `Allow` header. An app that is backgrounded or disabled normally has no listener, so connection failure rather than an HTTP response is expected. Loss of a response after dispatch leaves the outcome unknown; the API does not provide completion tracking.

## Key Entities

- **Channel catalog snapshot**: One version plus every currently configured channel's number, name, and group.
- **Switch request**: A number chosen by the external agent and the version from which it was chosen.
- **Switch acknowledgement**: Confirmation of dispatch for that request; not a media-state report.
- **Local control settings**: Enabled preference, dedicated controller credential, and installation-specific TLS identity; all managed on the TV.

## Success Criteria

- **SC-001**: A controller can retrieve a seeded catalog and request a listed channel using only the two documented operations. Fake-player assertions or observation of the TV verify the resulting switch; no status endpoint is needed.
- **SC-002**: Catalog tests return every stored record exactly once, preserve configured metadata, and cover empty catalogs, duplicate names, non-contiguous numbers, and replacement imports.
- **SC-003**: Stale versions, unknown numbers, invalid input, unauthorized callers, and unavailable playback produce the specified result with no unintended switch. Tests include a mutation racing with dispatch.
- **SC-004**: An accepted response is produced without awaiting stream buffering or playback success. An injected later player failure appears on the TV and does not change the documented acknowledgement semantics.
- **SC-005**: Enable/disable, background/foreground transitions, token rotation, TLS identity verification, and port-binding failure are covered by integration or instrumented tests.
- **SC-006**: On the recorded test device, warm local requests for a catalog of 1,000 channels and switch dispatch meet the constitution's 200 ms p95 standard-operation target, excluding connection/TLS establishment and media loading. Playback remains responsive during requests.
- **SC-007**: Existing channel switching, EPG, and settings tests remain green; new implementation code meets the constitution's coverage thresholds. Implementation verification is recorded in [verification.md](verification.md).

## Out of Scope

- A third playback-status interface, command polling, media-state feedback, callbacks, or event subscriptions.
- Channel-name search, aliases, fuzzy matching, speech recognition, LLM inference, or agent implementation inside ATV.
- New globally stable channel IDs; v1 uses existing channel numbers with a catalog version.
- Remote channel editing/import, source changes, EPG queries, volume control, or arbitrary media URLs.
- Network pairing/admin endpoints, per-client credential inventory, discovery endpoints, or a separately hosted agent/MCP service.
- Background control, wake-on-LAN, TV power control, remote app launch, deep links, and MediaSession integration.

## Dependencies and References

- Existing [channel model](../../app/src/main/kotlin/com/example/atv/domain/model/Channel.kt), [channel repository](../../app/src/main/kotlin/com/example/atv/data/repository/ChannelRepositoryImpl.kt), and [playback workflow](../../app/src/main/kotlin/com/example/atv/ui/screens/playback/PlaybackViewModel.kt).
- The [project constitution](../../.specify/memory/constitution.md), especially authorization, encryption at rest/in transit, performance, and test coverage.
- [RFC 9110, 202 Accepted](https://www.rfc-editor.org/rfc/rfc9110.html#section-15.3.3) defines an acknowledgement that does not establish completed processing.
- Android documents [server sockets](https://developer.android.com/reference/java/net/ServerSocket), [process lifecycle](https://developer.android.com/guide/components/activities/process-lifecycle), and [local-network permissions](https://developer.android.com/privacy-and-security/local-network-permission). The latter requires runtime local-network access for apps targeting SDK 37+ on Android 17; current target SDK 35 does not require that upgrade for this feature.
