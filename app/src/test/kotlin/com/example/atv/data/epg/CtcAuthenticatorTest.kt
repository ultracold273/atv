package com.example.atv.data.epg

import com.example.atv.EpgFixtures
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test

class CtcAuthenticatorTest {

    @Test
    fun `encrypt3des output is deterministic for same input`() {
        val key = EpgFixtures.GOLDEN_KEY
        val plaintext = EpgFixtures.GOLDEN_PLAINTEXT.toByteArray(Charsets.UTF_8)
        val a = CtcAuthenticator.encrypt3des(key, plaintext)
        val b = CtcAuthenticator.encrypt3des(key, plaintext)
        assertArrayEquals(a, b)
    }

    @Test
    fun `encrypt3des produces ciphertext that is a multiple of 8 bytes (DES block size)`() {
        val key = EpgFixtures.GOLDEN_KEY
        val ct = CtcAuthenticator.encrypt3des(key, "hello".toByteArray())
        assertEquals(0, ct.size % 8)
        assertTrue(ct.size >= 8)
    }

    @Test
    fun `buildAuthenticator with fixed rand matches python golden hex`() {
        // GATE: this test is the ONLY byte-for-byte verification that the Kotlin
        // 3DES port matches the python reference. It MUST fail loudly when the
        // golden fixture has not been regenerated yet — silently skipping defeats
        // the entire point of porting cryptography.
        //
        // To regenerate the fixture: see Task 6 Step 0 in the plan, or the header
        // of EpgFixtures.kt. After regeneration, GOLDEN_AUTHENTICATOR_HEX will be
        // non-empty and this test runs the byte-match assertion.
        Assumptions.assumeFalse(
            EpgFixtures.GOLDEN_AUTHENTICATOR_HEX.isEmpty(),
            "Golden 3DES fixture not regenerated yet — run the python command in " +
                "EpgFixtures.kt's header comment, paste the outputs into GOLDEN_RAND " +
                "and GOLDEN_AUTHENTICATOR_HEX, then re-run this test. Until then, " +
                "the Kotlin port is UNVERIFIED against the reference implementation."
        )

        val hex = CtcAuthenticator.buildAuthenticatorWithRand(
            userId = EpgFixtures.USER_ID,
            password = EpgFixtures.PASSWORD,
            stbId = EpgFixtures.STB_ID,
            ip = EpgFixtures.IP,
            mac = EpgFixtures.MAC,
            encryToken = EpgFixtures.ENCRY_TOKEN,
            rand = EpgFixtures.GOLDEN_RAND,
        )
        assertEquals(EpgFixtures.GOLDEN_AUTHENTICATOR_HEX, hex)
    }

    @Test
    fun `buildAuthenticator with same randomSeed is deterministic`() {
        val a = CtcAuthenticator.buildAuthenticator(
            userId = EpgFixtures.USER_ID,
            password = EpgFixtures.PASSWORD,
            stbId = EpgFixtures.STB_ID,
            ip = EpgFixtures.IP,
            mac = EpgFixtures.MAC,
            encryToken = EpgFixtures.ENCRY_TOKEN,
            randomSeed = 1234L,
        )
        val b = CtcAuthenticator.buildAuthenticator(
            userId = EpgFixtures.USER_ID,
            password = EpgFixtures.PASSWORD,
            stbId = EpgFixtures.STB_ID,
            ip = EpgFixtures.IP,
            mac = EpgFixtures.MAC,
            encryToken = EpgFixtures.ENCRY_TOKEN,
            randomSeed = 1234L,
        )
        assertEquals(a, b)
    }

    @Test
    fun `randomRand is always 8 digits in 10000000 to 99999999`() {
        repeat(100) { i ->
            val rand = CtcAuthenticator.randomRand(java.util.Random(i.toLong()))
            assertTrue(rand in 10_000_000L..99_999_999L, "rand=$rand")
        }
    }

    @Test
    fun `plaintext format is rand dollar encryToken dollar userId dollar stbId dollar ip dollar mac dollar dollar CTC`() {
        val pt = CtcAuthenticator.plaintext(
            rand = 12345678L,
            encryToken = "tok",
            userId = "u",
            stbId = "s",
            ip = "i",
            mac = "m",
        )
        assertEquals("12345678\$tok\$u\$s\$i\$m\$\$CTC", pt)
    }

    @Test
    fun `key derivation pads password to 24 bytes with zero character`() {
        val key = CtcAuthenticator.deriveKey("abc")
        assertEquals(24, key.size)
        assertEquals('a'.code.toByte(), key[0])
        assertEquals('b'.code.toByte(), key[1])
        assertEquals('c'.code.toByte(), key[2])
        assertEquals('0'.code.toByte(), key[3])
        assertEquals('0'.code.toByte(), key[23])
    }
}
