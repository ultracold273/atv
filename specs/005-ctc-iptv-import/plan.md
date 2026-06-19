# CTC IPTV Channel Import Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Light up the dormant EPG feature shipped in spec 004 by adding CTC credential entry, encrypted storage, the import action, and the Room schema change that makes `Channel.channelCode` a real field.

**Architecture:** Five units composed into a single import flow. `IptvCredentialsStore` (EncryptedSharedPreferences) holds the secrets. `DeviceDefaultsProvider` generates first-open defaults. `CtcChannelFetcher` ports the python `frameset_builder.jsp` step. `ImportCtcChannelsUseCase` orchestrates login → fetch → save → flip-isConfigured, called by both the manual "Test & import" button and the `IptvSessionBootstrapper` auto-relogin at startup. New `IptvSettingsScreen` UI surfaces the form.

**Tech Stack:** Kotlin, Compose for TV, Hilt, Room (with the project's first real `Migration`), AndroidX Security (`security-crypto` for `EncryptedSharedPreferences`), JUnit 5 + MockK + Turbine + MockWebServer + Room's `MigrationTestHelper`.

**Spec coverage:** All 33 FRs and 8 SCs from [spec.md](spec.md) map to tasks below.

---

## Phases overview

The plan has 5 phases, each independently committable:

1. **Phase 1: Schema + Channel model** — Room migration v1→v2, real `channelCode` field on `Channel`/`ChannelEntity`, delete the spec-004 extension property.
2. **Phase 2: Credential storage + defaults** — AndroidX Security dep, `IptvCredentialsStore` over `EncryptedSharedPreferences`, `DeviceDefaultsProvider` for auto-gen.
3. **Phase 3: Channel fetcher + import use case** — `CtcChannelFetcher`, `ImportCtcChannelsUseCase`, `ImportResult` sealed class, `CtcEpgProvider.markConfigured` replaces `testSetConfigured`.
4. **Phase 4: Settings UI** — `IptvSettingsScreen` + `IptvSettingsViewModel`, navigation route, settings row, localized strings.
5. **Phase 5: Bootstrap + privacy follow-through** — `IptvSessionBootstrapper` in `AtvApplication.onCreate`, replace `EpgNetworkModule` sentinel providers with credentials-store-backed ones, end-to-end integration test, manual verification.

This file commits with **Phase 1 + 2 detailed** and Phases 3-5 outlined for follow-up authoring. Each detailed phase is independently executable; the outline is a faithful description of the remaining work that another spec-author session can expand to full TDD tasks.

---

## Phase 1: Schema + Channel model

### Task 1: Add `channelCode` to `Channel` data class

**Files:**
- Modify: `app/src/main/kotlin/com/example/atv/domain/model/Channel.kt`
- Delete: `app/src/main/kotlin/com/example/atv/domain/model/ChannelEpgExtensions.kt`

- [ ] **Step 1: Delete the temporary extension property**

```bash
rm app/src/main/kotlin/com/example/atv/domain/model/ChannelEpgExtensions.kt
```

- [ ] **Step 2: Add the real field to `Channel`**

Replace `app/src/main/kotlin/com/example/atv/domain/model/Channel.kt` with:

```kotlin
package com.example.atv.domain.model

/**
 * Represents a single IPTV channel from the playlist.
 *
 * @param number Unique channel number, 1-indexed
 * @param name Display name from EXTINF or operator-provided ChannelName
 * @param streamUrl HLS/RTSP stream URL
 * @param groupTitle Category/group (optional)
 * @param logoUrl Channel logo URL (optional)
 * @param channelCode Opaque per-provider EPG channel identifier. Null for M3U8-loaded
 *   channels; populated by spec 005's CTC import for operator-provided channels.
 */
data class Channel(
    val number: Int,
    val name: String,
    val streamUrl: String,
    val groupTitle: String? = null,
    val logoUrl: String? = null,
    val channelCode: String? = null
) {
    init {
        require(number >= 1) { "Channel number must be >= 1" }
        require(name.isNotBlank()) { "Channel name must not be blank" }
        require(streamUrl.isNotBlank()) { "Stream URL must not be blank" }
    }
}
```

- [ ] **Step 3: Verify the project still compiles**

Run: `./studio-gradlew compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`. The spec-004 call sites that imported `com.example.atv.domain.model.channelCode` now resolve to the data-class property of the same name; the import line still compiles but is now an unused-import warning. Remove it in the next step.

- [ ] **Step 4: Remove the now-unused extension imports**

In `app/src/main/kotlin/com/example/atv/ui/screens/playback/PlaybackViewModel.kt`, delete:

```kotlin
import com.example.atv.domain.model.channelCode
```

In `app/src/main/kotlin/com/example/atv/ui/components/EpgPanel.kt`, delete:

```kotlin
import com.example.atv.domain.model.channelCode
```

(Both files keep `channel.channelCode` access; it now resolves to the data-class property.)

- [ ] **Step 5: Run full unit test suite to confirm no regression**

Run: `./studio-gradlew :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`. All existing tests pass — `Channel(...)` constructor calls work because `channelCode` defaults to null.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/example/atv/domain/model/Channel.kt \
        app/src/main/kotlin/com/example/atv/ui/screens/playback/PlaybackViewModel.kt \
        app/src/main/kotlin/com/example/atv/ui/components/EpgPanel.kt
git rm app/src/main/kotlin/com/example/atv/domain/model/ChannelEpgExtensions.kt
git commit -m "feat(005): promote Channel.channelCode from extension to real field"
```

---

### Task 2: Add `channel_code` column to Room + register Migration(1, 2)

**Files:**
- Modify: `app/src/main/kotlin/com/example/atv/data/local/db/ChannelEntity.kt`
- Modify: `app/src/main/kotlin/com/example/atv/data/local/db/AtvDatabase.kt`
- Modify: `app/src/main/kotlin/com/example/atv/di/DatabaseModule.kt`
- Create: `app/src/main/kotlin/com/example/atv/data/local/db/AtvDatabaseMigrations.kt`
- Create: `app/src/androidTest/kotlin/com/example/atv/data/local/db/MigrationTest.kt`

The project has no migrations today. This task establishes the migration scaffolding for future schema changes too.

- [ ] **Step 1: Add `channelCode` to `ChannelEntity` and update mappers**

In `app/src/main/kotlin/com/example/atv/data/local/db/ChannelEntity.kt`, add the column inside the `@Entity` data class:

```kotlin
@ColumnInfo(name = "channel_code")
val channelCode: String? = null,
```

In the same file, update `toDomain()` to include `channelCode = channelCode`, and update `toEntity()` (the `Channel.toEntity(isManuallyAdded: Boolean = false)` extension) to set `channelCode = channelCode` on the returned `ChannelEntity`.

- [ ] **Step 2: Create the migration**

Create `app/src/main/kotlin/com/example/atv/data/local/db/AtvDatabaseMigrations.kt`:

```kotlin
package com.example.atv.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration from v1 to v2 — adds `channel_code` column for EPG provider lookup.
 *
 * Spec 005 promotes `Channel.channelCode` from a temporary extension property
 * (always-null) to a real data-class field. Existing rows keep their data; the
 * new column defaults to NULL, so M3U8-loaded channels continue to render the
 * "EPG not available for this channel" empty state exactly as in spec 004.
 */
val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE channels ADD COLUMN channel_code TEXT")
    }
}

/** All registered migrations, in version order. */
val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)
```

- [ ] **Step 3: Bump database version and register the migration**

In `app/src/main/kotlin/com/example/atv/data/local/db/AtvDatabase.kt`, change `version = 1` to `version = 2`.

In `app/src/main/kotlin/com/example/atv/di/DatabaseModule.kt`, find the `Room.databaseBuilder(...)` call and add `.addMigrations(*ALL_MIGRATIONS)` before `.build()`. Import `com.example.atv.data.local.db.ALL_MIGRATIONS`.

If `DatabaseModule.kt` currently uses `fallbackToDestructiveMigration()`, DELETE that call — we now have a real migration.

- [ ] **Step 4: Write the migration test**

The project does not yet have `androidTest` infrastructure for Room. Add `androidx.room:room-testing` to test dependencies in `app/build.gradle.kts` if not already present (check `gradle/libs.versions.toml` for `room-testing`; if missing, add `room-testing = { module = "androidx.room:room-testing", version.ref = "room" }` and reference as `androidTestImplementation(libs.room.testing)`).

Create `app/src/androidTest/kotlin/com/example/atv/data/local/db/MigrationTest.kt`:

```kotlin
package com.example.atv.data.local.db

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val testDbName = "migration-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AtvDatabase::class.java,
    )

    @Test
    fun migrate1To2_preservesExistingChannels_andAddsNullChannelCode() {
        // Create v1 DB with one channel row.
        helper.createDatabase(testDbName, 1).use { db ->
            db.execSQL(
                "INSERT INTO channels (number, name, stream_url, group_title, logo_url, is_manually_added) " +
                    "VALUES (1, 'Test', 'http://example.com/s.m3u8', 'News', NULL, 0)"
            )
        }

        // Run migration to v2.
        helper.runMigrationsAndValidate(testDbName, 2, true, MIGRATION_1_2).use { db ->
            db.query("SELECT number, name, channel_code FROM channels").use { cursor ->
                assertEquals(1, cursor.count)
                cursor.moveToFirst()
                assertEquals(1, cursor.getInt(0))
                assertEquals("Test", cursor.getString(1))
                assertNull(cursor.getString(2))   // channel_code is NULL after migration
            }
        }
    }

    @Test
    fun openWithBuilder_v2_succeeds() {
        // Smoke test: full open via builder path with migrations registered.
        val db = Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AtvDatabase::class.java,
            testDbName,
        )
            .addMigrations(*ALL_MIGRATIONS)
            .openHelperFactory(FrameworkSQLiteOpenHelperFactory())
            .build()
        db.openHelper.writableDatabase   // forces migration if needed
        db.close()
    }
}
```

- [ ] **Step 5: Enable schema export so Room can verify the migration**

In `app/build.gradle.kts`, find the `android { defaultConfig { ... } }` block and add the Room schema-export configuration (Room's `MigrationTestHelper` needs the v1 + v2 schemas as JSON):

```kotlin
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
```

(Add inside the `android {}` block, NOT `defaultConfig`. The `ksp` extension is provided by the KSP gradle plugin already on the project.)

Build once to generate `app/schemas/com.example.atv.data.local.db.AtvDatabase/2.json`:

Run: `./studio-gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`. A new `app/schemas/` directory is created. Commit the generated schema files.

- [ ] **Step 6: Run the migration test (requires emulator)**

Run: `./studio-gradlew :app:connectedAndroidTest --tests "com.example.atv.data.local.db.MigrationTest"`
Expected: 2 tests PASSED.

If no emulator is available locally, mark this step as "must run in CI" and proceed; the connected-test gate catches it before merge.

- [ ] **Step 7: Run unit tests to confirm no regression**

Run: `./studio-gradlew :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`. The Phase 1 Task 1 changes plus the new column should not break existing repository or VM tests because everything defaults to null.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/kotlin/com/example/atv/data/local/db/ChannelEntity.kt \
        app/src/main/kotlin/com/example/atv/data/local/db/AtvDatabase.kt \
        app/src/main/kotlin/com/example/atv/data/local/db/AtvDatabaseMigrations.kt \
        app/src/main/kotlin/com/example/atv/di/DatabaseModule.kt \
        app/src/androidTest/kotlin/com/example/atv/data/local/db/MigrationTest.kt \
        app/build.gradle.kts \
        gradle/libs.versions.toml \
        app/schemas/
git commit -m "feat(005): add channel_code column with Room Migration(1, 2)"
```

---

## Phase 2: Credential storage + defaults

### Task 3: Add AndroidX Security dependency

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Add version + library entry to `libs.versions.toml`**

Add under `[versions]`:

```toml
# Encrypted storage
securityCrypto = "1.1.0-alpha06"
```

Add under `[libraries]`:

```toml
# Encrypted storage
androidx-security-crypto = { module = "androidx.security:security-crypto-ktx", version.ref = "securityCrypto" }
```

(The `1.1.0-alpha06` version supports the modern `MasterKey.Builder` API; the stable `1.0.0` line uses a deprecated `MasterKeys` helper.)

- [ ] **Step 2: Reference the lib in `app/build.gradle.kts`**

In the `dependencies {` block, add:

```kotlin
implementation(libs.androidx.security.crypto)
```

- [ ] **Step 3: Verify build succeeds**

Run: `./studio-gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts
git commit -m "build(005): add AndroidX Security (EncryptedSharedPreferences)"
```

---

### Task 4: Add `IptvCredentials` domain model

**Files:**
- Create: `app/src/main/kotlin/com/example/atv/domain/model/IptvCredentials.kt`
- Create: `app/src/test/kotlin/com/example/atv/domain/model/IptvCredentialsTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/kotlin/com/example/atv/domain/model/IptvCredentialsTest.kt`:

```kotlin
package com.example.atv.domain.model

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class IptvCredentialsTest {

    private fun valid() = IptvCredentials(
        userId = "1234567890123",
        password = "000000",
        stbId = "0".repeat(32),
        ip = "192.0.2.1",
        mac = "00:00:5E:00:53:01",
        authServerUrl = "http://example.com:8298",
    )

    @Test
    fun `isComplete true when all fields valid`() {
        assertTrue(valid().isComplete)
    }

    @Test
    fun `isComplete false when userId blank`() {
        assertFalse(valid().copy(userId = "").isComplete)
        assertFalse(valid().copy(userId = "  ").isComplete)
    }

    @Test
    fun `isComplete false when password blank`() {
        assertFalse(valid().copy(password = "").isComplete)
    }

    @Test
    fun `isComplete false when stbId is not 32 chars`() {
        assertFalse(valid().copy(stbId = "0".repeat(31)).isComplete)
        assertFalse(valid().copy(stbId = "0".repeat(33)).isComplete)
        assertFalse(valid().copy(stbId = "").isComplete)
    }

    @Test
    fun `isComplete false when ip blank`() {
        assertFalse(valid().copy(ip = "").isComplete)
    }

    @Test
    fun `isComplete false when mac blank`() {
        assertFalse(valid().copy(mac = "").isComplete)
    }

    @Test
    fun `isComplete false when authServerUrl is not a valid http url`() {
        assertFalse(valid().copy(authServerUrl = "").isComplete)
        assertFalse(valid().copy(authServerUrl = "not a url").isComplete)
        assertFalse(valid().copy(authServerUrl = "ftp://example.com").isComplete)
    }

    @Test
    fun `isComplete true for both http and https urls`() {
        assertTrue(valid().copy(authServerUrl = "http://x.com").isComplete)
        assertTrue(valid().copy(authServerUrl = "https://x.com").isComplete)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./studio-gradlew :app:testDebugUnitTest --tests "com.example.atv.domain.model.IptvCredentialsTest"`
