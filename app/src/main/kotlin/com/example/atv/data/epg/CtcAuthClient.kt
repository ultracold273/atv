package com.example.atv.data.epg

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/** Identity used in the authenticator plaintext. Mirrors python `DeviceProfile`. */
data class DeviceProfile(
    val userId: String,
    val password: String,
    val stbId: String,
    val ip: String,
    val mac: String,
)

sealed class LoginResult {
    data class Success(
        val epgLbBase: String,
        val jsessionId: String,
        val config: Map<String, String>,
        val userToken: String,
    ) : LoginResult()

    data class Failure(val reason: String) : LoginResult()
}

/**
 * 6-step CTC login client. Single-use per call to [login]; constructs a private cookie jar
 * each time so concurrent logins do not share session state.
 *
 * Ports `IPTVClient.login` and its `_step_*` helpers from
 * `~/Documents/itv-reverse/iptv_client.py` lines 353-501.
 */
@Singleton
class CtcAuthClient @Inject constructor(
    private val baseHttp: OkHttpClient,
    @Named("authServer") private val authServer: String,
    private val device: DeviceProfile,
) {

    /** Per-instance random seed source. Production uses `System.nanoTime()`; tests inject a deterministic seed. */
    @set:JvmName("setRandomSeedForTest")
    internal var randomSeed: () -> Long = { System.nanoTime() }

    private val authBase: String = authServer.trimEnd('/')

    suspend fun login(): LoginResult = withContext(Dispatchers.IO) {
        try {
            val jar = InMemoryCookieJar()
            val http = baseHttp.newBuilder().cookieJar(jar).build()

            val encryToken = stepLoginPage(http)
                ?: return@withContext LoginResult.Failure("EncryToken not found in login page")

            val authenticator = CtcAuthenticator.buildAuthenticator(
                userId = device.userId,
                password = device.password,
                stbId = device.stbId,
                ip = device.ip,
                mac = device.mac,
                encryToken = encryToken,
                randomSeed = randomSeed(),
            )

            val config = stepUploadAuth(http, authenticator)
            if (config.isEmpty()) {
                return@withContext LoginResult.Failure("uploadAuthInfo had no CTCSetConfig entries")
            }
            val userToken = config["UserToken"]
                ?: return@withContext LoginResult.Failure("UserToken missing from auth response")

            val initialUrl = stepServiceList(http, userToken)
                ?: return@withContext LoginResult.Failure("getServiceList: no document.location redirect")

            val sessionInfo = stepEpgSession(http, jar, initialUrl)
                ?: return@withContext LoginResult.Failure("JSESSIONID cookie not received from EPG")

            stepPortalAuth(http, sessionInfo.epgLbBase, sessionInfo.jsessionId, sessionInfo.portalHtml)

            LoginResult.Success(
                epgLbBase = sessionInfo.epgLbBase,
                jsessionId = sessionInfo.jsessionId,
                config = config,
                userToken = userToken,
            )
        } catch (e: IOException) {
            Timber.d("CTC login network error: %s", e.message)
            LoginResult.Failure("network error: ${e.message}")
        } catch (e: IllegalArgumentException) {
            Timber.d("CTC login parse error: %s", e.message)
            LoginResult.Failure("parse error: ${e.message}")
        }
    }

    private fun stepLoginPage(http: OkHttpClient): String? {
        val url = "$authBase/auth".toHttpUrl().newBuilder()
            .addQueryParameter("UserID", device.userId)
            .addQueryParameter("Action", "Login")
            .build()
        val body = http.execGet(url)
        return CtcResponseParsers.parseEncryToken(body)
    }

    private fun stepUploadAuth(http: OkHttpClient, authenticator: String): Map<String, String> {
        val form = FormBody.Builder()
            .add("UserID", device.userId)
            .add("Authenticator", authenticator)
            .add("AccessMethod", "dhcp")
            .add("AccessUserName", device.userId)
            .build()
        val req = Request.Builder().url("$authBase/uploadAuthInfo").post(form).build()
        val body = http.exec(req)
        return CtcResponseParsers.parseSetConfig(body)
    }

    private fun stepServiceList(http: OkHttpClient, userToken: String): String? {
        val req = Request.Builder()
            .url("$authBase/getServiceList")
            .header("Cookie", "UserToken=$userToken")
            .build()
        val body = http.exec(req)
        return CtcResponseParsers.parseDocumentLocation(body)
    }

    private data class SessionInfo(
        val epgLbBase: String,
        val jsessionId: String,
        val portalHtml: String,
    )

    private fun stepEpgSession(
        http: OkHttpClient,
        jar: InMemoryCookieJar,
        initialUrl: String,
    ): SessionInfo? {
        val firstUrl = initialUrl.toHttpUrlOrNull()
            ?: throw IOException("invalid initial EPG URL: $initialUrl")
        val firstBody = http.execGet(firstUrl)
        val balancedUrl = CtcResponseParsers.parseDocumentLocation(firstBody)
            ?: throw IOException("EPG entry: no load-balanced redirect")

        val secondUrl = balancedUrl.toHttpUrlOrNull()
            ?: throw IOException("invalid balanced EPG URL: $balancedUrl")
        val req = Request.Builder().url(secondUrl).build()
        return http.newCall(req).executeIo().use { resp ->
            val portalHtml = resp.body?.string().orEmpty()
            val jsessionFromJar = jar.cookieValue(secondUrl, "JSESSIONID")
            val jsession = jsessionFromJar
                ?: parseJsessionFromHeaders(resp.headers("Set-Cookie"))
                ?: return@use null
            val finalUrl = resp.request.url
            val pathDir = finalUrl.encodedPath.substringBeforeLast('/').ifEmpty { "/" } + "/"
            val epgLbBase = "${finalUrl.scheme}://${finalUrl.host}" +
                (if (finalUrl.port == HttpUrl.defaultPort(finalUrl.scheme)) "" else ":${finalUrl.port}") +
                pathDir
            SessionInfo(epgLbBase = epgLbBase, jsessionId = jsession, portalHtml = portalHtml)
        }
    }

    private fun stepPortalAuth(
        http: OkHttpClient,
        epgLbBase: String,
        jsessionId: String,
        portalHtml: String,
    ) {
        val params = CtcResponseParsers.parseHiddenInputs(portalHtml)
        val form = FormBody.Builder().apply {
            params.forEach { (k, v) -> add(k, v) }
        }.build()
        val url = "${epgLbBase}funcportalauth.jsp".toHttpUrl()
        val req = Request.Builder()
            .url(url)
            .post(form)
            .header("Cookie", "JSESSIONID=$jsessionId")
            .build()
        // We don't fail login on a non-2xx here; python tolerates it loosely too.
        http.newCall(req).executeIo().use { resp -> resp.body?.string() }
    }

    private fun parseJsessionFromHeaders(setCookieHeaders: List<String>): String? {
        for (h in setCookieHeaders) {
            val m = Regex("JSESSIONID=([^;]+)").find(h)
            if (m != null) return m.groupValues[1]
        }
        return null
    }

    // --- HTTP helpers (private) --------------------------------------------

    private fun OkHttpClient.execGet(url: HttpUrl): String {
        val req = Request.Builder().url(url).build()
        return exec(req)
    }

    private fun OkHttpClient.exec(req: Request): String =
        newCall(req).executeIo().use { it.body?.string().orEmpty() }

    private fun okhttp3.Call.executeIo(): okhttp3.Response =
        try {
            execute()
        } catch (e: IOException) {
            throw e
        }
}

/**
 * Minimal in-memory cookie jar, scoped per [CtcAuthClient.login] invocation.
 * OkHttp's default jar discards cookies; we keep them so JSESSIONID survives across hops.
 */
internal class InMemoryCookieJar : CookieJar {
    private val store = mutableListOf<Cookie>()

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        // Replace existing same-name cookies first.
        for (c in cookies) {
            store.removeAll { it.name == c.name && it.domain == c.domain && it.path == c.path }
            store += c
        }
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> =
        store.filter { it.matches(url) }

    @Synchronized
    fun cookieValue(url: HttpUrl, name: String): String? =
        store.firstOrNull { it.name == name && it.matches(url) }?.value
}
