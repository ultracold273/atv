package com.example.atv.data.local.secure

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.example.atv.data.remotecontrol.ControlTlsIdentity
import com.example.atv.data.remotecontrol.ControlTlsIdentityProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigInteger
import java.net.Socket
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.Principal
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.X509ExtendedKeyManager
import javax.security.auth.x500.X500Principal

@Singleton
class AndroidControlTlsIdentity @Inject constructor() : ControlTlsIdentityProvider {
    override suspend fun load(): ControlTlsIdentity = withContext(Dispatchers.IO) {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        if (!store.containsAlias(ALIAS)) generateKey()
        val key = store.getKey(ALIAS, null) as PrivateKey
        val certificate = store.getCertificate(ALIAS) as X509Certificate
        val context = SSLContext.getInstance("TLS").apply {
            init(arrayOf(ServerKeyManager(key, certificate)), null, null)
        }
        val fingerprint = MessageDigest.getInstance("SHA-256").digest(certificate.encoded)
            .joinToString(":") { String.format(Locale.ROOT, "%02X", it) }
        ControlTlsIdentity(context, fingerprint)
    }

    private fun generateKey() {
        val now = System.currentTimeMillis()
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN)
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            // Conscrypt hashes the TLS transcript itself and delegates NONEwithECDSA to Keystore.
            .setDigests(KeyProperties.DIGEST_NONE, KeyProperties.DIGEST_SHA256)
            .setCertificateSubject(X500Principal("CN=ATV Remote Control"))
            .setCertificateSerialNumber(BigInteger.ONE)
            .setCertificateNotBefore(Date(now - CLOCK_TOLERANCE_MS))
            .setCertificateNotAfter(Date(now + CERTIFICATE_LIFETIME_MS))
            .build()
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, KEYSTORE).apply {
            initialize(spec)
        }.generateKeyPair()
    }

    private class ServerKeyManager(
        private val key: PrivateKey,
        private val certificate: X509Certificate,
    ) : X509ExtendedKeyManager() {
        override fun chooseServerAlias(type: String?, issuers: Array<out Principal>?, socket: Socket?): String? =
            ALIAS.takeIf { type == key.algorithm }

        override fun chooseEngineServerAlias(
            type: String?, issuers: Array<out Principal>?, engine: SSLEngine?
        ): String? = ALIAS.takeIf { type == key.algorithm }

        override fun getServerAliases(type: String?, issuers: Array<out Principal>?): Array<String>? =
            arrayOf(ALIAS).takeIf { type == key.algorithm }

        override fun getCertificateChain(alias: String?): Array<X509Certificate>? =
            arrayOf(certificate).takeIf { alias == ALIAS }

        override fun getPrivateKey(alias: String?): PrivateKey? = key.takeIf { alias == ALIAS }
        override fun getClientAliases(type: String?, issuers: Array<out Principal>?): Array<String>? = null
        override fun chooseClientAlias(
            types: Array<out String>?, issuers: Array<out Principal>?, socket: Socket?
        ): String? = null
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "atv_remote_control_ec_tls_v1"
        const val CLOCK_TOLERANCE_MS = 86_400_000L
        const val CERTIFICATE_LIFETIME_MS = 315_360_000_000L
    }
}
