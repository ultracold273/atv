# Feature Specification: Home Proxy Pairing

**Feature Branch**: `008-home-proxy-pairing`  
**Created**: 2026-06-27  
**Status**: Draft  
**Input**: Replace manual Home Proxy access-token entry with a TV-friendly pairing-code flow that lets an administrator approve the Android TV client through the proxy server admin API.

## Overview

Home Proxy mode currently requires the user to type an access token on the Android TV device. That is awkward on TVs because remote-control text entry is slow, error-prone, and hostile to high-entropy token strings.

This feature adds an optional pairing flow to the existing Home Proxy tab. The user enters or confirms the proxy base URL, selects a button to generate a short pairing code, and approves that code from the proxy server's admin surface. After approval, the Android client receives a scoped access token automatically, stores it through the existing secure proxy settings path, and can immediately use the current Home Proxy channel and EPG APIs.

Manual token entry remains available as an advanced fallback for administrators who prefer provisioning tokens directly.

## User Scenarios & Testing

### User Story 1 - Pair a TV Without Typing a Token (Priority: P1)

As a Home Proxy user setting up Android TV, I want to generate a short pairing code so I can approve the device from the proxy server admin surface instead of typing a long token with the TV remote.

**Independent Test**: Select Home Proxy mode, enter a MockWebServer proxy URL, generate a pairing code, approve the session through mocked polling responses, and verify the ViewModel saves proxy settings with the returned access token.

**Acceptance Scenarios**:

1. **Given** Home Proxy mode is selected and Proxy URL is valid, **When** I select Generate pairing code, **Then** the app creates a pairing session with the proxy server and shows a large short code plus expiration state.
2. **Given** a pairing session is pending, **When** the admin approves the matching code on the proxy server, **Then** the Android client receives the access token automatically and stores complete proxy settings.
3. **Given** pairing succeeds, **When** the app saves the token, **Then** existing Home Proxy import and EPG requests continue using `Authorization: Bearer <accessToken>` without any API changes.
4. **Given** pairing succeeds, **When** the user chooses Test & import or the app auto-imports, **Then** channels import through the existing Home Proxy path.

### User Story 2 - Handle Pairing Failure States Clearly (Priority: P1)

As a user, I want pairing failures to be understandable and recoverable from the TV screen.

**Independent Test**: Simulate rejected, expired, unauthorized, malformed, and network-failure pairing responses and verify no token is saved and the UI exposes a retry path.

**Acceptance Scenarios**:

1. **Given** a pairing code expires before approval, **When** polling receives `expired` or the local expiry time passes, **Then** the app stops polling, keeps the existing token unchanged, and allows generating a new code.
2. **Given** the admin rejects the pairing request, **When** polling receives `rejected`, **Then** the app stops polling, keeps the existing token unchanged, and displays rejected status.
3. **Given** the proxy server is unreachable, **When** pairing creation or polling fails, **Then** the app reports a network failure without clearing any existing Home Proxy settings.
4. **Given** the user leaves the screen or cancels pairing, **When** a session is pending, **Then** polling stops and no token is saved from later responses.

### User Story 3 - Preserve Manual Token Provisioning (Priority: P2)

As an administrator, I want manual token entry to remain available so I can recover from pairing-server issues or use an externally provisioned token.

**Independent Test**: Enter Proxy URL and Access token manually, run Test & import, and verify behavior remains identical to the current Home Proxy flow.

**Acceptance Scenarios**:

1. **Given** Home Proxy mode is selected, **When** I enter Proxy URL and Access token manually, **Then** Test & import remains enabled as it is today.
2. **Given** a pairing flow is pending, **When** I manually edit the access token or start manual import, **Then** pending pairing is cancelled or ignored so stale polling cannot overwrite user input.
3. **Given** an existing valid token is stored, **When** pairing fails, **Then** the existing token remains available.

### User Story 4 - Admin Approval Uses a Narrow Contract (Priority: P2)

As a proxy server administrator, I want a small admin approval API so pairing can be implemented in a web UI, CLI, or automation without exposing provider credentials to the Android client.

**Independent Test**: Verify the Android client only calls client pairing endpoints, while approval is represented by documented admin endpoints that require admin authorization.

**Acceptance Scenarios**:

1. **Given** a pending pairing session exists, **When** the admin lists pending sessions, **Then** the response includes session ID, pairing code, device metadata, and expiration.
2. **Given** an admin approves a session, **When** the pairing code matches and the session is unexpired, **Then** the proxy server issues a scoped client token.
3. **Given** an admin rejects a session, **When** the Android client polls, **Then** the client sees rejected status and no token.

## Functional Requirements

### Home Proxy Pairing UI

