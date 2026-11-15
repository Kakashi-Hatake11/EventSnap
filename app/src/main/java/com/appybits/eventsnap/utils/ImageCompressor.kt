package com.appybits.eventsnap.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object ImageCompressor {
    suspend fun compressImage(context: Context, uri: Uri, targetWidth: Int = 1920, targetHeight: Int = 1080): File? {
        return withContext(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext null
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeStream(inputStream, null, options)
                inputStream.close()

                var inSampleSize = 1
                if (options.outHeight > targetHeight || options.outWidth > targetWidth) {
                    val halfHeight: Int = options.outHeight / 2
                    val halfWidth: Int = options.outWidth / 2
                    while (halfHeight / inSampleSize >= targetHeight && halfWidth / inSampleSize >= targetWidth) {
                        inSampleSize *= 2
                    }
                }

                val decodeOptions = BitmapFactory.Options().apply {
                    this.inSampleSize = inSampleSize
                }
                val inputStream2 = context.contentResolver.openInputStream(uri) ?: return@withContext null
                val bitmap = BitmapFactory.decodeStream(inputStream2, null, decodeOptions)
                inputStream2.close()

                if (bitmap == null) return@withContext null

                val outputFile = File(context.cacheDir, "compressed_${UUID.randomUUID()}.jpg")
                val outputStream = FileOutputStream(outputFile)
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
                outputStream.flush()
                outputStream.close()
                bitmap.recycle()

                outputFile
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
}