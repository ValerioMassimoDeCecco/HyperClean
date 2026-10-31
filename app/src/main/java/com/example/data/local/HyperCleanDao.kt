package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HyperCleanDao {

    @Query("SELECT * FROM cached_file_signatures WHERE fileKey = :fileKey AND sizeBytes = :sizeBytes AND lastModified = :lastModified LIMIT 1")
    suspend fun getValidSignature(
        fileKey: String,
        sizeBytes: Long,
        lastModified: Long
    ): CachedFileSignature?

    @Query("SELECT * FROM cached_file_signatures")
    suspend fun getAllSignaturesList(): List<CachedFileSignature>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSignature(signature: CachedFileSignature)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSignatures(signatures: List<CachedFileSignature>)

    @Query("SELECT COUNT(*) FROM cached_file_signatures")
    fun observeCachedSignatureCount(): Flow<Int>

    @Query("DELETE FROM cached_file_signatures")
    suspend fun clearAllSignatures()

    @Query("SELECT * FROM cleanup_history ORDER BY timestamp DESC")
    fun observeCleanupHistory(): Flow<List<CleanupHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCleanupRecord(record: CleanupHistoryEntity)

    @Query("SELECT COALESCE(SUM(bytesFreed), 0) FROM cleanup_history")
    fun observeTotalBytesFreedAllTime(): Flow<Long>

    @Query("SELECT * FROM linked_folders ORDER BY addedAt DESC")
    fun observeLinkedFolders(): Flow<List<LinkedFolderEntity>>

    @Query("SELECT * FROM linked_folders ORDER BY addedAt DESC")
    suspend fun getLinkedFoldersList(): List<LinkedFolderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLinkedFolder(folder: LinkedFolderEntity)

    @Query("DELETE FROM linked_folders WHERE uriString = :uriString")
    suspend fun removeLinkedFolder(uriString: String)
}
