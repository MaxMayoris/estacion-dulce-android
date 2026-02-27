package com.estaciondulce.app.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

object ImageUtils {
    private const val TAG = "ImageUtils"

    /**
     * Gets the size of an image from its Uri in bytes.
     */
    fun getImageSize(context: Context, uri: Uri): Long {
        var size: Long = 0
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                size = cursor.getLong(sizeIndex)
            }
        }
        return size
    }

    /**
     * Compresses an image to be smaller than the max size if possible.
     * @param context App context
     * @param uri Original image Uri
     * @param targetSizeInBytes Target size (e.g., 1MB = 1024 * 1024)
     * @param initialQuality Initial compression quality (0-100)
     * @return File Uri of the compressed image, or original Uri if compression failed or wasn't needed
     */
    fun compressImage(
        context: Context,
        uri: Uri
    ): Uri {
        val originalSize = getImageSize(context, uri)
        
        return try {
            val originalBitmap = getBitmapFromUri(context, uri) ?: return uri
            
            val rotation = getOrientationRotation(context, uri)
            val orientedBitmap = rotateBitmap(originalBitmap, rotation)
            
            val resizedBitmap = resizeBitmap(orientedBitmap, 1200)
            
            val tempFile = File(context.cacheDir, "compressed_${System.currentTimeMillis()}.webp")
            
            val out = FileOutputStream(tempFile)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                resizedBitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 75, out)
            } else {
                @Suppress("DEPRECATION")
                resizedBitmap.compress(Bitmap.CompressFormat.WEBP, 75, out)
            }
            out.flush()
            out.close()
            
            val compressedSize = tempFile.length()
            
            if (originalBitmap != orientedBitmap && originalBitmap != resizedBitmap) originalBitmap.recycle()
            if (orientedBitmap != resizedBitmap) orientedBitmap.recycle()
            // We don't recycle the resizedBitmap here if we might need it, but it's local so it's fine
            
            Uri.fromFile(tempFile)
        } catch (e: Exception) {
            Log.e(TAG, "Error compressing image", e)
            uri
        }
    }

    private fun getOrientationRotation(context: Context, uri: Uri): Int {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val exifInterface = ExifInterface(inputStream)
                val orientation = exifInterface.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
                when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } ?: 0
        } catch (e: Exception) {
            Log.e(TAG, "Error reading orientation", e)
            0
        }
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return bitmap
        return try {
            val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (e: Exception) {
            Log.e(TAG, "Error rotating bitmap", e)
            bitmap
        }
    }

    private fun resizeBitmap(bitmap: Bitmap, maxSize: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        
        if (width <= maxSize && height <= maxSize) return bitmap
        
        val ratio: Float = width.toFloat() / height.toFloat()
        val targetWidth: Int
        val targetHeight: Int
        
        if (width > height) {
            targetWidth = maxSize
            targetHeight = (maxSize / ratio).toInt()
        } else {
            targetHeight = maxSize
            targetWidth = (maxSize * ratio).toInt()
        }
        
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }

    fun clearTempFiles(context: Context) {
        try {
            val cacheDir = context.cacheDir
            cacheDir.listFiles()?.forEach { file ->
                if (file.name.startsWith("compressed_") && (file.name.endsWith(".jpg") || file.name.endsWith(".webp"))) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing temp files", e)
        }
    }

    private fun getBitmapFromUri(context: Context, uri: Uri): Bitmap? {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            bitmap
        } catch (e: IOException) {
            Log.e(TAG, "Error reading bitmap from Uri", e)
            null
        }
    }
}
