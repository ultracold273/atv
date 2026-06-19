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
