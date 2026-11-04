package com.wordlens.app.data.backup

import android.content.Context
import android.net.Uri
import com.wordlens.app.data.db.AppDatabase
import com.wordlens.app.data.db.DocumentRow
import com.wordlens.app.data.db.SavedWord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 备份 / 恢复。
 *
 * 只搬「文字数据」：识别记录（标题 + 全文 + 词框坐标）、生词本、自定义释义。
 * 照片文件不打包 —— 一张几 MB，几百条记录就是几百 MB，还要处理权限和压缩。
 * 恢复后记录仍在，只是没有原图可看（阅读页的「原图对照」会退化成只有单词）。
 *
 * 格式是普通 JSON，用文本编辑器就能看，也方便自己改。
 */
class BackupManager(
    context: Context,
    private val db: AppDatabase
) {

    private val appContext = context.applicationContext

    data class ImportResult(val documents: Int, val words: Int, val notes: Int, val skipped: Int)

    suspend fun suggestFileName(): String =
        "wordlens-backup-" + SimpleDateFormat("yyyyMMdd-HHmm", Locale.getDefault()).format(Date()) + ".json"

    /** 导出到用户选中的位置（SAF 给的 uri） */
    suspend fun exportTo(uri: Uri): Int = withContext(Dispatchers.IO) {
        val json = buildJson()
        appContext.contentResolver.openOutputStream(uri, "wt")?.use { out ->
            out.write(json.toByteArray(Charsets.UTF_8))
        } ?: error("无法写入所选位置")
        db.allDocuments().size + db.allSavedWords().size
    }

    /** 直接生成字符串（也方便以后走分享） */
    suspend fun buildJson(): String = withContext(Dispatchers.IO) {
        val root = JSONObject()
        root.put("app", "WordLens")
        root.put("format", 1)
        root.put("exportedAt", System.currentTimeMillis())

        val docs = JSONArray()
        for (d in db.allDocuments()) {
            docs.put(
                JSONObject().apply {
                    put("title", d.title)
                    put("fullText", d.fullText)
                    put("wordsJson", d.wordsJson)
                    put("imageWidth", d.imageWidth)
                    put("imageHeight", d.imageHeight)
                    put("wordCount", d.wordCount)
                    put("createdAt", d.createdAt)
                }
            )
        }
        root.put("documents", docs)

        val words = JSONArray()
        for (w in db.allSavedWords()) {
            words.put(
                JSONObject().apply {
                    put("word", w.word)
                    put("phonetic", w.phonetic)
                    put("translation", w.translation)
                    put("definition", w.definition)
                    put("tags", w.tags)
                    put("context", w.context)
                    put("source", w.source)
                    put("box", w.box)
                    put("dueAt", w.dueAt)
                    put("addedAt", w.addedAt)
                    put("reviewedAt", w.reviewedAt)
                    put("reviewCount", w.reviewCount)
                }
            )
        }
        root.put("words", words)

        val notes = JSONObject()
        for ((k, v) in db.allUserNotes()) notes.put(k, v)
        root.put("notes", notes)

        root.toString(2)
    }

    suspend fun importFrom(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        val text = appContext.contentResolver.openInputStream(uri)?.use { input ->
            input.bufferedReader(Charsets.UTF_8).readText()
        } ?: error("读不到所选文件")
        importJson(text)
    }

    suspend fun importJson(text: String): ImportResult = withContext(Dispatchers.IO) {
        val root = runCatching { JSONObject(text) }.getOrElse {
            error("这不是词镜的备份文件（JSON 解析失败）")
        }
        if (root.optString("app") != "WordLens") error("这不是词镜的备份文件")

        var docCount = 0
        var skipped = 0
        val docs = root.optJSONArray("documents") ?: JSONArray()
        for (i in 0 until docs.length()) {
            val d = docs.optJSONObject(i) ?: continue
            val fullText = d.optString("fullText", "")
            val wordsJson = d.optString("wordsJson", "")
            if (wordsJson.isBlank()) { skipped++; continue }
            // 同一次识别的正文相同，就不重复插入
            if (db.documentExistsByText(fullText)) { skipped++; continue }
            db.insertDocument(
                DocumentRow(
                    title = d.optString("title", "导入的记录"),
                    imagePath = null,
                    fullText = fullText,
                    wordsJson = wordsJson,
                    imageWidth = d.optInt("imageWidth", 0),
                    imageHeight = d.optInt("imageHeight", 0),
                    wordCount = d.optInt("wordCount", 0),
                    createdAt = d.optLong("createdAt", System.currentTimeMillis())
                )
            )
            docCount++
        }

        var wordCount = 0
        val words = root.optJSONArray("words") ?: JSONArray()
        for (i in 0 until words.length()) {
            val w = words.optJSONObject(i) ?: continue
            val word = w.optString("word", "").trim()
            if (word.isEmpty()) continue
            db.saveWord(
                SavedWord(
                    word = word,
                    phonetic = w.optString("phonetic", ""),
                    translation = w.optString("translation", ""),
                    definition = w.optString("definition", ""),
                    tags = w.optString("tags", ""),
                    context = w.optString("context", ""),
                    source = w.optString("source", "backup"),
                    box = w.optInt("box", 0),
                    dueAt = w.optLong("dueAt", 0),
                    addedAt = w.optLong("addedAt", System.currentTimeMillis()),
                    reviewedAt = w.optLong("reviewedAt", 0),
                    reviewCount = w.optInt("reviewCount", 0)
                )
            )
            wordCount++
        }

        var noteCount = 0
        root.optJSONObject("notes")?.let { notes ->
            for (key in notes.keys()) {
                val note = notes.optString(key, "")
                if (key.isNotBlank() && note.isNotBlank()) {
                    db.setUserNote(key, note)
                    noteCount++
                }
            }
        }

        ImportResult(documents = docCount, words = wordCount, notes = noteCount, skipped = skipped)
    }
}
