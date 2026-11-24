package com.example.suru

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import kotlin.math.max

data class SuruImageAttachment(
    val displayName: String,
    val jpegBytes: ByteArray,
    val base64Jpeg: String,
    val width: Int,
    val height: Int,
    val previewBitmap: Bitmap
) {
    val sizeKb: Int get() = (jpegBytes.size / 1024).coerceAtLeast(1)
    val summaryLabel: String get() = "$displayName (${width}×${height}, ${sizeKb}KB)"

    fun toComposeImageBitmap(): ImageBitmap = previewBitmap.asImageBitmap()
}

sealed class SuruImageLoadResult {
    data class Success(val attachment: SuruImageAttachment) : SuruImageLoadResult()
    data class Error(val message: String) : SuruImageLoadResult()
}

object SuruImageProcessor {
    private const val MAX_DIMENSION_PX = 1280
    private const val MAX_INPUT_STREAM_BYTES = 20 * 1024 * 1024
    private const val TARGET_MAX_JPEG_BYTES = 3 * 1024 * 1024

    fun createCameraCaptureUri(context: Context): Uri {
        val dir = File(context.cacheDir, "suru_captures")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "suru_capture_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    suspend fun loadFromUri(context: Context, uri: Uri, sourceLabel: String = "Photo"): SuruImageLoadResult {
        return withContext(Dispatchers.IO) {
            runCatching {
                val mime = context.contentResolver.getType(uri)
                if (mime != null && !mime.startsWith("image/")) {
                    return@withContext SuruImageLoadResult.Error("Unsupported file type ($mime). Please choose a PNG, JPG, or WebP image.")
                }

                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, bounds)
                } ?: return@withContext SuruImageLoadResult.Error("Could not open selected image.")

                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                    return@withContext SuruImageLoadResult.Error("Corrupted or unsupported image format.")
                }

                val sampleSize = computeInSampleSize(bounds.outWidth, bounds.outHeight, MAX_DIMENSION_PX)
                val decodeOpts = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val decoded = context.contentResolver.openInputStream(uri)?.use { stream: InputStream ->
                    BitmapFactory.decodeStream(stream, null, decodeOpts)
                } ?: return@withContext SuruImageLoadResult.Error("Failed to decode image into memory safely.")

                encodeBitmapForUpload(decoded, sourceLabel)
            }.getOrElse { err ->
                SuruImageLoadResult.Error("Could not read image: ${err.localizedMessage ?: "Unknown error"}")
            }
        }
    }

    fun encodeBitmapForUpload(bitmap: Bitmap, sourceLabel: String = "Photo"): SuruImageLoadResult {
        if (bitmap.width <= 0 || bitmap.height <= 0) {
            return SuruImageLoadResult.Error("Image has invalid dimensions.")
        }
        val scaled = scaleDownIfNeeded(bitmap, MAX_DIMENSION_PX)
        val baos = ByteArrayOutputStream()
        var quality = 84
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, baos)
        while (baos.size() > TARGET_MAX_JPEG_BYTES && quality > 42) {
            baos.reset()
            quality -= 12
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, baos)
        }
        if (baos.size() > MAX_INPUT_STREAM_BYTES) {
            return SuruImageLoadResult.Error("Compressed image still exceeds safe upload size limit.")
        }
        val bytes = baos.toByteArray()
        val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        return SuruImageLoadResult.Success(
            SuruImageAttachment(
                displayName = sourceLabel,
                jpegBytes = bytes,
                base64Jpeg = b64,
                width = scaled.width,
                height = scaled.height,
                previewBitmap = scaled
            )
        )
    }

    internal fun computeInSampleSize(width: Int, height: Int, maxDim: Int): Int {
        var sample = 1
        val largest = max(width, height)
        while (largest / sample > maxDim * 2) {
            sample *= 2
        }
        return sample.coerceAtLeast(1)
    }

    internal fun scaleDownIfNeeded(bitmap: Bitmap, maxDim: Int): Bitmap {
        val largest = max(bitmap.width, bitmap.height)
        if (largest <= maxDim) return bitmap
        val ratio = maxDim.toFloat() / largest.toFloat()
        val targetW = (bitmap.width * ratio).toInt().coerceAtLeast(1)
        val targetH = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
    }
}
