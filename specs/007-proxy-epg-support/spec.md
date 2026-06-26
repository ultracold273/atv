# Feature 007: Proxy EPG Support

## Summary

Home Proxy mode shall support the same program-guide surfaces that Direct CTC mode already supports. Android clients must fetch EPG data from the LAN proxy when the source mode is `HOME_PROXY`, while keeping Direct CTC and M3U8 behavior unchanged.

## User Stories

### Story 1: Watch EPG Through Home Proxy

As an Android TV user in Home Proxy mode, I want the current/next banner and guide panel to show program data without storing IPTV provider credentials on the client.

Acceptance criteria:

- Given Home Proxy mode has a complete proxy URL/token and imported channels with `channelCode`, when playback starts, then the banner EPG loads through the proxy.
- Given the channel list is open, when focus settles on a channel with `channelCode`, then the guide panel loads that channel/day through the proxy.
- Given the proxy rejects the token or is unreachable, then playback remains usable and EPG surfaces show the existing failure/empty states.

### Story 2: Preserve Direct CTC Behavior

As a user in Direct CTC mode, I want the existing client-side CTC EPG flow to keep working.

Acceptance criteria:

- Direct CTC mode continues to use `CtcEpgProvider` and stored IPTV credentials.
- M3U8 mode keeps EPG unavailable unless a future playlist EPG feature is added.

## Functional Requirements

- **FR-001**: The app MUST route `EpgProvider.fetchPrograms(channelCode, dateOffset)` based on `ChannelSourceMode`.
- **FR-002**: In `HOME_PROXY`, the app MUST call the proxy endpoint `GET /api/v1/epg/day` with bearer-token authentication.
- **FR-003**: In `HOME_PROXY`, `EpgProvider.isConfigured` MUST be true only when proxy settings are complete.
- **FR-004**: In `DIRECT_CTC`, the app MUST preserve the existing CTC provider, cache, retry, and configured-state behavior.
- **FR-005**: In `M3U8`, the app MUST report EPG as unconfigured.
- **FR-006**: Proxy EPG parsing MUST map proxy program DTOs to the existing `Program` domain model.

## API Contract

Request:

`dateOffset` follows the CTC backend convention shared by Direct CTC and Home Proxy modes: `-1` means tomorrow, `0` means today, and `1` means yesterday.

```http
GET /api/v1/epg/day?channelCode={provider-channel-code}&dateOffset={-1|0|1}
Authorization: Bearer {local-client-token}
```

Response:

```json
{
  "data": [
    {
      "code": "program-id",
      "name": "Evening News",
      "start": "2026-06-07T20:00:00+08:00",
      "end": "2026-06-07T20:30:00+08:00",
      "isLive": true,
      "isReplayable": false
    }
  ],
  "cache": {
    "stale": false,
    "cachedAt": 1780833600,
    "ttlSeconds": 300
  }
}
```

## Non-Goals

- XMLTV/M3U8 guide import.
- Client-side provider credential fallback while in Home Proxy mode.
- New playback UI surfaces.

## Success Criteria

- Home Proxy mode can show banner and panel EPG using only proxy credentials.
- Unit tests cover proxy EPG request construction, DTO parsing, and mode-based provider routing.
- Existing Direct CTC EPG tests continue to pass.
