package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_file_signatures")
data class CachedFileSignature(
    @PrimaryKey val fileKey: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val first1KbHash: String,
    val fullProgressiveHash: String?,
    val fourKbChunksVerified: Int,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "cleanup_history")
data class CleanupHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val filesDeletedCount: Int,
    val bytesFreed: Long,
    val scanDurationMs: Long,
    val ioSavedPercent: Int,
    val summaryNote: String
)

@Entity(tableName = "linked_folders")
data class LinkedFolderEntity(
    @PrimaryKey val uriString: String,
    val displayName: String,
    val addedAt: Long = System.currentTimeMillis()
)