Expected: FAIL — `IptvCredentials` class does not exist.

- [ ] **Step 3: Create the model**

Create `app/src/main/kotlin/com/example/atv/domain/model/IptvCredentials.kt`:

```kotlin
package com.example.atv.domain.model

/**
 * User-entered CTC IPTV credentials. Stored encrypted via
 * [com.example.atv.data.local.secure.IptvCredentialsStore].
 *
 * `isComplete` mirrors the form-validation rules in spec 005 FR-011: every field
 * non-blank, STB ID exactly 32 chars, auth server URL parses as http(s).
 */
data class IptvCredentials(
    val userId: String,
    val password: String,
    val stbId: String,
    val ip: String,
    val mac: String,
    val authServerUrl: String,
) {
    val isComplete: Boolean
        get() = userId.isNotBlank() &&
            password.isNotBlank() &&
            stbId.length == 32 &&
            ip.isNotBlank() &&
            mac.isNotBlank() &&
            isHttpUrl(authServerUrl)

    private fun isHttpUrl(s: String): Boolean {
        if (s.isBlank()) return false
        return try {
            val u = java.net.URI(s)
            u.scheme in setOf("http", "https") && !u.host.isNullOrBlank()
        } catch (_: java.net.URISyntaxException) {
            false
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./studio-gradlew :app:testDebugUnitTest --tests "com.example.atv.domain.model.IptvCredentialsTest"`
Expected: PASS, 8 tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/example/atv/domain/model/IptvCredentials.kt \
        app/src/test/kotlin/com/example/atv/domain/model/IptvCredentialsTest.kt
git commit -m "feat(005): add IptvCredentials domain model with isComplete validation"
```

---

### Task 5: Add `IptvCredentialsStore` (EncryptedSharedPreferences)

**Files:**
- Create: `app/src/main/kotlin/com/example/atv/domain/repository/IptvCredentialsStore.kt`
- Create: `app/src/main/kotlin/com/example/atv/data/local/secure/IptvCredentialsStoreImpl.kt`
- Create: `app/src/main/kotlin/com/example/atv/di/SecureStorageModule.kt`
- Create: `app/src/androidTest/kotlin/com/example/atv/data/local/secure/IptvCredentialsStoreImplTest.kt`

Encrypted-Storage tests must run on a device because `EncryptedSharedPreferences` uses the Android Keystore. Pure-JVM unit tests cannot exercise the encryption path; the store gets a thin pass-through fake for ViewModel tests instead.

- [ ] **Step 1: Define the interface**

Create `app/src/main/kotlin/com/example/atv/domain/repository/IptvCredentialsStore.kt`:

```kotlin
package com.example.atv.domain.repository

import com.example.atv.domain.model.IptvCredentials
import kotlinx.coroutines.flow.Flow

/**
 * Encrypted storage for CTC IPTV credentials. Implementation MUST use
 * EncryptedSharedPreferences or equivalent — plain DataStore is not acceptable
 * (spec 005 FR-004).
 */
interface IptvCredentialsStore {
    /** Emits the current credentials, or null when nothing is stored. */
    fun observe(): Flow<IptvCredentials?>

    /** Read-once snapshot, returns null when nothing stored. */
    suspend fun read(): IptvCredentials?

    /** Persist all six fields encrypted. */
    suspend fun save(creds: IptvCredentials)

