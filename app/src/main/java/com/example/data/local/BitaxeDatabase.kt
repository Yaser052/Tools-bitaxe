package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [ExperimentEntity::class], version = 2, exportSchema = false)
abstract class BitaxeDatabase : RoomDatabase() {
    abstract fun experimentDao(): ExperimentDao

    companion object {
        @Volatile
        private var INSTANCE: BitaxeDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE experiments ADD COLUMN operationMode TEXT NOT NULL DEFAULT 'BASELINE'")
                db.execSQL("ALTER TABLE experiments ADD COLUMN experimentResult TEXT NOT NULL DEFAULT 'STABLE_PASS'")
                db.execSQL("ALTER TABLE experiments ADD COLUMN rollbackTriggered INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE experiments ADD COLUMN rollbackTarget TEXT")
                db.execSQL("ALTER TABLE experiments ADD COLUMN rollbackConfirmed INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): BitaxeDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    BitaxeDatabase::class.java,
                    "bitaxe_experiments.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
