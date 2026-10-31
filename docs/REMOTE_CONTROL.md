# Agent channel control

ATV offers two authenticated HTTPS operations while it is in the foreground:

| Operation | Request |
|-----------|---------|
| Read all configured channel numbers, names, and groups | `GET /api/v1/channels` |
| Request a switch from that catalog | `POST /api/v1/playback/channel` with `number` and `catalogVersion` |

The agent chooses the number. There is no channel-name resolver, status endpoint, or remote editing capability. The full contract is in [spec 012](../specs/012-agent-channel-control/spec.md).

## TV setup

1. Open Settings and enable **Control by an assistant**. This is off by default.
2. Open **Connection details** and provision your controller with the TV address, access token, and SHA-256 certificate fingerprint.
3. Verify that fingerprint out of band before sending the token. The included client does this before sending any HTTP request.
4. Return to the playback screen to accept channel switches. Catalog retrieval also works from settings; switching there returns `PLAYBACK_UNAVAILABLE`.

The listener uses port 8765. Closing or backgrounding ATV closes the listener; returning to ATV starts it again if enabled. A foreground callback updates the displayed addresses when the network changes. **Replace access token** revokes old controllers, including requests awaiting dispatch. Reinstalling/clearing app data changes the TV identity and requires provisioning again.

## Example controller

[scripts/atv_control.py](../scripts/atv_control.py) requires only Python 3's standard library. Replace the example address and fingerprint with the values displayed by the TV:

```bash
python3 scripts/atv_control.py --url 'https://192.168.1.50:8765' --fingerprint 'COPY_THE_COMPLETE_SHA256_FINGERPRINT_FROM_THE_TV' list
```

The client prompts for the token without echoing it. Automated callers can instead supply `ATV_CONTROL_TOKEN` through their secret/environment configuration; avoid putting it in command arguments or source code.

Choose a number from the response and carry over its exact `catalogVersion`:

```bash
python3 scripts/atv_control.py --url 'https://192.168.1.50:8765' --fingerprint 'COPY_THE_COMPLETE_SHA256_FINGERPRINT_FROM_THE_TV' switch 17 --catalog-version 'COPY_VERSION_FROM_THE_SELECTED_CATALOG'
```

The client authenticates the exact self-signed certificate using the supplied fingerprint instead of a public CA or a hostname that may change on the LAN. It deliberately sends no token until that check succeeds. There is no option to bypass the fingerprint check.

`202 Accepted` means the request was dispatched to playback. A voice assistant may say “正在切换到央视一套”; this response does not confirm that the stream has started. Media failures remain visible on the TV. A lost response leaves the outcome unknown; the client does not retry switch commands automatically.

On `409 CATALOG_CHANGED`, retrieve the catalog again and choose again. Do not silently refresh the version while retaining a previously chosen number: a replacement playlist may have reassigned it.

## Implementation boundaries

- The active playback route registers the existing channel-play workflow with a coordinator. Remote and physical controls execute on the UI/player thread; source URL resolution, EPG, and last-watched persistence share the same path.
- A repository mutex protects snapshot selection through dispatch. Playlist replacement is a Room transaction. The catalog version is a length-framed SHA-256 digest of ordered channel records, including stream URLs; URLs themselves are never returned.
- The listener uses Netty's HTTP/1 codec and bounded aggregation, with platform JSSE TLS and a non-exportable P-256 EC key in Android Keystore. It allows eight connections, limits request bodies to 4 KiB, uses five-second socket/handshake timeouts and a two-second dispatch timeout, and caps connection lifetime at 30 seconds. HTTP pipelining is not supported; serial keep-alive requests are.
- Control credentials are stored in dedicated encrypted preferences excluded from cloud/device-transfer backups. Network processing never logs credentials, catalog stream URLs, or raw exception messages.
- Netty 4.1.139.Final is pinned for the HTTP codec and handler; native transports, HTTP/2, decompression, and multipart features are not enabled. Its [release notes](https://netty.io/news/2026/10/06/4-1-139-Final.html) describe the security/bug-fix release and July 2027 maintenance deadline. Netty documents [Android support in 4.1](https://netty.io/wiki/new-and-noteworthy-in-4.1.html). Review the pinned dependency on upgrade, including its transitive modules.

## Verification

```bash
./studio-gradlew testDebugUnitTest
python3 -m unittest discover -s scripts/tests -v
./studio-gradlew detekt lintDebug assembleDebug assembleDebugAndroidTest
./studio-gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.atv.RemoteControlSmokeTest,com.example.atv.ChannelCatalogDatabaseTest,com.example.atv.RemoteControlStorageTest
```

Instrumented tests bind only emulator loopback, use an in-memory database and fake player, and never contact live IPTV services. They cover real Android Keystore TLS, TV settings, channel/UI/persistence integration, rotation, foreground lifecycle, bind failure, transactional rollback, and a 1,000-channel warm-request latency fixture.