    /** Wipe all stored values. */
    suspend fun clear()
}
```

- [ ] **Step 2: Write the device-side test**

Create `app/src/androidTest/kotlin/com/example/atv/data/local/secure/IptvCredentialsStoreImplTest.kt`:

```kotlin
package com.example.atv.data.local.secure

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.atv.domain.model.IptvCredentials
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IptvCredentialsStoreImplTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var store: IptvCredentialsStoreImpl

    @Before
    fun setUp() {
        // Use a unique prefs name so concurrent test runs don't collide.
        store = IptvCredentialsStoreImpl(context, prefsName = "iptv_creds_test")
        runBlocking { store.clear() }
    }

    @After
    fun tearDown() {
        runBlocking { store.clear() }
    }

    @Test
    fun read_returnsNullWhenEmpty() = runBlocking {
        assertNull(store.read())
    }

    @Test
    fun saveAndRead_roundTripsAllFields() = runBlocking {
        val creds = IptvCredentials(
            userId = "1234567890123",
            password = "000000",
            stbId = "0".repeat(32),
            ip = "192.0.2.1",
            mac = "00:00:5E:00:53:01",
            authServerUrl = "http://example.com:8298",
        )
        store.save(creds)
        assertEquals(creds, store.read())
    }

    @Test
    fun clear_wipesAllFields() = runBlocking {
        store.save(
            IptvCredentials(
                userId = "u", password = "p", stbId = "0".repeat(32),
                ip = "i", mac = "m", authServerUrl = "http://x.com",
            )
        )
        store.clear()
        assertNull(store.read())
    }

    @Test
    fun observe_emitsNullThenStoredValueAfterSave() = runBlocking {
        assertNull(store.observe().first())
        val creds = IptvCredentials(
            userId = "u2", password = "p2", stbId = "0".repeat(32),
            ip = "1.2.3.4", mac = "00:00:5E:00:53:02", authServerUrl = "https://x.com",
        )
        store.save(creds)
        assertEquals(creds, store.observe().first())
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `./studio-gradlew :app:connectedAndroidTest --tests "com.example.atv.data.local.secure.IptvCredentialsStoreImplTest"`
Expected: FAIL — `IptvCredentialsStoreImpl` class does not exist.

- [ ] **Step 4: Create the implementation**

Create `app/src/main/kotlin/com/example/atv/data/local/secure/IptvCredentialsStoreImpl.kt`:

```kotlin
package com.example.atv.data.local.secure

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.atv.domain.model.IptvCredentials
import com.example.atv.domain.repository.IptvCredentialsStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * EncryptedSharedPreferences-backed store. Each value is encrypted with AES-256-GCM
 * under a key bound to the Android Keystore (per AndroidX Security defaults).
 *
 * Threading: all reads/writes are dispatched to `Dispatchers.IO` because
 * `SharedPreferences.edit()` and the underlying encryption are synchronous blocking calls.
 */
@Singleton
class IptvCredentialsStoreImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefsName: String = DEFAULT_PREFS_NAME,
) : IptvCredentialsStore {

    private val prefs: SharedPreferences by lazy { buildEncryptedPrefs() }

    override fun observe(): Flow<IptvCredentials?> = callbackFlow {
        // Emit current state immediately, then on every change.
        trySend(readBlocking())
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            trySend(readBlocking())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.flowOn(Dispatchers.IO)

    override suspend fun read(): IptvCredentials? = withContext(Dispatchers.IO) {
        readBlocking()
    }

    override suspend fun save(creds: IptvCredentials): Unit = withContext(Dispatchers.IO) {
        prefs.edit()
            .putString(KEY_USER_ID, creds.userId)
            .putString(KEY_PASSWORD, creds.password)
            .putString(KEY_STB_ID, creds.stbId)
            .putString(KEY_IP, creds.ip)
            .putString(KEY_MAC, creds.mac)
            .putString(KEY_AUTH_URL, creds.authServerUrl)
            .apply()
    }

    override suspend fun clear(): Unit = withContext(Dispatchers.IO) {
        prefs.edit().clear().apply()
    }

    private fun readBlocking(): IptvCredentials? {
        val userId = prefs.getString(KEY_USER_ID, null) ?: return null
        return IptvCredentials(
            userId = userId,
            password = prefs.getString(KEY_PASSWORD, "").orEmpty(),
            stbId = prefs.getString(KEY_STB_ID, "").orEmpty(),
            ip = prefs.getString(KEY_IP, "").orEmpty(),
            mac = prefs.getString(KEY_MAC, "").orEmpty(),
            authServerUrl = prefs.getString(KEY_AUTH_URL, "").orEmpty(),
        )
    }

    private fun buildEncryptedPrefs(): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            prefsName,
            masterKey,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private companion object {
        const val DEFAULT_PREFS_NAME = "iptv_credentials"
        const val KEY_USER_ID = "user_id"
        const val KEY_PASSWORD = "password"
        const val KEY_STB_ID = "stb_id"
        const val KEY_IP = "ip"
        const val KEY_MAC = "mac"
        const val KEY_AUTH_URL = "auth_server_url"
    }
}
```

- [ ] **Step 5: Create the Hilt binding**

Create `app/src/main/kotlin/com/example/atv/di/SecureStorageModule.kt`:

```kotlin
package com.example.atv.di

import com.example.atv.data.local.secure.IptvCredentialsStoreImpl
import com.example.atv.domain.repository.IptvCredentialsStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SecureStorageModule {
    @Binds
    @Singleton
    abstract fun bindIptvCredentialsStore(impl: IptvCredentialsStoreImpl): IptvCredentialsStore
}
```

- [ ] **Step 6: Run the device test to verify it passes**

Run: `./studio-gradlew :app:connectedAndroidTest --tests "com.example.atv.data.local.secure.IptvCredentialsStoreImplTest"`
Expected: PASS, 4 tests.

- [ ] **Step 7: Run JVM tests to confirm no regression**

Run: `./studio-gradlew :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/kotlin/com/example/atv/domain/repository/IptvCredentialsStore.kt \
        app/src/main/kotlin/com/example/atv/data/local/secure/IptvCredentialsStoreImpl.kt \
        app/src/main/kotlin/com/example/atv/di/SecureStorageModule.kt \
        app/src/androidTest/kotlin/com/example/atv/data/local/secure/IptvCredentialsStoreImplTest.kt
git commit -m "feat(005): add IptvCredentialsStore over EncryptedSharedPreferences"
```

---

### Task 6: Add `DeviceDefaultsProvider`

**Files:**
- Create: `app/src/main/kotlin/com/example/atv/data/epg/DeviceDefaultsProvider.kt`
- Create: `app/src/test/kotlin/com/example/atv/data/epg/DeviceDefaultsProviderTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/kotlin/com/example/atv/data/epg/DeviceDefaultsProviderTest.kt`:

```kotlin
package com.example.atv.data.epg

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Random

class DeviceDefaultsProviderTest {

    private fun newProvider(
        seed: Long = 42L,
        lanIp: String? = "10.0.0.5",
    ) = DefaultDeviceDefaultsProvider(
        random = Random(seed),
        lanIpSource = { lanIp },
    )

    @Test
    fun `userId and password are empty by default`() {
        val c = newProvider().generate()
        assertEquals("", c.userId)
        assertEquals("", c.password)
    }

    @Test
    fun `stbId is 32 hex chars`() {
        val c = newProvider().generate()
        assertEquals(32, c.stbId.length)
        assertTrue(c.stbId.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun `mac is in RFC 7042 documentation range`() {
        val c = newProvider().generate()
        assertTrue(
            c.mac.startsWith("00:00:5E:00:53:"),
            "mac=${c.mac}",
        )
        // Last byte is 2 hex chars.
        assertEquals(17, c.mac.length)
    }

    @Test
    fun `ip uses lan source when available`() {
        val c = newProvider(lanIp = "10.20.30.40").generate()
        assertEquals("10.20.30.40", c.ip)
    }

    @Test
    fun `ip falls back to RFC 5737 documentation range when lan source returns null`() {
        val c = newProvider(lanIp = null).generate()
        assertEquals("192.0.2.1", c.ip)
    }

    @Test
    fun `authServerUrl uses the operator default`() {
        val c = newProvider().generate()
        assertEquals("http://itv.jsinfo.net:8298", c.authServerUrl)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./studio-gradlew :app:testDebugUnitTest --tests "com.example.atv.data.epg.DeviceDefaultsProviderTest"`
Expected: FAIL — `DeviceDefaultsProvider` does not exist.

- [ ] **Step 3: Create the provider**

Create `app/src/main/kotlin/com/example/atv/data/epg/DeviceDefaultsProvider.kt`:

```kotlin
package com.example.atv.data.epg

import android.content.Context
import com.example.atv.domain.model.IptvCredentials
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.Inet4Address
import java.net.NetworkInterface
import java.security.SecureRandom
import java.util.Random
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Generates first-open defaults for the IPTV setup form. Auto-fills STB ID, IP,
 * MAC, and the operator auth server URL; UserID and Password start empty (the
 * user must enter them).
 */
interface DeviceDefaultsProvider {
    fun generate(): IptvCredentials
}

@Singleton
class DefaultDeviceDefaultsProvider @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : DeviceDefaultsProvider {

    // Test seam: construct with a deterministic Random and a synthetic LAN source.
    internal constructor(
        random: Random,
        lanIpSource: () -> String?,
    ) : this(context = noContext) {
        this.randomOverride = random
        this.lanIpSourceOverride = lanIpSource
    }

    @Volatile
    private var randomOverride: Random? = null
    @Volatile
    private var lanIpSourceOverride: (() -> String?)? = null

    override fun generate(): IptvCredentials {
        val rnd = randomOverride ?: SecureRandom()
        val ipSource = lanIpSourceOverride ?: ::detectLanIp
        return IptvCredentials(
            userId = "",
            password = "",
            stbId = randomHex(rnd, 32),
            ip = ipSource() ?: FALLBACK_IP,
            mac = "00:00:5E:00:53:%02X".format(rnd.nextInt(0x100)),
            authServerUrl = DEFAULT_AUTH_SERVER_URL,
        )
    }

    private fun randomHex(rnd: Random, lengthChars: Int): String {
        val bytes = ByteArray(lengthChars / 2)
        rnd.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun detectLanIp(): String? = try {
        NetworkInterface.getNetworkInterfaces()?.toList()
            ?.asSequence()
            ?.filter { it.isUp && !it.isLoopback }
            ?.flatMap { it.inetAddresses.toList().asSequence() }
            ?.filterIsInstance<Inet4Address>()
            ?.firstOrNull()
            ?.hostAddress
    } catch (_: Exception) {
        null
    }

    companion object {
        const val FALLBACK_IP = "192.0.2.1"   // RFC 5737 TEST-NET-1
        const val DEFAULT_AUTH_SERVER_URL = "http://itv.jsinfo.net:8298"

        // Sentinel context for the test-seam secondary constructor.
        // The seam path never touches `context`, so an unsafe cast is acceptable.
        @Suppress("CAST_NEVER_SUCCEEDS")
        private val noContext: Context = null as Context
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./studio-gradlew :app:testDebugUnitTest --tests "com.example.atv.data.epg.DeviceDefaultsProviderTest"`
Expected: PASS, 6 tests.

- [ ] **Step 5: Hilt binding via @Binds**

Add to `app/src/main/kotlin/com/example/atv/di/EpgModule.kt`, inside the existing `abstract class EpgModule`:

```kotlin
@Binds
@Singleton
abstract fun bindDeviceDefaultsProvider(
    impl: DefaultDeviceDefaultsProvider
): DeviceDefaultsProvider
```

Add the corresponding imports at the top of the file.

- [ ] **Step 6: Verify build**

Run: `./studio-gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/com/example/atv/data/epg/DeviceDefaultsProvider.kt \
        app/src/main/kotlin/com/example/atv/di/EpgModule.kt \
        app/src/test/kotlin/com/example/atv/data/epg/DeviceDefaultsProviderTest.kt
git commit -m "feat(005): add DeviceDefaultsProvider with auto-gen STB/IP/MAC"
```

---

## Phase 3: Channel fetcher + import use case + auth refactor

This phase has an architectural shift: `CtcAuthClient` moves from "Hilt-injected with static `DeviceProfile` + `authServerUrl`" (the spec 004 shape) to "stateless, takes credentials at call time". Same for `CtcEpgProvider` — the `recommpara` query parameter currently sourced from the injected `DeviceProfile.userId` becomes a call-time argument. Without this shift, every `CtcAuthClient.login()` would still use the empty sentinel credentials Phase 1+2 left in `EpgNetworkModule`, so no real auth would ever happen.

The refactor is mechanical (move constructor args to method args) but touches Phase 2's `CtcAuthClient` and its Phase 2 tests. The expanded plan walks each touched file.

### Task 7: Refactor `CtcAuthClient` to take credentials at call time

**Files:**
- Modify: `app/src/main/kotlin/com/example/atv/data/epg/CtcAuthClient.kt`
- Modify: `app/src/test/kotlin/com/example/atv/data/epg/CtcAuthClientTest.kt`
- Delete: the `DeviceProfile` data class (move to `IptvCredentials` already added in Phase 2)

- [ ] **Step 1: Update the existing test to use the new signature**

In `app/src/test/kotlin/com/example/atv/data/epg/CtcAuthClientTest.kt`, replace the existing `device` field and all `CtcAuthClient(http, authBase(), device)` constructions with the new shape. The test now constructs an empty-arg `CtcAuthClient(http)` and passes credentials per-call.

Replace the top of the class through `tearDown()` with:

```kotlin
class CtcAuthClientTest {

    private lateinit var server: MockWebServer
    private lateinit var http: OkHttpClient
    private lateinit var client: CtcAuthClient

    private fun creds(): IptvCredentials = IptvCredentials(
        userId = EpgFixtures.USER_ID,
        password = EpgFixtures.PASSWORD,
        stbId = EpgFixtures.STB_ID,
        ip = EpgFixtures.IP,
        mac = EpgFixtures.MAC,
        authServerUrl = server.url("/").toString().removeSuffix("/"),
    )

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        http = OkHttpClient.Builder().build()
        client = CtcAuthClient(http)
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }
```

Then in each `@Test` method, replace every `CtcAuthClient(http, authBase(), device).login()` with `client.login(creds())`, and every `val client = CtcAuthClient(http, authBase(), device)` with using the field `client`. The "login returns Failure rather than throwing on network error" test still does `server.shutdown()` before calling `client.login(creds())` — but `creds()` reads `server.url(...)` so capture the URL before shutdown:

```kotlin
@Test
fun `login returns Failure rather than throwing on network error`() = runTest {
    val c = creds()                    // capture URL BEFORE shutdown
    server.shutdown()
    val result = client.login(c)
    assertTrue(result is LoginResult.Failure, "got $result")
    assertNotNull((result as LoginResult.Failure).reason)
}
```

Also add an import line at the top of the test file:

```kotlin
import com.example.atv.domain.model.IptvCredentials
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./studio-gradlew :app:testDebugUnitTest --tests "com.example.atv.data.epg.CtcAuthClientTest"`
Expected: FAIL — `CtcAuthClient` constructor still takes 3 args and `login()` takes no args.

- [ ] **Step 3: Refactor `CtcAuthClient`**

Replace `app/src/main/kotlin/com/example/atv/data/epg/CtcAuthClient.kt` body. The class becomes single-field (just `baseHttp`); `login()` takes `IptvCredentials`; all `device`/`authServerUrl` references become call-local. The `DeviceProfile` data class is deleted from this file.

```kotlin
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
 */
@Singleton
class CtcAuthClient @Inject constructor(
    private val baseHttp: OkHttpClient,
) {

    /**
     * Random-seed source for the 8-digit `rand` field in the 3DES authenticator
     * plaintext. Production uses `System.nanoTime()`; tests overwrite for determinism.
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
        http.newCall(req).executeIo().use { resp -> resp.body?.string() }
    }

    private fun parseJsessionFromHeaders(setCookieHeaders: List<String>): String? {
        for (h in setCookieHeaders) {
            val m = Regex("JSESSIONID=([^;]+)").find(h)
            if (m != null) return m.groupValues[1]
        }
        return null
    }

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

internal class InMemoryCookieJar : CookieJar {
    private val store = mutableListOf<Cookie>()

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
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
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./studio-gradlew :app:testDebugUnitTest --tests "com.example.atv.data.epg.CtcAuthClientTest"`
Expected: PASS, all 9 tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/example/atv/data/epg/CtcAuthClient.kt \
        app/src/test/kotlin/com/example/atv/data/epg/CtcAuthClientTest.kt
git commit -m "refactor(005): CtcAuthClient takes credentials at call time"
```

---

### Task 8: Refactor `CtcEpgProvider` to take credentials at fetch time

**Files:**
- Modify: `app/src/main/kotlin/com/example/atv/data/epg/CtcEpgProvider.kt`
- Modify: `app/src/test/kotlin/com/example/atv/data/epg/CtcEpgProviderTest.kt`

`CtcEpgProvider` currently injects `DeviceProfile` and `CtcAuthClient` (which itself injected `DeviceProfile` + `authServerUrl`). After Task 7, `CtcAuthClient` no longer injects them. `CtcEpgProvider` must do the same — drop the `DeviceProfile` field and instead read credentials from the store inside `fetchPrograms`.

The `EpgProvider` interface signature in `domain/repository/EpgProvider.kt` is **unchanged**: `suspend fun fetchPrograms(channelCode: String, dateOffset: Int): Result<List<Program>>`. Consumers (the `PlaybackViewModel` from spec 004) keep working untouched. Credentials are read inside the impl, not surfaced through the interface.

- [ ] **Step 1: Update Phase 2 test to use the new constructor shape**

In `app/src/test/kotlin/com/example/atv/data/epg/CtcEpgProviderTest.kt`, replace the `private val device = DeviceProfile(...)` field with a credentials store mock, and update each `CtcEpgProvider(authClient, http, device, clock)` construction.

Top of the class:

```kotlin
class CtcEpgProviderTest {

    private lateinit var server: MockWebServer
    private lateinit var http: OkHttpClient
    private lateinit var authClient: CtcAuthClient
    private lateinit var credentialsStore: IptvCredentialsStore

    private val creds = IptvCredentials(
        userId = "1234567890123",
        password = "000000",
        stbId = "0".repeat(32),
        ip = "192.0.2.1",
        mac = "00:00:5E:00:53:01",
        authServerUrl = "http://example.com:8298",
    )

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        http = OkHttpClient.Builder().build()
        authClient = mockk()
        credentialsStore = mockk {
            coEvery { read() } returns creds
        }
    }
```

Add the imports:

```kotlin
import com.example.atv.domain.model.IptvCredentials
import com.example.atv.domain.repository.IptvCredentialsStore
```

Replace every `CtcEpgProvider(authClient, http, device, ...)` with `CtcEpgProvider(authClient, http, credentialsStore, ...)`.

Replace every `coEvery { authClient.login() } returns successLogin()` with `coEvery { authClient.login(any()) } returns successLogin()`.

- [ ] **Step 2: Refactor `CtcEpgProvider`**

In `app/src/main/kotlin/com/example/atv/data/epg/CtcEpgProvider.kt`:

Replace the import line `import com.example.atv.data.epg.DeviceProfile` (the file is in the same package, so it's actually a forward reference — search for `DeviceProfile` in the file and remove all uses). Add:

```kotlin
import com.example.atv.domain.model.IptvCredentials
import com.example.atv.domain.repository.IptvCredentialsStore
```

Change the primary constructor:

```kotlin
@Singleton
class CtcEpgProvider @Inject constructor(
    private val authClient: CtcAuthClient,
    private val http: OkHttpClient,
    private val credentialsStore: IptvCredentialsStore,
    private val clock: Clock,
) : EpgProvider {
```

Change the secondary test-seam constructor to match:

```kotlin
internal constructor(
    authClient: CtcAuthClient,
    http: OkHttpClient,
    credentialsStore: IptvCredentialsStore,
    clock: Clock,
    maxCacheEntries: Int,
) : this(authClient, http, credentialsStore, clock) {
    this.maxCacheEntriesOverride = maxCacheEntries
}
```

Modify `ensureSession()` to read credentials each call:

```kotlin
private suspend fun ensureSession(): LoginResult.Success {
    session?.let { return it }
    return relogin()
}

private suspend fun relogin(): LoginResult.Success {
    val creds = credentialsStore.read()
        ?: throw IOException("login failed: no credentials")
    if (!creds.isComplete) {
        throw IOException("login failed: incomplete credentials")
    }
    val r = authClient.login(creds)
    if (r is LoginResult.Success) {
        session = r
        return r
    }
    throw IOException("login failed: ${(r as LoginResult.Failure).reason}")
}
```

In `buildPrevueUrl`, the `recommpara` query parameter previously used `device.userId`. Read it from a session-cached credentials snapshot (capture in `relogin()`):

```kotlin
@Volatile
private var cachedUserId: String = ""
```

In `relogin()` after `session = r`, add `cachedUserId = creds.userId`.

In `buildPrevueUrl`, replace `userId=${device.userId}` with `userId=$cachedUserId`.

- [ ] **Step 3: Run the test to verify it passes**

Run: `./studio-gradlew :app:testDebugUnitTest --tests "com.example.atv.data.epg.CtcEpgProviderTest"`
Expected: PASS, all 12 tests.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/example/atv/data/epg/CtcEpgProvider.kt \
        app/src/test/kotlin/com/example/atv/data/epg/CtcEpgProviderTest.kt
git commit -m "refactor(005): CtcEpgProvider reads credentials from store"
```

---

### Task 9: Remove sentinel providers from `EpgNetworkModule`

**Files:**
- Modify: `app/src/main/kotlin/com/example/atv/di/EpgModule.kt`

After Tasks 7-8, nothing in the Hilt graph injects `DeviceProfile` or `@Named("authServerUrl")` anymore. The two sentinel providers from spec 004 (which carried the privacy-guardrail `TODO(005)` markers) are now dead code.

- [ ] **Step 1: Delete the two providers**

In `app/src/main/kotlin/com/example/atv/di/EpgModule.kt`, delete `provideDeviceProfile()` and `provideAuthServerUrl()` from `EpgNetworkModule`. The `provideOkHttpClient()` provider stays.

Remove the now-unused imports:

```kotlin
import com.example.atv.data.epg.DeviceProfile      // delete
import javax.inject.Named                          // delete (if no other Named uses)
```

Also delete the explanatory docstring block that justified the sentinels.

- [ ] **Step 2: Verify Hilt graph still resolves**

Run: `./studio-gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`. If Hilt reports `MissingBinding`, a refactor missed a `DeviceProfile` or `@Named("authServerUrl")` reference — grep and fix.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/example/atv/di/EpgModule.kt
git commit -m "refactor(005): remove sentinel DeviceProfile/authServerUrl Hilt providers"
```

---

### Task 10: Add `CtcChannelFetcher`

**Files:**
- Create: `app/src/main/kotlin/com/example/atv/data/epg/CtcChannelFetcher.kt`
- Create: `app/src/test/kotlin/com/example/atv/data/epg/CtcChannelFetcherTest.kt`

Ports the `frameset_builder.jsp` POST + `get_channel_info_mapping.jsp` GET from `iptv_client.py` lines 377-402. Returns a list of wire DTOs that the use case maps into domain `Channel`s.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/kotlin/com/example/atv/data/epg/CtcChannelFetcherTest.kt`:

```kotlin
package com.example.atv.data.epg

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CtcChannelFetcherTest {

    private lateinit var server: MockWebServer
    private lateinit var http: OkHttpClient
    private lateinit var fetcher: CtcChannelFetcher

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        http = OkHttpClient.Builder().build()
        fetcher = CtcChannelFetcher(http)
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun successLogin(): LoginResult.Success = LoginResult.Success(
        epgLbBase = server.url("/iptvepg/function/").toString(),
        jsessionId = "JS-1",
        config = mapOf("UserToken" to "tok"),
        userToken = "tok",
    )

    private val twoChannelsHtml = """
        <script>
        jsSetConfig('Channel','ChannelID=ch00000000000000000001,ChannelName="CCTV-1",UserChannelID=001,ChannelURL=igmp://239.0.0.1:8000,ChannelSDP="",TimeShift=0,TimeShiftURL="",TimeShiftLength=0,ChannelType=0');
        jsSetConfig('Channel','ChannelID=ch00000000000000000002,ChannelName="CCTV-2",UserChannelID=002,ChannelURL=igmp://239.0.0.2:8000,ChannelSDP="",TimeShift=0,TimeShiftURL="",TimeShiftLength=0,ChannelType=0');
        </script>
    """.trimIndent()

    private val mappingJson = """
        {"channelMixnoMapping":"1:001,2:002","other":"ignored"}
    """.trimIndent()

    @Test
    fun `fetch returns mapped channels on happy path`() = runTest {
        server.enqueue(MockResponse().setBody(twoChannelsHtml))
        server.enqueue(MockResponse().setBody(mappingJson))

        val result = fetcher.fetch(successLogin())
        assertTrue(result.isSuccess)
        val channels = result.getOrNull()!!
        assertEquals(2, channels.size)
        assertEquals("ch00000000000000000001", channels[0].channelId)
        assertEquals("CCTV-1", channels[0].channelName)
        assertEquals(1, channels[0].displayNumber)
        assertEquals("CCTV-2", channels[1].channelName)
        assertEquals(2, channels[1].displayNumber)
    }

    @Test
    fun `fetch posts to frameset_builder jsp with required form fields and JSESSIONID`() = runTest {
        server.enqueue(MockResponse().setBody(twoChannelsHtml))
        server.enqueue(MockResponse().setBody(mappingJson))

        fetcher.fetch(successLogin())

        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertTrue(req.path!!.endsWith("/iptvepg/function/frameset_builder.jsp"))
        val body = req.body.readUtf8()
        assertTrue(body.contains("BUILD_ACTION=FRAMESET_BUILDER"))
        assertTrue(body.contains("MAIN_WIN_SRC="))
        assertTrue(body.contains("NEED_UPDATE_STB=1"))
        assertTrue(req.getHeader("Cookie").orEmpty().contains("JSESSIONID=JS-1"))
    }

    @Test
    fun `fetch returns success with empty list when channels payload has no entries`() = runTest {
        server.enqueue(MockResponse().setBody("<html><body>no channels here</body></html>"))
        server.enqueue(MockResponse().setBody("""{"channelMixnoMapping":""}"""))

        val result = fetcher.fetch(successLogin())
        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrNull()!!.size)
    }

    @Test
    fun `fetch falls back to index+1 numbering when mapping is missing for a channel`() = runTest {
        server.enqueue(MockResponse().setBody(twoChannelsHtml))
        server.enqueue(MockResponse().setBody("""{"channelMixnoMapping":""}"""))

        val result = fetcher.fetch(successLogin())
        val channels = result.getOrNull()!!
        assertEquals(1, channels[0].displayNumber)
        assertEquals(2, channels[1].displayNumber)
    }

    @Test
    fun `fetch returns failure on network error`() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        val result = fetcher.fetch(successLogin())
        assertTrue(result.isFailure)
    }

    @Test
    fun `fetch returns failure on HTTP 5xx`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))

        val result = fetcher.fetch(successLogin())
        assertTrue(result.isFailure)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./studio-gradlew :app:testDebugUnitTest --tests "com.example.atv.data.epg.CtcChannelFetcherTest"`
Expected: FAIL — `CtcChannelFetcher` does not exist.

- [ ] **Step 3: Create the fetcher**

Create `app/src/main/kotlin/com/example/atv/data/epg/CtcChannelFetcher.kt`:

```kotlin
package com.example.atv.data.epg

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wire DTO for a CTC channel as returned by `frameset_builder.jsp`. Mapped to
 * the domain [com.example.atv.domain.model.Channel] by [ImportCtcChannelsUseCase].
 */
data class CtcChannelEntry(
    val channelId: String,
    val channelName: String,
    val userChannelId: String,
    val channelUrl: String,
    val displayNumber: Int,
)

/**
 * Fetches the operator channel list. Two HTTP calls:
 *   1. POST `frameset_builder.jsp` — returns HTML with embedded `jsSetConfig('Channel', '...')`
 *      blocks that parse to channel records.
 *   2. GET `get_channel_info_mapping.jsp` — returns JSON with `channelMixnoMapping`
 *      that maps display numbers ("001") to UserChannelID values.
 *
 * Ports `IPTVClient.fetch_channels` + `fetch_channel_mapping` from
 * `~/Documents/itv-reverse/iptv_client.py` lines 377-402.
 */
@Singleton
class CtcChannelFetcher @Inject constructor(
    private val http: OkHttpClient,
) {
    suspend fun fetch(session: LoginResult.Success): Result<List<CtcChannelEntry>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val rawChannels = fetchFrameset(session)
                val mapping = runCatching { fetchMapping(session) }.getOrDefault(emptyMap())
                val byUserCh = mapping.entries.associate { (display, userCh) ->
                    userCh to display.toIntOrNull()
                }
                rawChannels.mapIndexed { idx, raw ->
                    val displayNumber = byUserCh[raw.userChannelId] ?: (idx + 1)
                    raw.copy(displayNumber = displayNumber)
                }
            }.onFailure { Timber.d(it, "CTC fetch channels failed") }
        }

    private fun fetchFrameset(session: LoginResult.Success): List<CtcChannelEntry> {
        val url = "${session.epgLbBase}frameset_builder.jsp".toHttpUrl()
        val form = FormBody.Builder()
            .add("BUILD_ACTION", "FRAMESET_BUILDER")
            .add("MAIN_WIN_SRC", "/iptvepg/frame310/first_channel_play.jsp?tempno=777")
            .add("NEED_UPDATE_STB", "1")
            .build()
        val req = Request.Builder()
            .url(url)
            .post(form)
            .header("Cookie", "JSESSIONID=${session.jsessionId}")
            .build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("frameset_builder HTTP ${resp.code}")
            return CtcResponseParsers.parseChannels(resp.body?.string().orEmpty())
        }
    }

    private fun fetchMapping(session: LoginResult.Success): Map<String, String> {
        // mapping endpoint lives at iptvepg/frame224/... not under function/.
        val root = session.epgLbBase.removeSuffix("function/")
        val url = "${root}frame224/datas/get_channel_info_mapping.jsp".toHttpUrl()
        val req = Request.Builder()
            .url(url)
            .header("Cookie", "JSESSIONID=${session.jsessionId}")
            .build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return emptyMap()
            return CtcResponseParsers.parseMixnoMapping(resp.body?.string().orEmpty())
        }
    }
}
```

- [ ] **Step 4: Add the two parsing helpers to `CtcResponseParsers`**

In `app/src/main/kotlin/com/example/atv/data/epg/CtcResponseParsers.kt`, add two functions inside the `object`:

```kotlin
/**
 * Parse the `jsSetConfig('Channel', '...')` blocks emitted by `frameset_builder.jsp`.
 * Each block holds a comma-separated list of `key=value` pairs (with `value` optionally
 * double-quoted). Mirrors the python `_parse_channels` helper at iptv_client.py:251.
 */
fun parseChannels(html: String): List<CtcChannelEntry> {
    val re = Regex("""jsSetConfig\(\s*'Channel'\s*,\s*'(.*?)'\s*\)""")
    return re.findAll(html).mapNotNull { m ->
        val body = m.groupValues[1]
        val kv: MutableMap<String, String> = mutableMapOf()
        for (piece in body.split(",")) {
            val idx = piece.indexOf('=')
            if (idx < 0) continue
            val k = piece.substring(0, idx).trim()
            val v = piece.substring(idx + 1).trim().trim('"')
            kv[k] = v
        }
        if (kv.isEmpty()) return@mapNotNull null
        CtcChannelEntry(
            channelId = kv["ChannelID"].orEmpty(),
            channelName = kv["ChannelName"].orEmpty(),
            userChannelId = kv["UserChannelID"].orEmpty(),
            channelUrl = kv["ChannelURL"].orEmpty(),
            displayNumber = 0,           // filled in by the fetcher using the mapping
        )
    }.toList()
}

/**
 * Parse the `channelMixnoMapping` field from `get_channel_info_mapping.jsp`.
 * The value is comma-separated `display:user_channel_id` pairs. Mirrors
 * python `_parse_mixno_mapping` (iptv_client.py:285).
 */
fun parseMixnoMapping(jsonText: String): Map<String, String> {
    val obj = runCatching { AppJson.parseToJsonElement(jsonText) }.getOrNull() ?: return emptyMap()
    val raw = obj.jsonObject["channelMixnoMapping"]?.jsonPrimitive?.contentOrNull.orEmpty()
    val out = mutableMapOf<String, String>()
    for (piece in raw.split(",")) {
        val idx = piece.indexOf(':')
        if (idx < 0) continue
        val display = piece.substring(0, idx).trim().trim('"')
        val userCh = piece.substring(idx + 1).trim().trim('"')
        if (display.isNotEmpty()) out[display] = userCh
    }
    return out
}
```

Add the necessary imports to `CtcResponseParsers.kt`:

```kotlin
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
```

- [ ] **Step 5: Add tests for the new parsers**

Append to `app/src/test/kotlin/com/example/atv/data/epg/CtcResponseParsersTest.kt`:

```kotlin
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
```

- [ ] **Step 6: Run all tests**

Run: `./studio-gradlew :app:testDebugUnitTest`
Expected: PASS, full suite including the 6 new fetcher tests and 5 new parser tests.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/com/example/atv/data/epg/CtcChannelFetcher.kt \
        app/src/main/kotlin/com/example/atv/data/epg/CtcResponseParsers.kt \
        app/src/test/kotlin/com/example/atv/data/epg/CtcChannelFetcherTest.kt \
        app/src/test/kotlin/com/example/atv/data/epg/CtcResponseParsersTest.kt
git commit -m "feat(005): add CtcChannelFetcher + parseChannels/parseMixnoMapping helpers"
```

---

### Task 11: Replace `testSetConfigured` with public `markConfigured`

**Files:**
- Modify: `app/src/main/kotlin/com/example/atv/data/epg/CtcEpgProvider.kt`
- Modify: `app/src/test/kotlin/com/example/atv/data/epg/CtcEpgProviderTest.kt`

The spec-004 `internal fun testSetConfigured(value: Boolean)` was named "test-only" because spec 004 had no production trigger. Spec 005's `ImportCtcChannelsUseCase` IS that trigger. Rename to communicate intent.

- [ ] **Step 1: Rename in `CtcEpgProvider`**

In `app/src/main/kotlin/com/example/atv/data/epg/CtcEpgProvider.kt`, replace:

```kotlin
/** Test-only hatch to flip configured state without going through 005's trigger. */
internal fun testSetConfigured(value: Boolean) {
    _isConfigured.value = value
}
```

with:

```kotlin
/**
 * Flip the configured signal. Called by [ImportCtcChannelsUseCase] after a
 * successful login + channel fetch. Tests can call this directly to set up
 * provider state without going through the full import flow.
 */
internal fun markConfigured(value: Boolean) {
    _isConfigured.value = value
}
```

- [ ] **Step 2: Update test call sites**

In `app/src/test/kotlin/com/example/atv/data/epg/CtcEpgProviderTest.kt`, replace every `provider.testSetConfigured(true)` with `provider.markConfigured(true)`.

- [ ] **Step 3: Run tests**

Run: `./studio-gradlew :app:testDebugUnitTest --tests "com.example.atv.data.epg.CtcEpgProviderTest"`
Expected: PASS, 12 tests.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/example/atv/data/epg/CtcEpgProvider.kt \
        app/src/test/kotlin/com/example/atv/data/epg/CtcEpgProviderTest.kt
git commit -m "refactor(005): rename CtcEpgProvider.testSetConfigured to markConfigured"
```

---

### Task 12: Add `ImportResult` sealed class

**Files:**
- Create: `app/src/main/kotlin/com/example/atv/domain/usecase/ImportResult.kt`

Pure data class — no test needed (covered by `ImportCtcChannelsUseCaseTest` in Task 13).

- [ ] **Step 1: Create the file**

Create `app/src/main/kotlin/com/example/atv/domain/usecase/ImportResult.kt`:

```kotlin
package com.example.atv.domain.usecase

/**
 * Outcome of [ImportCtcChannelsUseCase]. Communicates the exact reason for
 * failure so the UI can render granular status messages.
 */
sealed class ImportResult {
    /** Login + fetch + save succeeded; N channels persisted. */
    data class Success(val importedCount: Int) : ImportResult()

    /** Login step failed; nothing was changed. */
    data class LoginFailure(val reason: String) : ImportResult()

    /** Login succeeded but channel fetch / parse failed; nothing was changed. */
    data class FetchFailure(val reason: String) : ImportResult()

    /** Both steps succeeded but the operator returned zero channels; nothing was changed. */
    object NoChannelsReturned : ImportResult()
}
```

- [ ] **Step 2: Verify compilation**

Run: `./studio-gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/example/atv/domain/usecase/ImportResult.kt
git commit -m "feat(005): add ImportResult sealed class"
```

---

### Task 13: Add `ImportCtcChannelsUseCase`

**Files:**
- Create: `app/src/main/kotlin/com/example/atv/domain/usecase/ImportCtcChannelsUseCase.kt`
- Create: `app/src/test/kotlin/com/example/atv/domain/usecase/ImportCtcChannelsUseCaseTest.kt`

The orchestrator. Reads credentials, logs in, fetches channels, persists, flips `markConfigured(true)`. Single source of truth for the import flow — shared between the manual "Test & import" button (Phase 4) and the auto-bootstrapper (Phase 5).

- [ ] **Step 1: Write the failing test**

Create `app/src/test/kotlin/com/example/atv/domain/usecase/ImportCtcChannelsUseCaseTest.kt`:

```kotlin
package com.example.atv.domain.usecase

import com.example.atv.data.epg.CtcAuthClient
import com.example.atv.data.epg.CtcChannelEntry
import com.example.atv.data.epg.CtcChannelFetcher
import com.example.atv.data.epg.CtcEpgProvider
import com.example.atv.data.epg.LoginResult
import com.example.atv.domain.model.Channel
import com.example.atv.domain.model.IptvCredentials
import com.example.atv.domain.repository.ChannelRepository
import com.example.atv.domain.repository.IptvCredentialsStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ImportCtcChannelsUseCaseTest {

    private lateinit var authClient: CtcAuthClient
    private lateinit var fetcher: CtcChannelFetcher
    private lateinit var repo: ChannelRepository
    private lateinit var store: IptvCredentialsStore
    private lateinit var provider: CtcEpgProvider
    private lateinit var useCase: ImportCtcChannelsUseCase

    private val creds = IptvCredentials(
        userId = "1234567890123",
        password = "000000",
        stbId = "0".repeat(32),
        ip = "192.0.2.1",
        mac = "00:00:5E:00:53:01",
        authServerUrl = "http://example.com:8298",
    )

    private fun login() = LoginResult.Success(
        epgLbBase = "http://lb/iptvepg/function/",
        jsessionId = "JS",
        config = emptyMap(),
        userToken = "tok",
    )

    @BeforeEach
    fun setUp() {
        authClient = mockk()
        fetcher = mockk()
        repo = mockk()
        store = mockk()
        provider = mockk(relaxed = true)
        useCase = ImportCtcChannelsUseCase(authClient, fetcher, repo, store, provider)
    }

    @Test
    fun `returns LoginFailure when credentials are missing`() = runTest {
        coEvery { store.read() } returns null

        val r = useCase()
        assertTrue(r is ImportResult.LoginFailure)
        assertTrue((r as ImportResult.LoginFailure).reason.contains("no credentials", ignoreCase = true))
    }

    @Test
    fun `returns LoginFailure when credentials are incomplete`() = runTest {
        coEvery { store.read() } returns creds.copy(password = "")

        val r = useCase()
        assertTrue(r is ImportResult.LoginFailure)
        assertTrue((r as ImportResult.LoginFailure).reason.contains("incomplete", ignoreCase = true))
    }

    @Test
    fun `returns LoginFailure when login itself fails`() = runTest {
        coEvery { store.read() } returns creds
        coEvery { authClient.login(creds) } returns LoginResult.Failure("auth refused")

        val r = useCase()
        assertTrue(r is ImportResult.LoginFailure)
        assertEquals("auth refused", (r as ImportResult.LoginFailure).reason)
    }

    @Test
    fun `returns FetchFailure when channel fetch fails after a successful login`() = runTest {
        coEvery { store.read() } returns creds
        coEvery { authClient.login(creds) } returns login()
        coEvery { fetcher.fetch(any()) } returns Result.failure(RuntimeException("boom"))

        val r = useCase()
        assertTrue(r is ImportResult.FetchFailure)
        assertEquals("boom", (r as ImportResult.FetchFailure).reason)
    }

    @Test
    fun `returns NoChannelsReturned when fetch succeeds with empty list`() = runTest {
        coEvery { store.read() } returns creds
        coEvery { authClient.login(creds) } returns login()
        coEvery { fetcher.fetch(any()) } returns Result.success(emptyList())

        val r = useCase()
        assertTrue(r is ImportResult.NoChannelsReturned)
    }

    @Test
    fun `returns Success and persists mapped channels and flips isConfigured`() = runTest {
        val entries = listOf(
            CtcChannelEntry("ch1", "CCTV-1", "001", "igmp://239.0.0.1", displayNumber = 1),
            CtcChannelEntry("ch2", "CCTV-2", "002", "igmp://239.0.0.2", displayNumber = 2),
        )
        coEvery { store.read() } returns creds
        coEvery { authClient.login(creds) } returns login()
        coEvery { fetcher.fetch(any()) } returns Result.success(entries)
        val saved = slot<List<Channel>>()
        coEvery { repo.savePlaylistChannels(capture(saved)) } just runs

        val r = useCase()
        assertTrue(r is ImportResult.Success)
        assertEquals(2, (r as ImportResult.Success).importedCount)

        // Mapping check
        val out = saved.captured
        assertEquals(1, out[0].number)
        assertEquals("CCTV-1", out[0].name)
        assertEquals("igmp://239.0.0.1", out[0].streamUrl)
        assertEquals("ch1", out[0].channelCode)
        assertEquals(2, out[1].number)
        assertEquals("ch2", out[1].channelCode)

        coVerify { provider.markConfigured(true) }
    }

    @Test
    fun `does not flip isConfigured on any failure path`() = runTest {
        coEvery { store.read() } returns creds
        coEvery { authClient.login(creds) } returns LoginResult.Failure("nope")

        useCase()

        coVerify(exactly = 0) { provider.markConfigured(any()) }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./studio-gradlew :app:testDebugUnitTest --tests "com.example.atv.domain.usecase.ImportCtcChannelsUseCaseTest"`
Expected: FAIL — class does not exist.

- [ ] **Step 3: Create the use case**

Create `app/src/main/kotlin/com/example/atv/domain/usecase/ImportCtcChannelsUseCase.kt`:

```kotlin
package com.example.atv.domain.usecase

import com.example.atv.data.epg.CtcAuthClient
import com.example.atv.data.epg.CtcChannelEntry
import com.example.atv.data.epg.CtcChannelFetcher
import com.example.atv.data.epg.CtcEpgProvider
import com.example.atv.data.epg.LoginResult
import com.example.atv.domain.model.Channel
import com.example.atv.domain.repository.ChannelRepository
import com.example.atv.domain.repository.IptvCredentialsStore
import timber.log.Timber
import javax.inject.Inject

/**
 * Orchestrates a one-shot CTC channel import:
 *
 *   read credentials → login → fetch channels → save → flip isConfigured
 *
 * Single source of truth for the import flow. Called by both the manual
 * "Test & import" button (Phase 4 [IptvSettingsViewModel]) and the auto-bootstrapper
 * at app launch (Phase 5 [IptvSessionBootstrapper]). Returns an [ImportResult]
 * sealed class so callers can render granular status without inspecting exceptions.
 *
 * On a failure at any step, NO existing channels are modified and `isConfigured`
 * is NOT flipped — the user's previous state remains usable.
 */
class ImportCtcChannelsUseCase @Inject constructor(
    private val authClient: CtcAuthClient,
    private val channelFetcher: CtcChannelFetcher,
    private val channelRepository: ChannelRepository,
    private val credentialsStore: IptvCredentialsStore,
    private val epgProvider: CtcEpgProvider,
) {
    suspend operator fun invoke(): ImportResult {
        val creds = credentialsStore.read()
            ?: return ImportResult.LoginFailure("no credentials stored")
        if (!creds.isComplete) {
            return ImportResult.LoginFailure("incomplete credentials")
        }

        val login = authClient.login(creds)
        if (login is LoginResult.Failure) {
            Timber.d("CTC import login failed: %s", login.reason)
            return ImportResult.LoginFailure(login.reason)
        }
        login as LoginResult.Success

        val fetchResult = channelFetcher.fetch(login)
        val entries = fetchResult.getOrElse { t ->
            Timber.d(t, "CTC import fetch failed")
            return ImportResult.FetchFailure(t.message ?: t::class.simpleName.orEmpty())
        }
        if (entries.isEmpty()) {
            return ImportResult.NoChannelsReturned
        }

        val channels = entries.map { it.toDomainChannel() }
        channelRepository.savePlaylistChannels(channels)
        epgProvider.markConfigured(true)
        return ImportResult.Success(channels.size)
    }

    private fun CtcChannelEntry.toDomainChannel(): Channel = Channel(
        number = displayNumber,
        name = channelName,
        streamUrl = channelUrl,
        groupTitle = null,
        logoUrl = null,
        channelCode = channelId,
    )
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./studio-gradlew :app:testDebugUnitTest --tests "com.example.atv.domain.usecase.ImportCtcChannelsUseCaseTest"`
Expected: PASS, 7 tests.

- [ ] **Step 5: Run full unit-test suite**

Run: `./studio-gradlew :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/example/atv/domain/usecase/ImportCtcChannelsUseCase.kt \
        app/src/test/kotlin/com/example/atv/domain/usecase/ImportCtcChannelsUseCaseTest.kt
git commit -m "feat(005): add ImportCtcChannelsUseCase orchestrating login + fetch + save"
```

End of Phase 3.

---

## Phase 4: Settings UI

Adds the user-facing surface: a new `IptvSettingsScreen` reachable from the existing Settings screen via a "IPTV setup" entry. Six `TextField`s for the credential fields, one "Test connection & import channels" button, a status block, and a "Clear IPTV credentials" action. No background work — that's Phase 5.

### Task 14: Add IPTV localized strings

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh/strings.xml`

- [ ] **Step 1: Add English strings**

In `app/src/main/res/values/strings.xml`, before `</resources>`, append:

```xml
<!-- IPTV Setup (spec 005) -->
<string name="iptv_setup_title">IPTV setup</string>
<string name="iptv_setup_subtitle_not_configured">Not configured</string>
<string name="iptv_setup_subtitle_imported">%1$d channels imported</string>
<string name="iptv_setup_subtitle_sync_failed">Last sync failed: %1$s</string>

<string name="iptv_field_user_id">User ID</string>
<string name="iptv_field_password">Password</string>
<string name="iptv_field_stb_id">STB ID</string>
<string name="iptv_field_ip">Local IP</string>
<string name="iptv_field_mac">Local MAC</string>
<string name="iptv_field_auth_server_url">Auth server URL</string>

<string name="iptv_action_test_and_import">Test connection &amp; import channels</string>
<string name="iptv_action_clear_credentials">Clear IPTV credentials</string>

<string name="iptv_status_idle">Ready</string>
<string name="iptv_status_logging_in">Logging in…</string>
<string name="iptv_status_fetching_channels">Fetching channels…</string>
<string name="iptv_status_imported">Imported %1$d channels</string>
<string name="iptv_status_login_failed">Login failed: %1$s</string>
<string name="iptv_status_fetch_failed">Fetch failed: %1$s</string>
<string name="iptv_status_no_channels">No channels returned by the server</string>

<string name="iptv_clear_dialog_title">Clear IPTV credentials?</string>
<string name="iptv_clear_dialog_message">Your stored CTC IPTV credentials will be deleted from the device. Imported channels remain in your playlist until you load a different one.</string>
<string name="iptv_clear_dialog_confirm">Clear</string>

<string name="iptv_validation_stb_length">STB ID must be exactly 32 characters</string>
<string name="iptv_validation_auth_url">Auth server URL must start with http:// or https://</string>
```

- [ ] **Step 2: Add Chinese strings**

In `app/src/main/res/values-zh/strings.xml`, before `</resources>`, append:

```xml
<!-- IPTV Setup (spec 005) -->
<string name="iptv_setup_title">IPTV 设置</string>
<string name="iptv_setup_subtitle_not_configured">未配置</string>
<string name="iptv_setup_subtitle_imported">已导入 %1$d 个频道</string>
<string name="iptv_setup_subtitle_sync_failed">上次同步失败：%1$s</string>

<string name="iptv_field_user_id">用户 ID</string>
<string name="iptv_field_password">密码</string>
<string name="iptv_field_stb_id">机顶盒 ID</string>
<string name="iptv_field_ip">本机 IP</string>
<string name="iptv_field_mac">本机 MAC</string>
<string name="iptv_field_auth_server_url">认证服务器地址</string>

<string name="iptv_action_test_and_import">测试连接并导入频道</string>
<string name="iptv_action_clear_credentials">清除 IPTV 凭据</string>

<string name="iptv_status_idle">就绪</string>
<string name="iptv_status_logging_in">正在登录…</string>
<string name="iptv_status_fetching_channels">正在获取频道列表…</string>
<string name="iptv_status_imported">已导入 %1$d 个频道</string>
<string name="iptv_status_login_failed">登录失败：%1$s</string>
<string name="iptv_status_fetch_failed">获取失败：%1$s</string>
<string name="iptv_status_no_channels">服务器未返回任何频道</string>

<string name="iptv_clear_dialog_title">清除 IPTV 凭据？</string>
<string name="iptv_clear_dialog_message">已保存的 CTC IPTV 凭据将从本机删除。已导入的频道在你加载其他播放列表前会继续保留。</string>
<string name="iptv_clear_dialog_confirm">清除</string>

<string name="iptv_validation_stb_length">机顶盒 ID 必须正好 32 个字符</string>
<string name="iptv_validation_auth_url">认证服务器地址必须以 http:// 或 https:// 开头</string>
```

- [ ] **Step 3: Verify build**

Run: `./studio-gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/values/strings.xml app/src/main/res/values-zh/strings.xml
git commit -m "feat(005): add IPTV setup localized strings (en, zh)"
```

---

### Task 15: Add `IptvSettingsViewModel` + `IptvSettingsUiState`

**Files:**
- Create: `app/src/main/kotlin/com/example/atv/ui/screens/iptv/IptvSettingsUiState.kt`
- Create: `app/src/main/kotlin/com/example/atv/ui/screens/iptv/IptvSettingsViewModel.kt`
- Create: `app/src/test/kotlin/com/example/atv/ui/screens/iptv/IptvSettingsViewModelTest.kt`

- [ ] **Step 1: Create `IptvSettingsUiState`**

Create `app/src/main/kotlin/com/example/atv/ui/screens/iptv/IptvSettingsUiState.kt`:

```kotlin
package com.example.atv.ui.screens.iptv

import com.example.atv.domain.model.IptvCredentials

/**
 * State for the IPTV setup screen. Holds the six editable field values plus
 * import-flow status and confirmation-dialog visibility.
 */
data class IptvSettingsUiState(
    val userId: String = "",
    val password: String = "",
    val stbId: String = "",
    val ip: String = "",
    val mac: String = "",
    val authServerUrl: String = "",
    val importStatus: ImportStatus = ImportStatus.Idle,
    val showClearConfirmation: Boolean = false,
) {
    /** The current form values as an [IptvCredentials] record. */
    val asCredentials: IptvCredentials
        get() = IptvCredentials(userId, password, stbId, ip, mac, authServerUrl)

    /** Form is valid and the "Test & import" button should be enabled. */
    val isFormValid: Boolean
        get() = asCredentials.isComplete && importStatus !is ImportStatus.InProgress
}

/**
 * Real-time status of the "Test & import" flow. The UI maps each variant to a
 * localized string. `Idle` is the initial and post-completion state.
 */
sealed class ImportStatus {
    object Idle : ImportStatus()
    object LoggingIn : ImportStatus()
    object FetchingChannels : ImportStatus()
    data class Success(val importedCount: Int) : ImportStatus()
    data class LoginFailed(val reason: String) : ImportStatus()
    data class FetchFailed(val reason: String) : ImportStatus()
    object NoChannelsReturned : ImportStatus()

    val isInProgress: Boolean
        get() = this is LoggingIn || this is FetchingChannels

    val isTerminal: Boolean
        get() = this is Success || this is LoginFailed ||
            this is FetchFailed || this is NoChannelsReturned
}
```

- [ ] **Step 2: Write the failing test**

Create `app/src/test/kotlin/com/example/atv/ui/screens/iptv/IptvSettingsViewModelTest.kt`:

```kotlin
package com.example.atv.ui.screens.iptv

import com.example.atv.data.epg.DefaultDeviceDefaultsProvider
import com.example.atv.data.epg.DeviceDefaultsProvider
import com.example.atv.domain.model.IptvCredentials
import com.example.atv.domain.repository.IptvCredentialsStore
import com.example.atv.domain.usecase.ImportCtcChannelsUseCase
import com.example.atv.domain.usecase.ImportResult
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Random

@OptIn(ExperimentalCoroutinesApi::class)
class IptvSettingsViewModelTest {

    @MockK
    private lateinit var store: IptvCredentialsStore

    @MockK
    private lateinit var useCase: ImportCtcChannelsUseCase

    private val storedFlow = MutableStateFlow<IptvCredentials?>(null)
    private val testDispatcher = StandardTestDispatcher()

    private fun defaults(): DeviceDefaultsProvider = DefaultDeviceDefaultsProvider(
        random = Random(42),
        lanIpSource = { "10.0.0.5" },
    )

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this, relaxUnitFun = true)
        Dispatchers.setMain(testDispatcher)
        every { store.observe() } returns storedFlow
        coEvery { store.save(any()) } just runs
        coEvery { store.clear() } just runs
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newVm(): IptvSettingsViewModel =
        IptvSettingsViewModel(store, defaults(), useCase)

    @Test
    fun `applies defaults when store is empty`() = runTest {
        val vm = newVm()
        advanceUntilIdle()

        val s = vm.uiState.value
        assertEquals("", s.userId)
        assertEquals("", s.password)
        assertEquals(32, s.stbId.length)
        assertEquals("10.0.0.5", s.ip)
        assertTrue(s.mac.startsWith("00:00:5E:00:53:"))
        assertTrue(s.authServerUrl.startsWith("http://"))
    }

    @Test
    fun `hydrates from store when credentials exist`() = runTest {
        val creds = IptvCredentials(
            userId = "1234567890123",
            password = "000000",
            stbId = "a".repeat(32),
            ip = "10.20.30.40",
            mac = "00:00:5E:00:53:AA",
            authServerUrl = "http://x.com",
        )
        storedFlow.value = creds
        val vm = newVm()
        advanceUntilIdle()

        assertEquals(creds, vm.uiState.value.asCredentials)
    }

    @Test
    fun `setUserId updates state`() = runTest {
        val vm = newVm()
        advanceUntilIdle()

        vm.setUserId("9999999999999")
        assertEquals("9999999999999", vm.uiState.value.userId)
    }

    @Test
    fun `isFormValid false until userId and password filled`() = runTest {
        val vm = newVm()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isFormValid)
        vm.setUserId("1234567890123")
        assertFalse(vm.uiState.value.isFormValid)
        vm.setPassword("000000")
        assertTrue(vm.uiState.value.isFormValid)
    }

    @Test
    fun `testAndImport saves credentials, runs use case, surfaces Success`() = runTest {
        coEvery { useCase() } returns ImportResult.Success(42)
        val vm = newVm()
        advanceUntilIdle()
        vm.setUserId("1234567890123")
        vm.setPassword("000000")

        vm.testAndImport()
        advanceUntilIdle()

        val status = vm.uiState.value.importStatus
        assertTrue(status is ImportStatus.Success)
        assertEquals(42, (status as ImportStatus.Success).importedCount)
        coVerifyOrder {
            store.save(any())
            useCase()
        }
    }

    @Test
    fun `testAndImport surfaces LoginFailed`() = runTest {
        coEvery { useCase() } returns ImportResult.LoginFailure("bad password")
        val vm = newVm()
        advanceUntilIdle()
        vm.setUserId("u").apply { vm.setPassword("p") }

        vm.testAndImport()
        advanceUntilIdle()

        val status = vm.uiState.value.importStatus
        assertTrue(status is ImportStatus.LoginFailed)
        assertEquals("bad password", (status as ImportStatus.LoginFailed).reason)
    }

    @Test
    fun `testAndImport surfaces NoChannelsReturned`() = runTest {
        coEvery { useCase() } returns ImportResult.NoChannelsReturned
        val vm = newVm()
        advanceUntilIdle()
        vm.setUserId("u"); vm.setPassword("p")

        vm.testAndImport()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.importStatus is ImportStatus.NoChannelsReturned)
    }

    @Test
    fun `concurrent testAndImport taps coalesce to one use-case invocation`() = runTest {
        coEvery { useCase() } returns ImportResult.Success(1)
        val vm = newVm()
        advanceUntilIdle()
        vm.setUserId("u"); vm.setPassword("p")

        vm.testAndImport()
        vm.testAndImport()
        vm.testAndImport()
        advanceUntilIdle()

        coVerify(exactly = 1) { useCase() }
    }

    @Test
    fun `clearCredentials shows dialog, confirm wipes store and resets state to defaults`() = runTest {
        storedFlow.value = IptvCredentials(
            userId = "u", password = "p", stbId = "0".repeat(32),
            ip = "i", mac = "m", authServerUrl = "http://x.com",
        )
        val vm = newVm()
        advanceUntilIdle()
        assertEquals("u", vm.uiState.value.userId)

        vm.requestClearCredentials()
        assertTrue(vm.uiState.value.showClearConfirmation)

        vm.confirmClearCredentials()
        advanceUntilIdle()
        coVerify { store.clear() }
        assertFalse(vm.uiState.value.showClearConfirmation)
        // State resets to defaults (UserID/Password empty, defaults reapplied).
        assertEquals("", vm.uiState.value.userId)
        assertEquals("", vm.uiState.value.password)
        assertEquals(32, vm.uiState.value.stbId.length)
    }

    @Test
    fun `dismissClearDialog hides the dialog without clearing`() = runTest {
        val vm = newVm()
        advanceUntilIdle()
        vm.requestClearCredentials()
        vm.dismissClearDialog()

        assertFalse(vm.uiState.value.showClearConfirmation)
        coVerify(exactly = 0) { store.clear() }
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./studio-gradlew :app:testDebugUnitTest --tests "com.example.atv.ui.screens.iptv.IptvSettingsViewModelTest"`
Expected: FAIL — `IptvSettingsViewModel` does not exist.

- [ ] **Step 4: Create the ViewModel**

Create `app/src/main/kotlin/com/example/atv/ui/screens/iptv/IptvSettingsViewModel.kt`:

```kotlin
package com.example.atv.ui.screens.iptv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.atv.data.epg.DeviceDefaultsProvider
import com.example.atv.domain.repository.IptvCredentialsStore
import com.example.atv.domain.usecase.ImportCtcChannelsUseCase
import com.example.atv.domain.usecase.ImportResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import javax.inject.Inject

@HiltViewModel
class IptvSettingsViewModel @Inject constructor(
    private val credentialsStore: IptvCredentialsStore,
    private val deviceDefaults: DeviceDefaultsProvider,
    private val importChannelsUseCase: ImportCtcChannelsUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(IptvSettingsUiState())
    val uiState: StateFlow<IptvSettingsUiState> = _uiState.asStateFlow()

    /** Coalesces concurrent `testAndImport` taps so only the first runs (FR-017). */
    private val importMutex = Mutex()

    init {
        viewModelScope.launch {
            credentialsStore.observe().collect { stored ->
                if (stored != null) {
                    _uiState.update {
                        it.copy(
                            userId = stored.userId,
                            password = stored.password,
                            stbId = stored.stbId,
                            ip = stored.ip,
                            mac = stored.mac,
                            authServerUrl = stored.authServerUrl,
                        )
                    }
                } else {
                    val defaults = deviceDefaults.generate()
                    _uiState.update {
                        it.copy(
                            userId = defaults.userId,
                            password = defaults.password,
                            stbId = defaults.stbId,
                            ip = defaults.ip,
                            mac = defaults.mac,
                            authServerUrl = defaults.authServerUrl,
                        )
                    }
                }
            }
        }
    }

    fun setUserId(v: String) = _uiState.update { it.copy(userId = v) }
    fun setPassword(v: String) = _uiState.update { it.copy(password = v) }
    fun setStbId(v: String) = _uiState.update { it.copy(stbId = v) }
    fun setIp(v: String) = _uiState.update { it.copy(ip = v) }
    fun setMac(v: String) = _uiState.update { it.copy(mac = v) }
    fun setAuthServerUrl(v: String) = _uiState.update { it.copy(authServerUrl = v) }

    fun testAndImport() {
        // Coalesce: if a fetch is in flight, drop the tap (FR-017).
        if (!importMutex.tryLock()) return
        viewModelScope.launch {
            try {
                val creds = _uiState.value.asCredentials
                if (!creds.isComplete) {
                    _uiState.update {
                        it.copy(importStatus = ImportStatus.LoginFailed("incomplete credentials"))
                    }
                    return@launch
                }
                credentialsStore.save(creds)
                _uiState.update { it.copy(importStatus = ImportStatus.LoggingIn) }
                val result = importChannelsUseCase()
                _uiState.update { it.copy(importStatus = result.toStatus()) }
            } finally {
                importMutex.unlock()
            }
        }
    }

    fun requestClearCredentials() {
        _uiState.update { it.copy(showClearConfirmation = true) }
    }

    fun dismissClearDialog() {
        _uiState.update { it.copy(showClearConfirmation = false) }
    }

    fun confirmClearCredentials() {
        viewModelScope.launch {
            credentialsStore.clear()
            val defaults = deviceDefaults.generate()
            _uiState.update {
                it.copy(
                    showClearConfirmation = false,
                    userId = defaults.userId,
                    password = defaults.password,
                    stbId = defaults.stbId,
                    ip = defaults.ip,
                    mac = defaults.mac,
                    authServerUrl = defaults.authServerUrl,
                    importStatus = ImportStatus.Idle,
                )
            }
        }
    }

    private fun ImportResult.toStatus(): ImportStatus = when (this) {
        is ImportResult.Success -> ImportStatus.Success(importedCount)
        is ImportResult.LoginFailure -> ImportStatus.LoginFailed(reason)
        is ImportResult.FetchFailure -> ImportStatus.FetchFailed(reason)
        is ImportResult.NoChannelsReturned -> ImportStatus.NoChannelsReturned
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./studio-gradlew :app:testDebugUnitTest --tests "com.example.atv.ui.screens.iptv.IptvSettingsViewModelTest"`
Expected: PASS, 10 tests.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/example/atv/ui/screens/iptv/IptvSettingsUiState.kt \
        app/src/main/kotlin/com/example/atv/ui/screens/iptv/IptvSettingsViewModel.kt \
        app/src/test/kotlin/com/example/atv/ui/screens/iptv/IptvSettingsViewModelTest.kt
git commit -m "feat(005): add IptvSettingsViewModel with import + clear actions"
```

---

### Task 16: Add `IptvSettingsScreen` composable

**Files:**
- Create: `app/src/main/kotlin/com/example/atv/ui/screens/iptv/IptvSettingsScreen.kt`

UI-only — covered by the manual verification checklist in Phase 5 Task 19. No automated UI test in this PR.

- [ ] **Step 1: Create the screen**

Create `app/src/main/kotlin/com/example/atv/ui/screens/iptv/IptvSettingsScreen.kt`:

```kotlin
package com.example.atv.ui.screens.iptv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text as M3Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.text.KeyboardOptions
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.example.atv.R
import com.example.atv.ui.theme.AtvColors
import com.example.atv.ui.theme.AtvTypography
import com.example.atv.ui.util.handleDPadKeyEvents

@Composable
fun IptvSettingsScreen(
    onBack: () -> Boolean,
    viewModel: IptvSettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    if (uiState.showClearConfirmation) {
        ClearCredentialsDialog(
            onConfirm = viewModel::confirmClearCredentials,
            onDismiss = viewModel::dismissClearDialog,
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AtvColors.Background)
            .handleDPadKeyEvents(onBack = { onBack() }),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.iptv_setup_title),
                style = AtvTypography.headlineLarge,
                color = AtvColors.OnSurface,
            )
            Spacer(Modifier.height(8.dp))

            IptvField(
                label = stringResource(R.string.iptv_field_user_id),
                value = uiState.userId,
                onValueChange = viewModel::setUserId,
                keyboardType = KeyboardType.NumberPassword,
            )
            IptvField(
                label = stringResource(R.string.iptv_field_password),
                value = uiState.password,
                onValueChange = viewModel::setPassword,
                keyboardType = KeyboardType.NumberPassword,
                isPassword = true,
            )
            IptvField(
                label = stringResource(R.string.iptv_field_stb_id),
                value = uiState.stbId,
                onValueChange = viewModel::setStbId,
                keyboardType = KeyboardType.Ascii,
                supportingText = if (uiState.stbId.isNotEmpty() && uiState.stbId.length != 32) {
                    stringResource(R.string.iptv_validation_stb_length)
                } else null,
            )
            IptvField(
                label = stringResource(R.string.iptv_field_ip),
                value = uiState.ip,
                onValueChange = viewModel::setIp,
                keyboardType = KeyboardType.Number,
            )
            IptvField(
                label = stringResource(R.string.iptv_field_mac),
                value = uiState.mac,
                onValueChange = viewModel::setMac,
                keyboardType = KeyboardType.Ascii,
            )
            IptvField(
                label = stringResource(R.string.iptv_field_auth_server_url),
                value = uiState.authServerUrl,
                onValueChange = viewModel::setAuthServerUrl,
                keyboardType = KeyboardType.Uri,
            )

            Spacer(Modifier.height(8.dp))

            ActionButton(
                label = stringResource(R.string.iptv_action_test_and_import),
                enabled = uiState.isFormValid,
                onClick = viewModel::testAndImport,
            )

            StatusBlock(status = uiState.importStatus)

            Spacer(Modifier.height(16.dp))

            ActionButton(
                label = stringResource(R.string.iptv_action_clear_credentials),
                enabled = true,
                onClick = viewModel::requestClearCredentials,
                destructive = true,
            )
        }
    }
}

@Composable
private fun IptvField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType,
    isPassword: Boolean = false,
    supportingText: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { M3Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = if (isPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        supportingText = supportingText?.let { { M3Text(it) } },
    )
}

@Composable
private fun StatusBlock(status: ImportStatus) {
    val text = when (status) {
        ImportStatus.Idle -> stringResource(R.string.iptv_status_idle)
        ImportStatus.LoggingIn -> stringResource(R.string.iptv_status_logging_in)
        ImportStatus.FetchingChannels -> stringResource(R.string.iptv_status_fetching_channels)
        is ImportStatus.Success -> stringResource(R.string.iptv_status_imported, status.importedCount)
        is ImportStatus.LoginFailed -> stringResource(R.string.iptv_status_login_failed, status.reason)
        is ImportStatus.FetchFailed -> stringResource(R.string.iptv_status_fetch_failed, status.reason)
        ImportStatus.NoChannelsReturned -> stringResource(R.string.iptv_status_no_channels)
    }
    val color = when (status) {
        is ImportStatus.LoginFailed, is ImportStatus.FetchFailed -> AtvColors.Error
        is ImportStatus.Success -> AtvColors.Primary
        else -> AtvColors.OnSurfaceVariant
    }
    Text(
        text = text,
        style = AtvTypography.bodyMedium,
        color = color,
    )
}

@Composable
private fun ActionButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    destructive: Boolean = false,
) {
    val baseColor = if (destructive) AtvColors.Error else AtvColors.Primary
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (enabled) baseColor.copy(alpha = 0.2f) else AtvColors.SurfaceVariant,
            focusedContainerColor = baseColor.copy(alpha = 0.3f),
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = androidx.tv.material3.Border(
                border = androidx.compose.foundation.BorderStroke(width = 2.dp, color = baseColor),
                shape = RoundedCornerShape(12.dp),
            ),
        ),
    ) {
        Box(
            modifier = Modifier.fillMaxHeight().padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = AtvTypography.titleMedium,
                color = if (enabled) baseColor else AtvColors.OnSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ClearCredentialsDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .background(AtvColors.Surface, RoundedCornerShape(16.dp))
                .padding(24.dp)
                .width(400.dp),
        ) {
            Column {
                Text(
                    text = stringResource(R.string.iptv_clear_dialog_title),
                    style = AtvTypography.headlineMedium,
                    color = AtvColors.OnSurface,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.iptv_clear_dialog_message),
                    style = AtvTypography.bodyLarge,
                    color = AtvColors.OnSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                ) {
                    ActionButton(
                        label = stringResource(R.string.cancel),
                        enabled = true,
                        onClick = onDismiss,
                    )
                    ActionButton(
                        label = stringResource(R.string.iptv_clear_dialog_confirm),
                        enabled = true,
                        onClick = onConfirm,
                        destructive = true,
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 2: Verify build**

Run: `./studio-gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/example/atv/ui/screens/iptv/IptvSettingsScreen.kt
git commit -m "feat(005): add IptvSettingsScreen composable"
```

---

### Task 17: Wire navigation + Settings entry row

**Files:**
- Modify: `app/src/main/kotlin/com/example/atv/ui/navigation/AtvNavGraph.kt`
- Modify: `app/src/main/kotlin/com/example/atv/ui/screens/settings/SettingsScreen.kt`
- Modify: `app/src/main/kotlin/com/example/atv/ui/screens/settings/SettingsViewModel.kt`

- [ ] **Step 1: Add the route and composable to `AtvNavGraph`**

In `app/src/main/kotlin/com/example/atv/ui/navigation/AtvNavGraph.kt`, add to the `Routes` object:

```kotlin
const val IPTV_SETTINGS = "iptv_settings"
```

Inside the `NavHost { ... }` block, alongside the other `composable(...)` entries, add:

```kotlin
composable(Routes.IPTV_SETTINGS) {
    com.example.atv.ui.screens.iptv.IptvSettingsScreen(
        onBack = {
            if (navController.previousBackStackEntry != null) {
                navController.popBackStack()
                true
            } else false
        }
    )
}
```

Wire the existing `SettingsScreen` to navigate to the new route. Find the existing `composable(Routes.SETTINGS) { SettingsScreen(...) }` block and add an `onNavigateToIptvSetup` lambda:

```kotlin
composable(Routes.SETTINGS) {
    SettingsScreen(
        onBack = { /* existing */ },
        onLoadNewPlaylist = { /* existing */ },
        onManageChannels = { /* existing */ },
        onNavigateToIptvSetup = { navController.navigate(Routes.IPTV_SETTINGS) },
    )
}
```

- [ ] **Step 2: Add the `IPTV setup` row to `SettingsScreen`**

In `app/src/main/kotlin/com/example/atv/ui/screens/settings/SettingsScreen.kt`, add a new param to the function signature:

```kotlin
onNavigateToIptvSetup: () -> Unit,
```

In the settings options column (find the `Column { ... }` with `verticalArrangement = Arrangement.spacedBy(8.dp)`), insert a new `SettingsItem` between the existing "Show program guide" `ToggleSettingsItem` and the "Clear All Data" `SettingsItem`:

```kotlin
SettingsItem(
    title = stringResource(R.string.iptv_setup_title),
    subtitle = uiState.iptvSetupSubtitle.resolve(),
    onClick = onNavigateToIptvSetup,
)
```

Where `iptvSetupSubtitle` is a new state field of type `IptvSetupSubtitle` (sealed class for the three states "Not configured" / "N channels imported" / "Last sync failed").

- [ ] **Step 3: Add the subtitle state to `SettingsViewModel`**

In `app/src/main/kotlin/com/example/atv/ui/screens/settings/SettingsViewModel.kt`, add:

```kotlin
import com.example.atv.domain.repository.IptvCredentialsStore
import kotlinx.coroutines.flow.combine

sealed class IptvSetupSubtitle {
    object NotConfigured : IptvSetupSubtitle()
    data class Imported(val count: Int) : IptvSetupSubtitle()
    data class SyncFailed(val reason: String) : IptvSetupSubtitle()

    @Composable
    fun resolve(): String = when (this) {
        NotConfigured -> stringResource(R.string.iptv_setup_subtitle_not_configured)
        is Imported -> stringResource(R.string.iptv_setup_subtitle_imported, count)
        is SyncFailed -> stringResource(R.string.iptv_setup_subtitle_sync_failed, reason)
    }
}
```

Add the field to `SettingsUiState`:

```kotlin
val iptvSetupSubtitle: IptvSetupSubtitle = IptvSetupSubtitle.NotConfigured,
```

Inject `IptvCredentialsStore` into the ViewModel constructor and update `loadSettings()` to compute the subtitle from store state + channel count:

```kotlin
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val channelRepository: ChannelRepository,
    private val preferencesRepository: PreferencesRepository,
    private val iptvCredentialsStore: IptvCredentialsStore,
) : ViewModel() { ... }
```

In `loadSettings()`, after the existing `_uiState.update { ... }`:

```kotlin
val creds = iptvCredentialsStore.read()
val channels = channelRepository.getAllChannels().first()
val subtitle = when {
    creds == null || !creds.isComplete -> IptvSetupSubtitle.NotConfigured
    channels.isEmpty() -> IptvSetupSubtitle.SyncFailed("no channels imported yet")
    else -> IptvSetupSubtitle.Imported(channels.size)
}
_uiState.update { it.copy(iptvSetupSubtitle = subtitle) }
```

(The "SyncFailed: last sync error" rendering moves to Phase 5 where the bootstrapper publishes a `lastBootstrapError` flow that `SettingsViewModel` can also consume. Phase 4 leaves it best-effort.)

- [ ] **Step 4: Update `SettingsViewModelTest`**

In `app/src/test/kotlin/com/example/atv/ui/screens/settings/SettingsViewModelTest.kt`, add a new `@MockK` field:

```kotlin
@MockK
private lateinit var iptvCredentialsStore: IptvCredentialsStore
```

In `setup()`, after the existing stubs:

```kotlin
coEvery { iptvCredentialsStore.read() } returns null
```

Update the `createViewModel()` to pass the new dependency:

```kotlin
return SettingsViewModel(
    channelRepository = channelRepository,
    preferencesRepository = preferencesRepository,
    iptvCredentialsStore = iptvCredentialsStore,
)
```

Add a new `@Nested` block for the subtitle behavior:

```kotlin
@Nested
@DisplayName("S-08: IPTV setup subtitle")
inner class IptvSetupSubtitle {

    @Test
    fun `subtitle is NotConfigured when no credentials stored`() = runTest {
        coEvery { iptvCredentialsStore.read() } returns null

        viewModel = createViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.iptvSetupSubtitle is
            com.example.atv.ui.screens.settings.IptvSetupSubtitle.NotConfigured)
    }

    @Test
    fun `subtitle is Imported when credentials stored and channels exist`() = runTest {
        coEvery { iptvCredentialsStore.read() } returns IptvCredentials(
            userId = "u", password = "p", stbId = "0".repeat(32),
            ip = "i", mac = "m", authServerUrl = "http://x.com",
        )
        every { channelRepository.getAllChannels() } returns flowOf(
            listOf(TestFixtures.SAMPLE_CHANNEL, TestFixtures.SAMPLE_CHANNEL_2)
        )

        viewModel = createViewModel()
        advanceUntilIdle()

        val sub = viewModel.uiState.value.iptvSetupSubtitle
        assertTrue(sub is com.example.atv.ui.screens.settings.IptvSetupSubtitle.Imported)
        assertEquals(2, (sub as com.example.atv.ui.screens.settings.IptvSetupSubtitle.Imported).count)
    }
}
```

- [ ] **Step 5: Wire the new lambda through `PlaybackScreen` → `SettingsScreen`**

`SettingsScreen` is called from two places: the navigation graph (already updated in Step 1) and... actually just the nav graph. Confirm by grepping:

```bash
grep -rn 'SettingsScreen(' app/src/main/kotlin
```

Only the graph site exists; no other update needed.

- [ ] **Step 6: Verify build + tests**

Run: `./studio-gradlew :app:testDebugUnitTest :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`. All existing tests pass; the 2 new subtitle tests pass.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/com/example/atv/ui/navigation/AtvNavGraph.kt \
        app/src/main/kotlin/com/example/atv/ui/screens/settings/SettingsScreen.kt \
        app/src/main/kotlin/com/example/atv/ui/screens/settings/SettingsViewModel.kt \
        app/src/test/kotlin/com/example/atv/ui/screens/settings/SettingsViewModelTest.kt
git commit -m "feat(005): wire IPTV setup navigation + Settings entry row"
```

End of Phase 4.

---

## Phase 5: Bootstrap + integration test

The final wire-up: a coroutine-scope provider, an `IptvSessionBootstrapper` that runs the import use case once at app launch, the `AtvApplication.onCreate` hook that triggers it, the `lastBootstrapError` flow that surfaces failures in the Settings subtitle, and an end-to-end instrumented integration test.

The privacy guardrail follow-through (`TODO(005)` markers in `EpgNetworkModule`) was already handled in Phase 3 Task 9 — sentinel providers are gone, `CtcAuthClient`/`CtcEpgProvider` read credentials from the store directly.

### Task 18: Add `applicationScope` Hilt provider

**Files:**
- Modify: `app/src/main/kotlin/com/example/atv/di/AppModule.kt`

- [ ] **Step 1: Add the provider**

In `app/src/main/kotlin/com/example/atv/di/AppModule.kt`, add the imports:

```kotlin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
```

Add a qualifier annotation (so other code that wants ViewModel-scope or a different scope doesn't collide):

```kotlin
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
```

Add the provider inside `object AppModule`:

```kotlin
/**
 * Coroutine scope tied to the Application lifecycle. Used for work that must
 * outlive any ViewModel — currently the IPTV auto re-login bootstrap (Phase 5).
 *
 * SupervisorJob so a failed child coroutine does not cancel the scope or its
 * siblings; Dispatchers.Default because the bootstrap is CPU + IO mixed and
 * shouldn't run on the main thread.
 */
@Provides
@Singleton
@ApplicationScope
fun provideApplicationScope(): CoroutineScope =
    CoroutineScope(SupervisorJob() + Dispatchers.Default)
```

- [ ] **Step 2: Verify build**

Run: `./studio-gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/example/atv/di/AppModule.kt
git commit -m "feat(005): add @ApplicationScope CoroutineScope provider"
```

---

### Task 19: Add `IptvSessionBootstrapper`

**Files:**
- Create: `app/src/main/kotlin/com/example/atv/data/epg/IptvSessionBootstrapper.kt`
- Create: `app/src/test/kotlin/com/example/atv/data/epg/IptvSessionBootstrapperTest.kt`

Reads credentials, kicks off the import use case in `applicationScope`. Exposes a `lastResult` flow that the `SettingsViewModel` consumes to render the "Last sync failed: …" subtitle.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/kotlin/com/example/atv/data/epg/IptvSessionBootstrapperTest.kt`:

```kotlin
package com.example.atv.data.epg

import com.example.atv.domain.model.IptvCredentials
import com.example.atv.domain.repository.IptvCredentialsStore
import com.example.atv.domain.usecase.ImportCtcChannelsUseCase
import com.example.atv.domain.usecase.ImportResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class IptvSessionBootstrapperTest {

    private val creds = IptvCredentials(
        userId = "u", password = "p", stbId = "0".repeat(32),
        ip = "i", mac = "m", authServerUrl = "http://x.com",
    )

    @Test
    fun `start does nothing when no credentials stored`() = runTest {
        val store: IptvCredentialsStore = mockk { coEvery { read() } returns null }
        val useCase: ImportCtcChannelsUseCase = mockk()
        val bootstrap = IptvSessionBootstrapper(store, useCase, this)

        bootstrap.start()
        advanceUntilIdle()

        coVerify(exactly = 0) { useCase() }
        assertEquals(null, bootstrap.lastResult.value)
    }

    @Test
    fun `start does nothing when credentials are incomplete`() = runTest {
        val store: IptvCredentialsStore = mockk {
            coEvery { read() } returns creds.copy(password = "")
        }
        val useCase: ImportCtcChannelsUseCase = mockk()
        val bootstrap = IptvSessionBootstrapper(store, useCase, this)

        bootstrap.start()
        advanceUntilIdle()

        coVerify(exactly = 0) { useCase() }
    }

    @Test
    fun `start runs use case and publishes Success result`() = runTest {
        val store: IptvCredentialsStore = mockk { coEvery { read() } returns creds }
        val useCase: ImportCtcChannelsUseCase = mockk {
            coEvery { invoke() } returns ImportResult.Success(7)
        }
        val bootstrap = IptvSessionBootstrapper(store, useCase, this)

        bootstrap.start()
        advanceUntilIdle()

        coVerify(exactly = 1) { useCase() }
        val r = bootstrap.lastResult.value
        assertTrue(r is ImportResult.Success)
        assertEquals(7, (r as ImportResult.Success).importedCount)
    }

    @Test
    fun `start publishes failure result on LoginFailure`() = runTest {
        val store: IptvCredentialsStore = mockk { coEvery { read() } returns creds }
        val useCase: ImportCtcChannelsUseCase = mockk {
            coEvery { invoke() } returns ImportResult.LoginFailure("bad")
        }
        val bootstrap = IptvSessionBootstrapper(store, useCase, this)

        bootstrap.start()
        advanceUntilIdle()

        val r = bootstrap.lastResult.value
        assertTrue(r is ImportResult.LoginFailure)
        assertEquals("bad", (r as ImportResult.LoginFailure).reason)
    }

    @Test
    fun `calling start twice does not double-fire the use case`() = runTest {
        val store: IptvCredentialsStore = mockk { coEvery { read() } returns creds }
        val useCase: ImportCtcChannelsUseCase = mockk {
            coEvery { invoke() } returns ImportResult.Success(1)
        }
        val bootstrap = IptvSessionBootstrapper(store, useCase, this)

        bootstrap.start()
        advanceUntilIdle()
        bootstrap.start()
        advanceUntilIdle()

        coVerify(exactly = 1) { useCase() }
    }
}
```

(Note: the test passes `this` — a `TestScope` extends `CoroutineScope` — as the application scope. In production it'll be the `@ApplicationScope` provider.)

- [ ] **Step 2: Run test to verify it fails**

Run: `./studio-gradlew :app:testDebugUnitTest --tests "com.example.atv.data.epg.IptvSessionBootstrapperTest"`
Expected: FAIL — class does not exist.

- [ ] **Step 3: Create the bootstrapper**

Create `app/src/main/kotlin/com/example/atv/data/epg/IptvSessionBootstrapper.kt`:

```kotlin
package com.example.atv.data.epg

import com.example.atv.di.ApplicationScope
import com.example.atv.domain.repository.IptvCredentialsStore
import com.example.atv.domain.usecase.ImportCtcChannelsUseCase
import com.example.atv.domain.usecase.ImportResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-startup IPTV bootstrap. When the app launches AND credentials are stored
 * AND those credentials are complete, runs [ImportCtcChannelsUseCase] once in
 * the [ApplicationScope] (not any ViewModel scope — must outlive any screen).
 *
 * The outcome is published to [lastResult] so the Settings screen can render
 * a "Last sync failed: …" subtitle when the auto-relogin fails (FR-021).
 *
 * Calling [start] more than once is a no-op after the first invocation
 * (idempotent — there is no scheduled or periodic re-login in 005).
 */
@Singleton
class IptvSessionBootstrapper @Inject constructor(
    private val credentialsStore: IptvCredentialsStore,
    private val importChannels: ImportCtcChannelsUseCase,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val started = AtomicBoolean(false)

    private val _lastResult = MutableStateFlow<ImportResult?>(null)
    val lastResult: StateFlow<ImportResult?> = _lastResult.asStateFlow()

    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            val creds = credentialsStore.read()
            if (creds == null || !creds.isComplete) {
                Timber.d("IPTV bootstrap: no/incomplete credentials, skipping")
                return@launch
            }
            val result = importChannels()
            _lastResult.value = result
            when (result) {
                is ImportResult.Success ->
                    Timber.d("IPTV bootstrap: imported %d channels", result.importedCount)
                is ImportResult.LoginFailure ->
                    Timber.w("IPTV bootstrap: login failed: %s", result.reason)
                is ImportResult.FetchFailure ->
                    Timber.w("IPTV bootstrap: fetch failed: %s", result.reason)
                ImportResult.NoChannelsReturned ->
                    Timber.w("IPTV bootstrap: no channels returned")
            }
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./studio-gradlew :app:testDebugUnitTest --tests "com.example.atv.data.epg.IptvSessionBootstrapperTest"`
Expected: PASS, 5 tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/example/atv/data/epg/IptvSessionBootstrapper.kt \
        app/src/test/kotlin/com/example/atv/data/epg/IptvSessionBootstrapperTest.kt
git commit -m "feat(005): add IptvSessionBootstrapper for auto re-login at launch"
```

---

### Task 20: Trigger bootstrap from `AtvApplication.onCreate`

**Files:**
- Modify: `app/src/main/kotlin/com/example/atv/AtvApplication.kt`

Hilt's `@HiltAndroidApp` already wires the Application as the injector root. To pull the bootstrapper into `onCreate` before any Activity is created, use an `EntryPoint` (the standard Hilt pattern when you need DI in a non-`@AndroidEntryPoint`-able location).

- [ ] **Step 1: Modify `AtvApplication.onCreate`**

In `app/src/main/kotlin/com/example/atv/AtvApplication.kt`, add imports:

```kotlin
import com.example.atv.data.epg.IptvSessionBootstrapper
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
```

Add an entry point declaration at the bottom of the file (outside the class):

```kotlin
@EntryPoint
@InstallIn(SingletonComponent::class)
interface BootstrapEntryPoint {
    fun iptvSessionBootstrapper(): IptvSessionBootstrapper
}
```

Modify `onCreate()` to fetch and start the bootstrapper after Timber is set up:

```kotlin
override fun onCreate() {
    super.onCreate()

    if (BuildConfig.DEBUG) {
        Timber.plant(Timber.DebugTree())
    } else {
        Timber.plant(ReleaseTree())
    }

    // Spec 005: auto re-login if CTC credentials are stored. No-op otherwise.
    EntryPointAccessors.fromApplication(this, BootstrapEntryPoint::class.java)
        .iptvSessionBootstrapper()
        .start()
}
```

- [ ] **Step 2: Verify build**

Run: `./studio-gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/example/atv/AtvApplication.kt
git commit -m "feat(005): trigger IPTV bootstrap from AtvApplication.onCreate"
```

---

### Task 21: Surface bootstrap failures in the Settings subtitle

**Files:**
- Modify: `app/src/main/kotlin/com/example/atv/ui/screens/settings/SettingsViewModel.kt`

In Phase 4 Task 17, the "Last sync failed: …" branch was deferred ("rendering moves to Phase 5 where the bootstrapper publishes a `lastBootstrapError` flow"). Wire it now.

- [ ] **Step 1: Inject `IptvSessionBootstrapper` and observe its flow**

In `SettingsViewModel.kt`, add to the constructor:

```kotlin
private val iptvBootstrapper: com.example.atv.data.epg.IptvSessionBootstrapper,
```

In `init { ... }`, after `loadSettings()`, add:

```kotlin
viewModelScope.launch {
    iptvBootstrapper.lastResult.collect { result ->
        val current = _uiState.value.iptvSetupSubtitle
        val updated = when (result) {
            is com.example.atv.domain.usecase.ImportResult.LoginFailure ->
                IptvSetupSubtitle.SyncFailed(result.reason)
            is com.example.atv.domain.usecase.ImportResult.FetchFailure ->
                IptvSetupSubtitle.SyncFailed(result.reason)
            com.example.atv.domain.usecase.ImportResult.NoChannelsReturned ->
                IptvSetupSubtitle.SyncFailed("no channels")
            is com.example.atv.domain.usecase.ImportResult.Success ->
                IptvSetupSubtitle.Imported(result.importedCount)
            null -> current
        }
        _uiState.update { it.copy(iptvSetupSubtitle = updated) }
    }
}
```

- [ ] **Step 2: Update tests to mock the bootstrapper**

In `SettingsViewModelTest.kt`, add:

```kotlin
@MockK
private lateinit var iptvBootstrapper: IptvSessionBootstrapper
```

In `setup()`:

```kotlin
every { iptvBootstrapper.lastResult } returns MutableStateFlow(null)
```

Update `createViewModel()` to pass it:

```kotlin
return SettingsViewModel(
    channelRepository = channelRepository,
    preferencesRepository = preferencesRepository,
    iptvCredentialsStore = iptvCredentialsStore,
    iptvBootstrapper = iptvBootstrapper,
)
```

Add a new test in the existing `IptvSetupSubtitle` nested block:

```kotlin
@Test
fun `subtitle becomes SyncFailed when bootstrapper publishes LoginFailure`() = runTest {
    val resultFlow = MutableStateFlow<ImportResult?>(null)
    every { iptvBootstrapper.lastResult } returns resultFlow
    coEvery { iptvCredentialsStore.read() } returns IptvCredentials(
        userId = "u", password = "p", stbId = "0".repeat(32),
        ip = "i", mac = "m", authServerUrl = "http://x.com",
    )

    viewModel = createViewModel()
    advanceUntilIdle()

    resultFlow.value = ImportResult.LoginFailure("session expired")
    advanceUntilIdle()

    val sub = viewModel.uiState.value.iptvSetupSubtitle
    assertTrue(sub is com.example.atv.ui.screens.settings.IptvSetupSubtitle.SyncFailed)
    assertEquals(
        "session expired",
        (sub as com.example.atv.ui.screens.settings.IptvSetupSubtitle.SyncFailed).reason,
    )
}
```

- [ ] **Step 3: Run tests**

Run: `./studio-gradlew :app:testDebugUnitTest --tests "com.example.atv.ui.screens.settings.*"`
Expected: PASS — existing tests + 3 IPTV subtitle tests.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/example/atv/ui/screens/settings/SettingsViewModel.kt \
        app/src/test/kotlin/com/example/atv/ui/screens/settings/SettingsViewModelTest.kt
git commit -m "feat(005): render bootstrap failure as 'Last sync failed' subtitle"
```

---

### Task 22: End-to-end integration test (instrumented)

**Files:**
- Create: `app/src/androidTest/kotlin/com/example/atv/ui/screens/playback/PlaybackViewModelCtcImportIntegrationTest.kt`

The integration test seeds credentials in `EncryptedSharedPreferences`, points the auth + EPG endpoints at a `MockWebServer`, runs `ImportCtcChannelsUseCase` end-to-end against a real `CtcAuthClient` + `CtcChannelFetcher`, asserts channels land in Room, `EpgProvider.isConfigured.value == true`, and a subsequent `playChannel(ch)` on `PlaybackViewModel` populates `currentProgram`/`nextProgram` from a mocked EPG `prevue_list.jsp` response.

This test exists in `androidTest/` because it depends on `EncryptedSharedPreferences` (Keystore-backed) and a real Room database.

- [ ] **Step 1: Create the integration test**

Create `app/src/androidTest/kotlin/com/example/atv/ui/screens/playback/PlaybackViewModelCtcImportIntegrationTest.kt`:

```kotlin
package com.example.atv.ui.screens.playback

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.atv.data.epg.CtcAuthClient
import com.example.atv.data.epg.CtcChannelFetcher
import com.example.atv.data.epg.CtcEpgProvider
import com.example.atv.data.local.db.ALL_MIGRATIONS
import com.example.atv.data.local.db.AtvDatabase
import com.example.atv.data.local.secure.IptvCredentialsStoreImpl
import com.example.atv.data.repository.ChannelRepositoryImpl
import com.example.atv.domain.model.IptvCredentials
import com.example.atv.domain.usecase.ImportCtcChannelsUseCase
import com.example.atv.domain.usecase.ImportResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock

@RunWith(AndroidJUnit4::class)
class PlaybackViewModelCtcImportIntegrationTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var server: MockWebServer
    private lateinit var db: AtvDatabase
    private lateinit var store: IptvCredentialsStoreImpl

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        db = Room.inMemoryDatabaseBuilder(context, AtvDatabase::class.java)
            .addMigrations(*ALL_MIGRATIONS)
            .build()
        store = IptvCredentialsStoreImpl(context, prefsName = "iptv_creds_integration_test")
        runBlocking { store.clear() }
    }

    @After
    fun tearDown() {
        runBlocking { store.clear() }
        db.close()
        server.shutdown()
    }

    @Test
    fun import_endToEnd_savesChannelsAndFlipsIsConfigured() = runBlocking {
        // 1. Seed credentials pointing at MockWebServer.
        val creds = IptvCredentials(
            userId = "1234567890123",
            password = "000000",
            stbId = "0".repeat(32),
            ip = "192.0.2.1",
            mac = "00:00:5E:00:53:01",
            authServerUrl = server.url("/").toString().removeSuffix("/"),
        )
        store.save(creds)

        // 2. Enqueue the 6-step login transcript + frameset_builder + mapping.
        // (See CtcAuthClientTest.enqueueHappyPath + CtcChannelFetcherTest sample data.)
        enqueueHappyLoginAnd2ChannelImport()

        // 3. Construct collaborators directly (skipping Hilt for the integration test).
        val http = OkHttpClient.Builder().build()
        val authClient = CtcAuthClient(http)
        val fetcher = CtcChannelFetcher(http)
        val repo = ChannelRepositoryImpl(db.channelDao())
        val epgProvider = CtcEpgProvider(authClient, http, store, Clock.systemUTC())
        val useCase = ImportCtcChannelsUseCase(authClient, fetcher, repo, store, epgProvider)

        // 4. Run.
        val result = useCase()

        // 5. Assert.
        assertTrue("got $result", result is ImportResult.Success)
        assertEquals(2, (result as ImportResult.Success).importedCount)
        val channels = repo.getAllChannels().first()
        assertEquals(2, channels.size)
        assertEquals("CCTV-1", channels[0].name)
        assertEquals("ch00000000000000000001", channels[0].channelCode)
        assertTrue(epgProvider.isConfigured.value)
    }

    private fun enqueueHappyLoginAnd2ChannelImport() {
        // Step 1: GET /auth — encryToken
        server.enqueue(MockResponse().setBody(
            "<script>Authentication.CTCGetAuthInfo('abcdef0123456789');</script>"
        ))
        // Step 2: POST /uploadAuthInfo — UserToken
        server.enqueue(MockResponse().setBody(
            "Authentication.CTCSetConfig('UserToken','tok-XYZ');"
        ))
        // Step 3: GET /getServiceList — document.location redirect
        val lbUrl = server.url("/iptvepg/lb").toString()
        server.enqueue(MockResponse().setBody("document.location='$lbUrl';"))
        // Step 4: lb hop
        val nodeUrl = server.url("/iptvepg/function/index.jsp").toString()
        server.enqueue(MockResponse().setBody("document.location='$nodeUrl';"))
        // Step 5: node — JSESSIONID + portal HTML
        server.enqueue(MockResponse()
            .addHeader("Set-Cookie", "JSESSIONID=ABC; Path=/iptvepg")
            .setBody("<html><body><input type='hidden' name='UserID' value='1234567890123'/></body></html>"))
        // Step 6: portal auth
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))
        // Channel fetch: frameset_builder.jsp
        server.enqueue(MockResponse().setBody("""
            <script>
            jsSetConfig('Channel','ChannelID=ch00000000000000000001,ChannelName="CCTV-1",UserChannelID=001,ChannelURL=igmp://239.0.0.1:8000');
            jsSetConfig('Channel','ChannelID=ch00000000000000000002,ChannelName="CCTV-2",UserChannelID=002,ChannelURL=igmp://239.0.0.2:8000');
            </script>
        """.trimIndent()))
        // Mapping
        server.enqueue(MockResponse().setBody("""{"channelMixnoMapping":"1:001,2:002"}"""))
    }
}
```

- [ ] **Step 2: Run on an emulator**

Run: `./studio-gradlew :app:connectedAndroidTest --tests "com.example.atv.ui.screens.playback.PlaybackViewModelCtcImportIntegrationTest"`
Expected: PASS, 1 test.

(If no emulator is available locally, mark as "must run in CI" — same as the migration test in Phase 1 Task 2.)

- [ ] **Step 3: Commit**

```bash
git add app/src/androidTest/kotlin/com/example/atv/ui/screens/playback/PlaybackViewModelCtcImportIntegrationTest.kt
git commit -m "test(005): end-to-end integration test for CTC import flow"
```

---

### Task 23: Manual verification

**Files:** none modified.

- [ ] **Step 1: Install debug APK**

Run: `./studio-gradlew :app:installDebug`
Expected: `BUILD SUCCESSFUL`, app launches.

- [ ] **Step 2: Verify pre-import behavior**

- Launch app fresh (clear app data first if previously installed).
- Open Settings.
- **Expected**: "IPTV setup" row shows subtitle "Not configured".
- Tap "IPTV setup".
- **Expected**: Form opens with STB ID, IP, MAC, Auth server URL pre-filled; UserID and Password empty. "Test connection & import channels" button is disabled.

- [ ] **Step 3: Enter credentials and import**

- Enter a known-good test UserID and Password using the soft keyboard.
- **Expected**: "Test & import" button enables.
- Tap "Test & import".
- **Expected**: Status block transitions Idle → "Logging in…" → (briefly) → "Imported N channels".

- [ ] **Step 4: Verify EPG surfaces light up**

- Press Back to return to Playback.
- Switch channels with UP/DOWN.
- **Expected**: Top-left banner appears as before AND bottom-center program block now shows current + next program with progress bar.
- Press LEFT to open the channel list.
- **Expected**: Side-by-side EPG panel renders with the focused channel's schedule. Date tabs Yesterday/Today/Tomorrow are visible.

- [ ] **Step 5: Verify auto re-login on relaunch**

- Force-stop the app.
- Relaunch.
- Wait ~2 seconds, then switch channels.
- **Expected**: EPG banner immediately works without re-importing manually.

- [ ] **Step 6: Verify clear credentials**

- Open Settings → IPTV setup.
- Scroll to "Clear IPTV credentials". Tap it.
- Confirm in the dialog.
- **Expected**: Fields reset to defaults (UserID/Password empty, auto-generated STB/IP/MAC). Status block returns to "Ready".
- Press Back to return to Playback.
- Switch channels.
- **Expected**: EPG surfaces are HIDDEN. Banner shows only top-left channel info. Channel list overlay has no side panel.
- Force-stop and relaunch.
- **Expected**: No auto-relogin fires. App behaves identically to a fresh install with only M3U8 channels.

- [ ] **Step 7: Run on-device test suite**

Run: `./studio-gradlew :app:connectedAndroidTest`
Expected: All instrumented tests pass — migration test (Phase 1), credentials store test (Phase 2), integration test (Phase 5).

- [ ] **Step 8: Final unit + static analysis run**

Run: `./studio-gradlew :app:testDebugUnitTest :app:detekt :app:lint`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 9: Final commit (chore)**

```bash
git commit --allow-empty -m "chore(005): manual verification complete (Phase 5 Task 23)"
```

End of Phase 5. Spec 005 implementation complete.

