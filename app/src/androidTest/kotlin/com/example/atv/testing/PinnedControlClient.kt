package com.example.atv.testing

import okhttp3.OkHttpClient
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/** Test client authenticates the real Android Keystore identity by its out-of-band fingerprint. */
fun pinnedControlClient(fingerprint: String): OkHttpClient {
    val trust = object : X509TrustManager {
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            val leaf = chain?.firstOrNull() ?: throw CertificateException("Missing server identity")
            val actual = MessageDigest.getInstance("SHA-256").digest(leaf.encoded)
                .joinToString(":") { String.format(Locale.ROOT, "%02X", it) }
            if (actual != fingerprint) throw CertificateException("Unexpected server identity")
        }
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            throw CertificateException("Client certificates are not used")
        }
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }
    val context = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), null) }
    return OkHttpClient.Builder()
        .sslSocketFactory(context.socketFactory, trust)
        // Identity is authenticated by the mandatory exact certificate pin, not a changing LAN address.
        .hostnameVerifier { _, _ -> true }
        .callTimeout(5, TimeUnit.SECONDS)
        .build()
}
