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
        BitmapFactory.decodeFile(file.absolutePath, opts)
    }.getOrNull()

    fun deleteQuietly(file: File?) {
        runCatching { file?.takeIf { it.exists() }?.delete() }
    }
}
