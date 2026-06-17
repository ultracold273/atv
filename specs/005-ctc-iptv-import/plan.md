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

## Phases 3–5 (outline, to be expanded before execution)

Each remaining phase has the same shape as Phase 1–2 (TDD steps with exact code, single-task commits). Authoring is left for a follow-up session so this plan stays reviewable. Below is a faithful task-level description.

### Phase 3 — Channel fetcher + import use case

- **Task 7** — `CtcChannelFetcher` (new class in `data/epg/`). Ports the `frameset_builder.jsp` POST + `get_channel_info_mapping.jsp` GET from `iptv_client.py` lines 377-402. Returns `List<CtcChannelEntry>` (wire DTO with `channelId, channelName, userChannelId, channelUrl`). Tests via `MockWebServer` covering happy path, empty list, malformed HTML, network error.
- **Task 8** — `ImportResult` sealed class (`Success(importedCount: Int) | LoginFailure(reason) | FetchFailure(reason) | NoChannelsReturned`) in `domain/usecase/`.
- **Task 9** — `ImportCtcChannelsUseCase` (`domain/usecase/`). Constructor: `(authClient, channelFetcher, channelRepository, credentialsStore, epgProvider)`. Algorithm: read credentials → if null/incomplete return `LoginFailure("no credentials")`; call `authClient.login()` (now constructed dynamically with current credentials rather than Hilt-injected since credentials live in the store, not the Hilt graph) → on success run `channelFetcher.fetch(session)` → if empty return `NoChannelsReturned`; else map to `Channel(... channelCode = entry.channelId)`, call `repo.savePlaylistChannels(...)`, then `epgProvider.markConfigured(true)`. Returns `Success(channels.size)`. Tests with MockK fakes for each collaborator covering all 4 result paths + edge cases.
- **Task 10** — Replace `CtcEpgProvider.testSetConfigured` with public `internal fun markConfigured(value: Boolean)`. Existing Phase 2 tests rename their seam call. No production behavior change.

### Phase 4 — Settings UI

- **Task 11** — Localized strings (`strings.xml` en + zh) for: setup screen title, all six field labels + hints, button label, status messages, "Clear IPTV credentials" + confirmation dialog text, "Last sync failed" subtitle.
- **Task 12** — `IptvSettingsUiState` and `IptvSettingsViewModel` (`ui/screens/iptv/`). State: per-field text values, validation errors, `importStatus: ImportStatus` (sealed: `Idle | InProgress | Success(n) | Failure(reason)`), confirmation dialog flag. Actions: `setUserId/...setAuthServerUrl/clearCredentials/testAndImport/applyDefaultsIfEmpty`. Init reads credentials store; if null, applies `DeviceDefaultsProvider.generate()`. `testAndImport` debounces concurrent taps via a `Mutex` (FR-017). Tests with MockK covering all state transitions.
- **Task 13** — `IptvSettingsScreen` composable (`ui/screens/iptv/`). Six `TextField`s with appropriate `KeyboardType`/`PasswordVisualTransformation`, "Test & import" button (disabled when form invalid), status block, "Clear IPTV credentials" button + confirmation dialog. Standard `handleDPadKeyEvents` for back.
- **Task 14** — Navigation wiring. Add `iptv_settings` route to `MainActivity`'s `NavHost`. Add `SettingsItem("IPTV setup", subtitle = …)` to `SettingsScreen` between the existing "Show program guide" toggle and "Clear All Data", with `onClick = { navController.navigate("iptv_settings") }`. Subtitle dynamically reflects state: "Not configured" | "N channels imported" | "Last sync failed: …".

### Phase 5 — Bootstrap + privacy follow-through + integration test

- **Task 15** — `applicationScope` provider in `AppModule`: `@Singleton fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)`.
- **Task 16** — `IptvSessionBootstrapper` (`data/epg/`). Constructor `(credentialsStore, importChannelsUseCase, scope)`. `fun start()` launches `scope.launch { credentialsStore.read()?.takeIf { it.isComplete }?.let { importChannelsUseCase() } }`. On any non-Success result, log via Timber. The status surfaces to UI via a new state field in `IptvSettingsViewModel` that reads the most-recent bootstrap result (stored in a `MutableStateFlow<LastBootstrapResult?>` exposed by the bootstrapper as a public Flow).
- **Task 17** — `AtvApplication.onCreate` wiring. Hilt entry point to fetch the bootstrapper singleton, call `start()` once. Existing init code (Timber, etc.) stays.
- **Task 18** — Replace `EpgNetworkModule.provideDeviceProfile` and `provideAuthServerUrl` with versions that read from `IptvCredentialsStore`. Both `@Provides` methods now inject `IptvCredentialsStore` and call `store.read()` (synchronous bridge via `runBlocking`; acceptable because these are Hilt providers, called once per injection request at construction time; the values are then cached by the `@Singleton` scope). When the store returns null, provide empty-string sentinels — but `CtcEpgProvider.isConfigured = false` blocks any actual use.
- **Task 19** — End-to-end integration test (`PlaybackViewModelCtcImportIntegrationTest` in `androidTest/`). Seeds credentials, runs `ImportCtcChannelsUseCase` against a `MockWebServer`-backed `CtcAuthClient`+`CtcChannelFetcher`, asserts channels appear in the Room DB, `EpgProvider.isConfigured.value == true`, and a subsequent `playChannel(ch)` on `PlaybackViewModel` populates `currentProgram`/`nextProgram` from the mocked EPG response.
- **Task 20** — Manual verification checklist: install debug APK, enter test credentials in IPTV setup, tap Test & import, verify channel list updates and EPG surfaces light up on next channel switch. Force-stop + relaunch to verify auto-bootstrap. Clear credentials and verify EPG surfaces hide.

---

## Notes for the spec-author session that expands Phases 3–5

- Phase 3 Task 9's "construct `CtcAuthClient` dynamically with current credentials" is the architectural shift away from Hilt-injected static credentials. The existing `CtcAuthClient @Inject constructor(...)` from spec 004 still works for testing, but the new production path will use an `AssistedInject` or `Provider<CtcAuthClient>` pattern. Decide between the two during expansion.
- Phase 4 Task 12's `Mutex` for tap-coalescing must use `Mutex.tryLock` (not `withLock`) so that a second tap returns immediately rather than queueing.
- Phase 5 Task 18's `runBlocking` inside Hilt providers is intentional but worth a code-review comment in the diff — calling it out as the documented bridge between sync DI and async storage.

End of Phase 1+2 detailed plan. Phases 3-5 outlined.
