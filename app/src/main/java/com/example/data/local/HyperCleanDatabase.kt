package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        CachedFileSignature::class,
        CleanupHistoryEntity::class,
        LinkedFolderEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class HyperCleanDatabase : RoomDatabase() {
    abstract fun dao(): HyperCleanDao

    companion object {
        @Volatile
        private var INSTANCE: HyperCleanDatabase? = null

        fun getInstance(context: Context): HyperCleanDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    HyperCleanDatabase::class.java,
                    "hyperclean_telemetry.db"
                )
                    .fallbackToDestructiveMigration(true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
