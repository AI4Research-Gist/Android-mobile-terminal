package com.example.ai4research.service

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import java.io.File

object OcrBitmapLoader {
    private const val TAG = "OcrBitmapLoader"
    private const val DEFAULT_MAX_EDGE = 1600

    fun loadBitmap(path: String, maxEdge: Int = DEFAULT_MAX_EDGE): Result<Bitmap> {
        return try {
            val imageFile = File(path)
            if (!imageFile.exists()) {
                return Result.failure(IllegalArgumentException("图片文件不存在"))
            }

            val boundsOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(path, boundsOptions)

            val sourceWidth = boundsOptions.outWidth
            val sourceHeight = boundsOptions.outHeight
            if (sourceWidth <= 0 || sourceHeight <= 0) {
                return Result.failure(IllegalArgumentException("无法读取图片尺寸"))
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = false
                inPreferredConfig = Bitmap.Config.RGB_565
                inSampleSize = calculateInSampleSize(
                    sourceWidth = sourceWidth,
                    sourceHeight = sourceHeight,
                    reqWidth = maxEdge,
                    reqHeight = maxEdge
                )
            }

            val decodedBitmap = BitmapFactory.decodeFile(path, decodeOptions)
                ?: return Result.failure(IllegalArgumentException("无法加载图片"))

            val finalBitmap = downscaleBitmapIfNeeded(decodedBitmap, maxEdge)
            Log.d(
                TAG,
                "Loaded OCR bitmap from $path, source=${sourceWidth}x${sourceHeight}, " +
                    "sampleSize=${decodeOptions.inSampleSize}, final=${finalBitmap.width}x${finalBitmap.height}"
            )
            Result.success(finalBitmap)
        } catch (oom: OutOfMemoryError) {
            Log.e(TAG, "Bitmap decode ran out of memory for $path", oom)
            Result.failure(Exception("图片过大，加载失败", oom))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load bitmap for OCR from $path", e)
            Result.failure(e)
        }
    }

    fun recycle(bitmap: Bitmap?) {
        if (bitmap != null && !bitmap.isRecycled) {
            bitmap.recycle()
        }
    }

    private fun downscaleBitmapIfNeeded(bitmap: Bitmap, maxEdge: Int): Bitmap {
        val maxSide = maxOf(bitmap.width, bitmap.height)
        if (maxSide <= maxEdge) return bitmap

        val scale = maxEdge.toFloat() / maxSide.toFloat()
        val scaledWidth = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val scaledHeight = (bitmap.height * scale).toInt().coerceAtLeast(1)

        return Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true).also { scaled ->
            if (scaled !== bitmap) {
                recycle(bitmap)
            }
        }
    }

    private fun calculateInSampleSize(
        sourceWidth: Int,
        sourceHeight: Int,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        var inSampleSize = 1
        var halfWidth = sourceWidth / 2
        var halfHeight = sourceHeight / 2

        while (halfWidth / inSampleSize >= reqWidth && halfHeight / inSampleSize >= reqHeight) {
            inSampleSize *= 2
        }

        return inSampleSize.coerceAtLeast(1)
    }
}
