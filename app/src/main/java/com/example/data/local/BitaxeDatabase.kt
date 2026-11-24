package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ExperimentEntity::class], version = 1, exportSchema = false)
abstract class BitaxeDatabase : RoomDatabase() {
    abstract fun experimentDao(): ExperimentDao

    companion object {
        @Volatile
        private var INSTANCE: BitaxeDatabase? = null

        fun getInstance(context: Context): BitaxeDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    BitaxeDatabase::class.java,
                    "bitaxe_experiments.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
