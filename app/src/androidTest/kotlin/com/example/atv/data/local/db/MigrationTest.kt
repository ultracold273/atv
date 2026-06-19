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