- **FR-001**: Home Proxy mode MUST expose a Generate pairing code action when Proxy URL is a valid HTTP or HTTPS URL.
- **FR-002**: The pairing action MUST be reachable by TV remote navigation and MUST NOT require typing an access token first.
- **FR-003**: While pairing is pending, the UI MUST show the pairing code in a TV-readable format, expiration status, and pending/approved/rejected/expired/error state.
- **FR-004**: The pairing code SHOULD be displayed in grouped digits, such as `482 913`, while preserving the raw code value for API matching.
- **FR-005**: The UI MUST offer a way to cancel or replace a pending pairing session.
- **FR-006**: Manual Proxy URL and Access token entry MUST remain available.
- **FR-007**: The existing Test & import action MUST continue to work with manually entered or paired tokens.
- **FR-008**: All new user-visible strings MUST be localized in English and Chinese resources.

### Pairing Client Behavior

- **FR-009**: The app MUST create a pairing session by calling `POST /api/v1/pairing/sessions` on the configured proxy base URL.
- **FR-010**: The create-session request MUST include device metadata sufficient for an admin to identify the TV, without including provider credentials or existing access tokens.
- **FR-011**: The app MUST generate a per-session `clientNonce` with at least 128 bits of entropy and keep it in memory only for the active pairing session.
- **FR-012**: The app MUST poll `GET /api/v1/pairing/sessions/{sessionId}` until approved, rejected, expired, cancelled, or failed.
- **FR-013**: Polling MUST respect the server-provided `pollIntervalSeconds` when valid, with a client-side minimum interval to avoid hammering the proxy.
- **FR-014**: Polling MUST stop when the ViewModel is cleared, the user starts a new pairing session, or the user cancels pairing.
- **FR-015**: The app MUST save the returned token only when the polling response status is `approved`, the response token is complete, and the locally configured proxy URL is still valid.
- **FR-016**: Pairing failure MUST NOT clear or overwrite an existing Home Proxy token.
- **FR-017**: Pairing success MUST store the returned token in the existing secure Home Proxy settings store.
- **FR-018**: Pairing success SHOULD update the active source mode to `HOME_PROXY` after the token is saved.
- **FR-019**: Pairing success MAY trigger the existing Home Proxy Test & import flow automatically if the UI makes that behavior explicit; otherwise it MUST leave Test & import available.

### Proxy Server Contract

- **FR-020**: The proxy server MUST generate short-lived pairing sessions and expose a short human-readable pairing code.
- **FR-021**: Pairing codes MUST expire quickly; the recommended default is 5 minutes.
- **FR-022**: Pairing codes MUST NOT be access tokens and MUST NOT grant API access by themselves.
- **FR-023**: Admin approval MUST require admin authorization on the proxy server.
- **FR-024**: Approved client tokens SHOULD be scoped to the APIs the Android client needs, initially `channels:read` and `epg:read`.
- **FR-025**: The proxy server SHOULD rate-limit pairing creation, polling, and admin approval attempts.
- **FR-026**: The proxy server SHOULD bind approval to the pending session and client nonce so approving a displayed code cannot be replayed into a different session.
- **FR-027**: The proxy server MUST NOT expose provider credentials through pairing responses.

### Security & Privacy

- **FR-028**: The app MUST NOT log access tokens, client nonces, or full pairing approval responses.
- **FR-029**: The app MAY log non-secret pairing state transitions at debug level, such as pending or expired, without logging tokens or nonce values.
- **FR-030**: Pairing tokens MUST be persisted only through the same encrypted storage path used for existing Home Proxy access tokens.
- **FR-031**: Pairing requests over HTTP are acceptable for trusted LAN proxy URLs, but HTTPS MUST remain supported.
- **FR-032**: Pairing MUST fail closed: malformed approved responses, missing token values, or invalid local proxy URLs MUST not update stored settings.

### Testing

- **FR-033**: Unit tests MUST cover successful pairing creation, polling, approval, and secure settings save behavior.
- **FR-034**: Unit tests MUST cover expiry, rejection, cancellation, malformed JSON, unauthorized responses, network errors, and stale responses after cancellation.
- **FR-035**: ViewModel tests MUST verify UI state transitions for idle, creating, pending, approved, rejected, expired, cancelling, and error states.
- **FR-036**: Existing Home Proxy manual token import tests MUST continue to pass unchanged or with only UI-state updates.
- **FR-037**: Proxy client tests MUST verify request paths, headers, JSON body fields, and response parsing using MockWebServer.

## Client Pairing API Contract

Create a pairing session:

```http
POST /api/v1/pairing/sessions
Content-Type: application/json
```

Request:

```json
{
  "deviceName": "Living Room ATV",
  "deviceType": "android_tv",
  "appId": "com.example.atv",
  "appVersion": "1.0.0",
  "clientNonce": "base64url-random-32-bytes"
}
```

