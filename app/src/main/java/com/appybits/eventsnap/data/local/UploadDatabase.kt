package com.appybits.eventsnap.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [UploadItem::class], version = 1, exportSchema = false)
abstract class UploadDatabase : RoomDatabase() {
    abstract fun uploadDao(): UploadDao
}