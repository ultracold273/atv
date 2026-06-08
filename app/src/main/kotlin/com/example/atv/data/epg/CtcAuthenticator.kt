package com.example.atv.data.epg

import java.util.Random
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * Pure crypto helpers for the CTC IPTV authentication scheme.
 *
 * Ports `_build_authenticator` and `_encrypt_3des` from
 * `~/Documents/itv-reverse/iptv_client.py` lines 199-213.
 *
 * Threading: stateless; all functions are safe to call from any thread.
 */
object CtcAuthenticator {

    private const val ALGORITHM = "DESede/ECB/PKCS5Padding"
    private const val KEY_ALGO = "DESede"
    private const val RAND_MIN = 10_000_000L
    private const val RAND_MAX = 99_999_999L

    /**
     * 3DES-ECB encrypt with PKCS5/PKCS7 padding (PKCS5 in JCE name == PKCS7 for 8-byte blocks).
     *
     * @param key 24-byte DESede key (see [deriveKey]).
     * @param plaintext arbitrary bytes; padding is applied automatically.
     */
    fun encrypt3des(key: ByteArray, plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, KEY_ALGO))
        return cipher.doFinal(plaintext)
    }

    /** Pad password to 24 ASCII bytes with '0', as in `password.ljust(24, "0").encode()`. */
    fun deriveKey(password: String): ByteArray =
        password.padEnd(24, '0').toByteArray(Charsets.US_ASCII)

    /** Build the plaintext exactly as the Python reference does (line 208-211). */
    fun plaintext(
        rand: Long,
        encryToken: String,
        userId: String,
        stbId: String,
        ip: String,
        mac: String,
    ): String = "$rand\$$encryToken\$$userId\$$stbId\$$ip\$$mac\$\$CTC"

    /** Pull an 8-digit random in [10_000_000, 99_999_999] from the given [Random]. */
    fun randomRand(random: Random): Long =
        RAND_MIN + (random.nextLong() and Long.MAX_VALUE) % (RAND_MAX - RAND_MIN + 1)

    /**
     * Production entrypoint. Caller supplies a seed for deterministic tests; production
     * callers pass `System.nanoTime()` (or any varied seed).
     */
    fun buildAuthenticator(
        userId: String,
        password: String,
        stbId: String,
        ip: String,
        mac: String,
        encryToken: String,
        randomSeed: Long,
    ): String {
        val rand = randomRand(Random(randomSeed))
        return buildAuthenticatorWithRand(userId, password, stbId, ip, mac, encryToken, rand)
    }

    /**
     * Test-friendly variant that accepts a pre-computed `rand`. The golden-fixture test uses
     * this to byte-match the Python reference (whose Mersenne Twister cannot be replicated by
     * `java.util.Random`).
     */
    fun buildAuthenticatorWithRand(
        userId: String,
        password: String,
        stbId: String,
        ip: String,
        mac: String,
        encryToken: String,
        rand: Long,
    ): String {
        require(rand in RAND_MIN..RAND_MAX) { "rand must be 8-digit, got $rand" }
        val pt = plaintext(rand, encryToken, userId, stbId, ip, mac).toByteArray(Charsets.UTF_8)
        val key = deriveKey(password)
        return encrypt3des(key, pt).toHex()
    }

    private fun ByteArray.toHex(): String {
        val sb = StringBuilder(size * 2)
        for (b in this) {
            sb.append(HEX[(b.toInt() ushr 4) and 0x0F])
            sb.append(HEX[b.toInt() and 0x0F])
        }
        return sb.toString()
    }

    private val HEX = "0123456789abcdef".toCharArray()
}
