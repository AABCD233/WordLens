package com.wordlens.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.util.UUID
import kotlin.math.max

/** 图片落盘 + 按需缩略解码，避免 OCR 大图直接把内存打爆 */
object ImageFiles {

    private const val DOC_DIR = "documents"

    fun documentsDir(context: Context): File =
        File(context.filesDir, DOC_DIR).apply { mkdirs() }

    /** 相机拍照的输出文件 */
    fun newCaptureFile(context: Context): File {
        val f = File(documentsDir(context), "cap_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.jpg")
        f.parentFile?.mkdirs()
        return f
    }

    /**
     * 相册选来的图只拿到临时授权，复制一份到私有目录，
     * 之后离线也能回看原图。
     */
    fun copyIntoApp(context: Context, uri: Uri): File? = runCatching {
        val out = File(documentsDir(context), "img_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.jpg")
        context.contentResolver.openInputStream(uri)?.use { input ->
            out.outputStream().use { output -> input.copyTo(output) }
        } ?: return null
        if (out.length() <= 0L) {
            out.delete()
            null
        } else out
    }.getOrNull()

    /** 解码时按目标尺寸做 inSampleSize，长边不超过 maxSide */
    fun loadSampled(file: File, maxSide: Int = 2048): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        val longest = max(bounds.outWidth, bounds.outHeight)
        while (longest / (sample * 2) >= maxSide) sample *= 2

        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        val decoded = BitmapFactory.decodeFile(file.absolutePath, opts) ?: return null
        applyExifRotation(file, decoded)
    }.getOrNull()

    /**
     * 按 EXIF 方向把位图转正。
     *
     * 这一步不能省：ML Kit 的 InputImage.fromFilePath 会读 EXIF 并自动旋转，
     * 而 BitmapFactory 不会。两边方向不一致时，词框坐标就和画出来的图对不上，
     * 表现出来就是「图片被拉伸/歪着」。
     */
    private fun applyExifRotation(file: File, bmp: Bitmap): Bitmap {
        val degrees = runCatching {
            val exif = android.media.ExifInterface(file.absolutePath)
            when (exif.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1)) {
                android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        }.getOrDefault(0f)
        if (degrees == 0f) return bmp
        return runCatching {
            val m = android.graphics.Matrix().apply { postRotate(degrees) }
            val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            if (rotated !== bmp) bmp.recycle()
            rotated
        }.getOrDefault(bmp)
    }

    fun deleteQuietly(file: File?) {
        runCatching { file?.takeIf { it.exists() }?.delete() }
    }
}
