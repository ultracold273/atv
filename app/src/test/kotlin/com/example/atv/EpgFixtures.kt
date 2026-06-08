package com.example.atv

/**
 * Captured fixtures from the Python reference at ~/Documents/itv-reverse/iptv_client.py.
 *
 * To regenerate GOLDEN_AUTHENTICATOR_HEX and GOLDEN_RAND, run (from ~/Documents/itv-reverse/):
 *
 *   python -c "
 *   import random
 *   random.seed(42)
 *   from iptv_client import DeviceProfile, _build_authenticator
 *   device = DeviceProfile(
 *       user_id='0512208781520',
 *       password='102255',
 *       stb_id='00109932000000001690878561017743',
 *       ip='192.168.20.200',
 *       mac='40:1A:58:96:92:BD',
 *   )
 *   print(_build_authenticator(device, 'abcdef0123456789'))
 *   print(random.Random(42).randint(10_000_000, 99_999_999))
 *   "
 *
 * Paste the first line into GOLDEN_AUTHENTICATOR_HEX, the second into GOLDEN_RAND.
 */
object EpgFixtures {
    const val USER_ID = "0512208781520"
    const val PASSWORD = "102255"
    const val STB_ID = "00109932000000001690878561017743"
    const val IP = "192.168.20.200"
    const val MAC = "40:1A:58:96:92:BD"
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
