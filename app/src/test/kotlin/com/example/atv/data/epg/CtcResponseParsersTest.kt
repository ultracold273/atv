package com.example.atv.data.epg

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class CtcResponseParsersTest {

    // --- parseEncryToken -----------------------------------------------------

    @Test
    fun `parseEncryToken extracts token from CTCGetAuthInfo call`() {
        val html = "<script>Authentication.CTCGetAuthInfo('deadbeef0123');</script>"
        assertEquals("deadbeef0123", CtcResponseParsers.parseEncryToken(html))
    }

    @Test
    fun `parseEncryToken returns null when missing`() {
        assertNull(CtcResponseParsers.parseEncryToken("<html>nothing here</html>"))
    }

    // --- parseSetConfig ------------------------------------------------------

    @Test
    fun `parseSetConfig collects all key value pairs`() {
        val html = """
            Authentication.CTCSetConfig('UserToken','abc123');
            Authentication.CTCSetConfig('EPGURL','http://10.0.0.1/epg/');
            Authentication.CTCSetConfig('Empty','');
        """.trimIndent()
        val cfg = CtcResponseParsers.parseSetConfig(html)
        assertEquals("abc123", cfg["UserToken"])
        assertEquals("http://10.0.0.1/epg/", cfg["EPGURL"])
        assertEquals("", cfg["Empty"])
    }

    @Test
    fun `parseSetConfig returns empty map when no entries`() {
        assertTrue(CtcResponseParsers.parseSetConfig("<html/>").isEmpty())
    }

    // --- parseDocumentLocation ----------------------------------------------

    @Test
    fun `parseDocumentLocation extracts redirect with single quotes`() {
        val html = "document.location = 'http://lb.example.com/iptvepg/index.jsp';"
        assertEquals(
            "http://lb.example.com/iptvepg/index.jsp",
            CtcResponseParsers.parseDocumentLocation(html),
        )
    }

    @Test
    fun `parseDocumentLocation extracts redirect with double quotes`() {
        val html = "document.location=\"http://node.example.com/iptvepg/portal.jsp\""
        assertEquals(
            "http://node.example.com/iptvepg/portal.jsp",
            CtcResponseParsers.parseDocumentLocation(html),
        )
    }

    @Test
    fun `parseDocumentLocation returns null when no redirect`() {
        assertNull(CtcResponseParsers.parseDocumentLocation("<html>no redirect</html>"))
    }

    // --- parseHiddenInputs ---------------------------------------------------

    @Test
    fun `parseHiddenInputs collects name value pairs case-insensitively`() {
        val html = """
            <INPUT TYPE="hidden" NAME="UserID" VALUE="1234567890123"/>
            <input type='hidden' name='STBID' value='001099320000'/>
            <input type="hidden" name="EmptyVal" value=""/>
        """.trimIndent()
        val inputs = CtcResponseParsers.parseHiddenInputs(html)
        assertEquals("1234567890123", inputs["UserID"])
        assertEquals("001099320000", inputs["STBID"])
        assertEquals("", inputs["EmptyVal"])
    }

    @Test
    fun `parseHiddenInputs ignores non-hidden inputs`() {
        val html = "<input type=\"text\" name=\"x\" value=\"y\"/>"
        assertTrue(CtcResponseParsers.parseHiddenInputs(html).isEmpty())
    }

    // --- parseTimestamp ------------------------------------------------------

    @Test
    fun `parseTimestamp accepts yyyyMMddHHmmss in device local zone`() {
        val ts = CtcResponseParsers.parseTimestamp("20260607080000")
        val expected = LocalDateTime.of(2026, 6, 7, 8, 0, 0)
            .atZone(ZoneId.systemDefault())
            .toInstant()
        assertEquals(expected, ts)
    }

    @Test
    fun `parseTimestamp accepts dotted yyyy_MM_dd HH_mm_ss in device local zone`() {
        // Real CTC prevue_list.jsp format, e.g. "2026.03.14 00:56:00".
        val ts = CtcResponseParsers.parseTimestamp("2026.03.14 00:56:00")
        val expected = LocalDateTime.of(2026, 3, 14, 0, 56, 0)
            .atZone(ZoneId.systemDefault())
            .toInstant()
        assertEquals(expected, ts)
    }

    @Test
    fun `parseTimestamp falls back to ISO-8601`() {
        val ts = CtcResponseParsers.parseTimestamp("2026-06-07T08:00:00Z")
        assertEquals(Instant.parse("2026-06-07T08:00:00Z"), ts)
    }

    @Test
    fun `parseTimestamp throws on unrecognized format`() {
        assertThrows(IllegalArgumentException::class.java) {
            CtcResponseParsers.parseTimestamp("not a timestamp")
        }
    }

    // --- parsePrograms -------------------------------------------------------

    @Test
    fun `parsePrograms reads channelPrevue array`() {
        val json = """
            {"channelPrevue":[
              {"prevuecode":"p1","prevuename":"News","begintime":"20260607080000",
               "endtime":"20260607090000","isLive":"1","isBack":"0","isRecord":"0"},
              {"prevuecode":"p2","prevuename":"Drama","begintime":"20260607090000",
               "endtime":"20260607100000","isLive":"0","isBack":"1","isRecord":"0"}
            ]}
        """.trimIndent()
        val programs = CtcResponseParsers.parsePrograms(json)
        assertEquals(2, programs.size)
        assertEquals("p1", programs[0].code)
        assertEquals("News", programs[0].name)
        assertTrue(programs[0].isLive)
        assertTrue(!programs[0].isReplayable)
        assertEquals("p2", programs[1].code)
        assertTrue(!programs[1].isLive)
        assertTrue(programs[1].isReplayable)
    }

    @Test
    fun `parsePrograms parses real CTC response with dotted timestamps and extra fields`() {
        // Mirrors the real prevue_list.jsp response: dotted "yyyy.MM.dd HH:mm:ss" times
        // and many extra fields (isFuture, names, contents, ...) the parser must ignore.
        // Regression guard for the bug where every program was dropped because the dotted
        // timestamp format was unrecognized, yielding an empty (silent) program list.
        val json = """
            {"channelPrevue":[
              {"prevuecode":"11SCHEcp000011260314000244159550","isRecord":"1",
               "endtime":"2026.03.14 00:56:00","begintime":"2026.03.14 00:00:00",
               "isFuture":"0","isBack":"1","noCopyright":"","names":"A|B|C",
               "isLive":"0","prevuename":"中华考工记(8)","chargetypes":"|||||",
               "contents":"x@JSBC","replaceUrl":"","replaceFlag":""},
              {"prevuecode":"11SCHEcp000011260314000244159551","isRecord":"0",
               "endtime":"2026.03.14 01:40:00","begintime":"2026.03.14 00:56:00",
               "isFuture":"0","isBack":"0","isLive":"1","prevuename":"生活圈"}
            ]}
        """.trimIndent()
        val programs = CtcResponseParsers.parsePrograms(json)
        assertEquals(2, programs.size)
        assertEquals("中华考工记(8)", programs[0].name)
        assertTrue(programs[0].isReplayable)
        assertTrue(programs[1].isLive)
        // Times actually parsed (not dropped): program 0 spans 56 minutes.
        assertEquals(56 * 60, java.time.Duration.between(programs[0].start, programs[0].end).seconds)
    }

    @Test
    fun `parsePrograms ignores unknown top-level fields`() {
        val json = """{"channelPrevue":[],"recommendation":"ignore me","totalCount":0}"""
        assertTrue(CtcResponseParsers.parsePrograms(json).isEmpty())
    }

    @Test
    fun `parsePrograms ignores unknown program fields`() {
        val json = """
            {"channelPrevue":[{
              "prevuecode":"p","prevuename":"X","begintime":"20260607080000",
              "endtime":"20260607083000","isLive":"0","isBack":"0","isRecord":"0",
              "isFuture":"1","poster":"http://example.com/p.jpg"
            }]}
        """.trimIndent()
        val programs = CtcResponseParsers.parsePrograms(json)
        assertEquals(1, programs.size)
        assertEquals("p", programs[0].code)
    }

    @Test
    fun `parsePrograms returns empty list when channelPrevue is empty`() {
        assertTrue(CtcResponseParsers.parsePrograms("""{"channelPrevue":[]}""").isEmpty())
    }

    @Test
    fun `parsePrograms throws SerializationException when input is not JSON`() {
        // Non-JSON input (e.g. an HTML login page returned because the session expired)
        // is the caller's responsibility to detect via Content-Type before calling this
        // function. We do NOT silently extract embedded JSON — that hid bugs in the
        // python reference.
        assertThrows(kotlinx.serialization.SerializationException::class.java) {
            CtcResponseParsers.parsePrograms("<html>session expired</html>")
        }
    }

    @Test
    fun `parsePrograms throws SerializationException when channelPrevue field is missing`() {
        // Top-level shape errors fail the whole fetch — there's no recovery.
        assertThrows(kotlinx.serialization.SerializationException::class.java) {
            CtcResponseParsers.parsePrograms("""{"otherKey":[]}""")
        }
    }

    @Test
    fun `parsePrograms skips individual rows with unparseable timestamps`() {
        // Per-row parse errors (e.g. a single program with a bad begintime) should NOT
        // poison the entire fetch — the rest of the schedule is still useful to the user.
        val json = """
            {"channelPrevue":[
              {"prevuecode":"good","prevuename":"OK","begintime":"20260607080000",
               "endtime":"20260607083000","isLive":"0","isBack":"0","isRecord":"0"},
              {"prevuecode":"bad","prevuename":"BadTime","begintime":"NOT-A-TIMESTAMP",
               "endtime":"20260607093000","isLive":"0","isBack":"0","isRecord":"0"},
              {"prevuecode":"also_good","prevuename":"OK2","begintime":"20260607100000",
               "endtime":"20260607103000","isLive":"0","isBack":"0","isRecord":"0"}
            ]}
        """.trimIndent()
        val programs = CtcResponseParsers.parsePrograms(json)
        assertEquals(2, programs.size)
        assertEquals("good", programs[0].code)
        assertEquals("also_good", programs[1].code)
    }

    @Test
    fun `parsePrograms isReplayable is true when isBack or isRecord is 1`() {
        val backOnly = """
            {"channelPrevue":[{"prevuecode":"p","prevuename":"X",
            "begintime":"20260607080000","endtime":"20260607083000",
            "isLive":"0","isBack":"1","isRecord":"0"}]}
        """.trimIndent()
        val recordOnly = """
            {"channelPrevue":[{"prevuecode":"p","prevuename":"X",
            "begintime":"20260607080000","endtime":"20260607083000",
            "isLive":"0","isBack":"0","isRecord":"1"}]}
        """.trimIndent()
        assertTrue(CtcResponseParsers.parsePrograms(backOnly).single().isReplayable)
        assertTrue(CtcResponseParsers.parsePrograms(recordOnly).single().isReplayable)
    }

    @Test
    fun `parseChannels reads jsSetConfig Channel blocks`() {
        val html = """
            <script>
            jsSetConfig('Channel','ChannelID=ch1,ChannelName="A",UserChannelID=001,ChannelURL=igmp://239.0.0.1');
            jsSetConfig('Channel','ChannelID=ch2,ChannelName="B",UserChannelID=002,ChannelURL=igmp://239.0.0.2');
            </script>
        """.trimIndent()
        val out = CtcResponseParsers.parseChannels(html)
        assertEquals(2, out.size)
        assertEquals("ch1", out[0].channelId)
        assertEquals("A", out[0].channelName)
        assertEquals("001", out[0].userChannelId)
    }

    @Test
    fun `parseChannels returns empty when no jsSetConfig blocks present`() {
        assertTrue(CtcResponseParsers.parseChannels("<html/>").isEmpty())
    }

    @Test
    fun `parseMixnoMapping reads display to userChannelId pairs`() {
        val json = """{"channelMixnoMapping":"1:001,2:002,3:003"}"""
        val m = CtcResponseParsers.parseMixnoMapping(json)
        assertEquals("001", m["1"])
        assertEquals("002", m["2"])
        assertEquals("003", m["3"])
    }

    @Test
    fun `parseMixnoMapping returns empty for missing field`() {
        assertTrue(CtcResponseParsers.parseMixnoMapping("""{"other":"x"}""").isEmpty())
    }

    @Test
    fun `parseMixnoMapping returns empty for non-JSON input`() {
        assertTrue(CtcResponseParsers.parseMixnoMapping("not json").isEmpty())
    }
}
