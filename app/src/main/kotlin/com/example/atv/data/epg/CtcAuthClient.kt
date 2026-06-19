package com.example.atv.data.epg

import com.example.atv.domain.model.IptvCredentials
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
import javax.inject.Singleton

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
 * 6-step CTC login client. Stateless — credentials are passed to [login] at call time
 * (changed from spec 004 where they were Hilt-injected). This shift is required by
 * spec 005, which moves credentials from the Hilt graph into encrypted storage; the
 * import use case reads them at call time and forwards them here.
 *
 * Constructs a private cookie jar each call so concurrent logins do not share session
 * state. Ports `IPTVClient.login` and its `_step_*` helpers from
 * `~/Documents/itv-reverse/iptv_client.py` lines 353-501.
 */
@Singleton
class CtcAuthClient @Inject constructor(
    private val baseHttp: OkHttpClient,
) {

    /**
     * Random-seed source for the 8-digit `rand` field in the 3DES authenticator
     * plaintext (see `iptv_client.py:_build_authenticator`). Production uses
     * `System.nanoTime()` so each login produces a different authenticator;
     * tests overwrite this property with a deterministic seed so the captured
     * golden authenticator hex is reproducible.
     */
    internal var randomSeed: () -> Long = { System.nanoTime() }

    suspend fun login(creds: IptvCredentials): LoginResult = withContext(Dispatchers.IO) {
        val authBase = creds.authServerUrl.trimEnd('/')
        try {
            val jar = InMemoryCookieJar()
            val http = baseHttp.newBuilder().cookieJar(jar).build()

            val encryToken = stepLoginPage(http, authBase, creds.userId)
                ?: return@withContext LoginResult.Failure("EncryToken not found in login page")

            val authenticator = CtcAuthenticator.buildAuthenticator(
                userId = creds.userId,
                password = creds.password,
                stbId = creds.stbId,
                ip = creds.ip,
                mac = creds.mac,
                encryToken = encryToken,
                randomSeed = randomSeed(),
            )

            val config = stepUploadAuth(http, authBase, creds.userId, authenticator)
            if (config.isEmpty()) {
                return@withContext LoginResult.Failure("uploadAuthInfo had no CTCSetConfig entries")
            }
            val userToken = config["UserToken"]
                ?: return@withContext LoginResult.Failure("UserToken missing from auth response")

            val initialUrl = stepServiceList(http, authBase, userToken)
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

    private fun stepLoginPage(http: OkHttpClient, authBase: String, userId: String): String? {
        val url = "$authBase/auth".toHttpUrl().newBuilder()
            .addQueryParameter("UserID", userId)
            .addQueryParameter("Action", "Login")
            .build()
        val body = http.execGet(url)
        return CtcResponseParsers.parseEncryToken(body)
    }

    private fun stepUploadAuth(
        http: OkHttpClient,
        authBase: String,
        userId: String,
        authenticator: String,
    ): Map<String, String> {
        val form = FormBody.Builder()
            .add("UserID", userId)
            .add("Authenticator", authenticator)
            .add("AccessMethod", "dhcp")
            .add("AccessUserName", userId)
            .build()
        val req = Request.Builder().url("$authBase/uploadAuthInfo").post(form).build()
        val body = http.exec(req)
        return CtcResponseParsers.parseSetConfig(body)
    }

    private fun stepServiceList(http: OkHttpClient, authBase: String, userToken: String): String? {
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
