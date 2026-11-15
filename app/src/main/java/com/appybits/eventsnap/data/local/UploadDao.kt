package com.appybits.eventsnap.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface UploadDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: UploadItem): Long

    @Update
    suspend fun update(item: UploadItem)

    @Query("SELECT * FROM upload_items WHERE status = 'PENDING' OR status = 'FAILED' LIMIT 1")
    suspend fun getNextPendingUpload(): UploadItem?

    @Query("SELECT * FROM upload_items WHERE eventId = :eventId ORDER BY id DESC")
    fun getUploadsForEvent(eventId: String): Flow<List<UploadItem>>

    @Query("UPDATE upload_items SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Int, status: UploadStatus)

    @Query("SELECT * FROM upload_items WHERE id = :id")
    suspend fun getUploadItem(id: Int): UploadItem?
}