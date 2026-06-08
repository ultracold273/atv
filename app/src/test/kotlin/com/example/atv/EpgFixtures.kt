package com.example.atv

/**
 * Test fixtures for the CTC EPG provider stack. These are SYNTHETIC dummy values
 * chosen to satisfy the format requirements asserted by the wire protocol
 * (digit-only userId, 32-hex STB id, documentation-range IP/MAC) without being
 * any real person's account, device, or network identity.
 *
 * Documentation ranges used (so we are not impersonating anyone real):
 *   IP:  192.0.2.x  — RFC 5737 "TEST-NET-1" reserved for documentation
 *   MAC: 00:00:5E:00:53:xx — RFC 7042 / IANA reserved for documentation
 *
 * --- 3DES golden fixture regeneration ---
 *
 * The byte-match test in CtcAuthenticatorTest is gated on a fixture captured
 * from the python reference. To regenerate it for THESE dummy inputs, run
 * (from ~/Documents/itv-reverse/):
 *
 *   python -c "
 *   import random
 *   random.seed(42)
 *   from iptv_client import DeviceProfile, _build_authenticator
 *   device = DeviceProfile(
 *       user_id='1234567890123',
 *       password='000000',
 *       stb_id='00000000000000000000000000000000',
 *       ip='192.0.2.1',
 *       mac='00:00:5E:00:53:01',
 *   )
 *   print(_build_authenticator(device, 'abcdef0123456789'))
 *   print(random.Random(42).randint(10_000_000, 99_999_999))
 *   "
 *
 * Paste the first line into GOLDEN_AUTHENTICATOR_HEX, the second into GOLDEN_RAND.
 *
 * DO NOT replace these constants with real account credentials when capturing
 * the fixture — the golden hex committed here must correspond to the dummy
 * inputs above, not to anyone's actual subscription.
 */
object EpgFixtures {
    const val USER_ID = "1234567890123"
    const val PASSWORD = "000000"
    const val STB_ID = "00000000000000000000000000000000"
    const val IP = "192.0.2.1"
    const val MAC = "00:00:5E:00:53:01"
    const val ENCRY_TOKEN = "abcdef0123456789"

    /** Python: random.Random(42).randint(10_000_000, 99_999_999). */
    const val GOLDEN_RAND: Long = 0L // TODO: paste from python output above

    /** Hex output of _build_authenticator(device, ENCRY_TOKEN) with rand fixed to GOLDEN_RAND. */
    const val GOLDEN_AUTHENTICATOR_HEX: String = "" // TODO: paste from python output above

    /** Plaintext that should be 3DES-encrypted when rand == GOLDEN_RAND. */
    val GOLDEN_PLAINTEXT: String
        get() = "$GOLDEN_RAND\$$ENCRY_TOKEN\$$USER_ID\$$STB_ID\$$IP\$$MAC\$\$CTC"

    /** Key derivation: password padded to 24 bytes with '0'. */
    val GOLDEN_KEY: ByteArray
        get() = PASSWORD.padEnd(24, '0').toByteArray(Charsets.US_ASCII)
}