Response:

```json
{
  "sessionId": "ps_01J00000000000000000000000",
  "pairingCode": "482913",
  "expiresAt": "2026-06-27T12:15:00Z",
  "pollIntervalSeconds": 2
}
```

Poll a pairing session:

```http
GET /api/v1/pairing/sessions/{sessionId}
X-Client-Nonce: base64url-random-32-bytes
```

Pending response:

```json
{
  "status": "pending",
  "expiresAt": "2026-06-27T12:15:00Z",
  "pollIntervalSeconds": 2
}
```

Approved response:

```json
{
  "status": "approved",
  "accessToken": "local-client-token",
  "tokenType": "Bearer"
}
```

Rejected response:

```json
{
  "status": "rejected"
}
```

Expired response:

```json
{
  "status": "expired"
}
```

Structured error response:

```json
{
  "error": {
    "code": "pairing_rate_limited",
    "message": "Too many pairing attempts; try again later"
  }
}
```

## Admin API Contract

List pending sessions:

```http
GET /admin/api/v1/pairing/sessions?status=pending
X-Admin-Password: {admin-password}
```

Response:

```json
{
  "data": [
    {
      "sessionId": "ps_01J00000000000000000000000",
      "pairingCode": "482913",
      "deviceName": "Living Room ATV",
      "deviceType": "android_tv",
      "appId": "com.example.atv",
      "appVersion": "1.0.0",
      "createdAt": "2026-06-27T12:10:00Z",
      "expiresAt": "2026-06-27T12:15:00Z"
    }
  ]
}
```

Approve a session:

```http
POST /admin/api/v1/pairing/approve
X-Admin-Password: {admin-password}
Content-Type: application/json
```

Request:

```json
{
  "pairingCode": "482913",
  "deviceLabel": "Living Room ATV",
  "scopes": ["channels:read", "epg:read"]
}
```

Response:

```json
{
  "status": "approved",
  "clientId": "client_01J00000000000000000000000"
}
```

Reject a session:

```http
POST /admin/api/v1/pairing/reject
X-Admin-Password: {admin-password}
Content-Type: application/json
```

Request:

```json
{
  "pairingCode": "482913"
}
```

Response:

```json
{
  "status": "rejected"
}
```

## Key Entities

- **ProxyPairingSession**: Active client-side pairing attempt containing session ID, displayed code, expiry, poll interval, and in-memory client nonce.
- **ProxyPairingStatus**: UI/domain state for idle, creating, pending, approved, rejected, expired, cancelled, and error.
- **ProxyPairingClient**: HTTP client for pairing creation and polling endpoints.
- **ProxyPairingRequestDto**: Device metadata and client nonce sent to the proxy server.
- **ProxyPairingResponseDto**: Session metadata returned when pairing starts.
- **ProxyPairingPollResponseDto**: Status and optional approved token payload returned while polling.
- **HomeProxySourceSettings**: Existing proxy URL and access-token settings that become complete after pairing approval.

## Success Criteria

- **SC-001**: A user can provision Home Proxy access on Android TV by typing only the proxy URL and approving a short code elsewhere.
- **SC-002**: The app stores paired tokens through the same secure settings path as manually entered tokens.
- **SC-003**: Existing Home Proxy channel and EPG APIs continue to work without contract changes after pairing.
- **SC-004**: Pairing failure, expiry, rejection, cancellation, and network errors never erase an existing token.
- **SC-005**: Automated tests cover pairing client parsing, ViewModel state transitions, and secure-save behavior.

## Assumptions

- The Android TV can reach the Home Proxy base URL over the LAN.
- The proxy server has or will have an authenticated admin API or admin UI.
- The proxy server is responsible for generating and storing client access tokens.
- The app already has a secure storage path for Home Proxy access tokens through `ChannelSourceSettingsStore`.
- The proxy server will continue supporting bearer-token authentication for `/api/v1/channels` and `/api/v1/epg/day`.

## Out of Scope

- Building the proxy server admin UI in this Android repo.
- Migrating provider credentials from Android TV to the proxy server.
- QR-code pairing.
- LAN proxy auto-discovery through mDNS, SSDP, or broadcast discovery.
- Token revocation management UI on Android TV.
- Multi-admin approval workflows.

## Technical Decisions

- **Additive enrollment layer**: Pairing only obtains and stores the existing Home Proxy token shape; channel import and EPG clients remain unchanged.
- **Manual fallback retained**: The current token field remains available for power users and recovery.
- **Polling over push**: The Android client polls because LAN proxies may not support push channels and polling is simple to test with MockWebServer.
- **In-memory nonce**: The nonce protects the active session without adding persistent secret lifecycle complexity.
- **Fail closed**: Approval responses must be complete and valid before stored settings are updated.
