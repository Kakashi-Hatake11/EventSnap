package com.appybits.eventsnap.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.storage.ktx.storage
import com.google.firebase.ktx.Firebase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = Firebase.auth

    @Provides
    @Singleton
    fun provideFirebaseFirestore(): FirebaseFirestore = Firebase.firestore

    @Provides
    @Singleton
    fun provideFirebaseStorage(): com.google.firebase.storage.FirebaseStorage = com.google.firebase.ktx.Firebase.storage

    @Provides
    @Singleton
    fun provideUploadDatabase(@dagger.hilt.android.qualifiers.ApplicationContext context: android.content.Context): com.appybits.eventsnap.data.local.UploadDatabase {
        return androidx.room.Room.databaseBuilder(
            context,
            com.appybits.eventsnap.data.local.UploadDatabase::class.java,
            "upload_database"
        ).build()
    }

    @Provides
    @Singleton
    fun provideUploadDao(database: com.appybits.eventsnap.data.local.UploadDatabase): com.appybits.eventsnap.data.local.UploadDao {
        return database.uploadDao()
    }
}
