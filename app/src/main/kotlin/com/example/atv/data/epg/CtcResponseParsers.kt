package com.example.atv.data.epg

import com.example.atv.domain.model.Program
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Pure parsing helpers for CTC HTML/JS/JSON responses.
 *
 * Ports the regexes from `~/Documents/itv-reverse/iptv_client.py` lines 221-228
 * and the `Program.from_json` field mapping (lines 180-191).
 *
 * JSON parsing is STRICT (no tolerance for HTML/JS wrappers): non-JSON input
 * throws [kotlinx.serialization.SerializationException]. Detecting "got HTML
 * instead of JSON" (e.g. a session-expired login page) is the caller's job —
 * see [CtcEpgProvider]'s Content-Type check, which converts that case into a
 * re-login trigger rather than silently falling back.
 *
 * Per-program parse failures (e.g. one row with an unparseable timestamp) are
 * SILENTLY SKIPPED — a single bad show should not blank the entire schedule.
 *
 * Threading: stateless; safe to call from any thread.
 */
object CtcResponseParsers {

    private val RE_ENCRY_TOKEN = Regex("""Authentication\.CTCGetAuthInfo\('([^']+)'\)""")
    private val RE_SET_CONFIG = Regex("""Authentication\.CTCSetConfig\s*\(\s*'([^']+)'\s*,\s*'([^']*)'\s*\)""")
    private val RE_DOCUMENT_LOCATION = Regex("""document\.location\s*=\s*['"]([^'"]+)['"]""")
    private val RE_HIDDEN_INPUT = Regex(
        """<input\s+type=["']hidden["']\s+name\s*=\s*["']([^"']+)["']\s+value\s*=\s*["']?([^"'>\s]*)["']?\s*/?>""",
        RegexOption.IGNORE_CASE,
    )

    private val TIMESTAMP_COMPACT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")

    // Local DTOs that match the wire format. Kept private — the rest of the app
    // sees only the domain-layer [Program].
    @Serializable
    private data class PrevueResponse(
        @SerialName("channelPrevue") val channelPrevue: List<PrevueEntry>,
    )

    @Serializable
    private data class PrevueEntry(
        @SerialName("prevuecode") val code: String = "",
        @SerialName("prevuename") val name: String = "",
        @SerialName("begintime") val begin: String = "",
        @SerialName("endtime") val end: String = "",
        @SerialName("isLive") val isLive: String = "0",
        @SerialName("isBack") val isBack: String = "0",
        @SerialName("isRecord") val isRecord: String = "0",
    )

    fun parseEncryToken(html: String): String? =
        RE_ENCRY_TOKEN.find(html)?.groupValues?.get(1)

    fun parseSetConfig(html: String): Map<String, String> =
        RE_SET_CONFIG.findAll(html).associate { it.groupValues[1] to it.groupValues[2] }

    fun parseDocumentLocation(html: String): String? =
        RE_DOCUMENT_LOCATION.find(html)?.groupValues?.get(1)

    fun parseHiddenInputs(html: String): Map<String, String> =
        RE_HIDDEN_INPUT.findAll(html).associate { it.groupValues[1] to it.groupValues[2] }

    /**
     * Parse a CTC server timestamp.
     *
     * The Python reference stores timestamps as raw strings (no parsing). The Kotlin port
     * normalizes at the provider boundary using these rules (per spec.md "Time zone"):
     *   1. `yyyyMMddHHmmss` (e.g. "20260607080000") interpreted in the device's local zone.
     *   2. ISO-8601 (e.g. "2026-06-07T08:00:00Z") via [Instant.parse].
     *   3. Otherwise throw [IllegalArgumentException].
     */
    fun parseTimestamp(s: String): Instant {
        runCatching {
            return LocalDateTime.parse(s, TIMESTAMP_COMPACT)
                .atZone(ZoneId.systemDefault())
                .toInstant()
        }
        runCatching { return Instant.parse(s) }
        throw IllegalArgumentException("Unrecognized timestamp format: $s")
    }

    /**
     * Parse a `prevue_list.jsp` response body (already verified to be JSON by the caller)
     * into [Program]s. Throws [kotlinx.serialization.SerializationException] on shape
     * errors (no `channelPrevue` array, malformed JSON). Per-row parse errors (e.g. bad
     * timestamp on one program) are skipped silently; only the bad row is dropped.
     */
    fun parsePrograms(jsonText: String): List<Program> {
        val response = AppJson.decodeFromString<PrevueResponse>(jsonText)
        return response.channelPrevue.mapNotNull { entry ->
            runCatching {
                Program(
                    code = entry.code,
                    name = entry.name,
                    start = parseTimestamp(entry.begin),
                    end = parseTimestamp(entry.end),
                    isLive = entry.isLive == "1",
                    isReplayable = entry.isBack == "1" || entry.isRecord == "1",
                )
            }.getOrNull()
        }
    }
}

/**
 * Project-wide [Json] configuration. Provided as a Hilt singleton from [AppModule];
 * exposed here as a top-level fallback so the parser can be unit-tested without DI.
 *
 *   - `ignoreUnknownKeys = true`  — forward-compat: server adds a field, we don't crash.
 *   - `isLenient = false`         — strict input shape; broken JSON is broken JSON.
 *   - `coerceInputValues = false` — don't paper over null-vs-missing-vs-wrong-type bugs.
 */
internal val AppJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = false
    coerceInputValues = false
}
