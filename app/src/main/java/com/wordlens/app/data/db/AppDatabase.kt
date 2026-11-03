package com.wordlens.app.data.db

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * App 自己的业务库：识别文档、生词本、网络缓存。
 *
 * 这里刻意没有用 Room —— 少一个注解处理器（KSP/kapt），
 * CI 上就少一类「插件版本和 Kotlin 版本不匹配」的构建失败。
 */
class AppDatabase(context: Context) :
    SQLiteOpenHelper(context.applicationContext, NAME, null, VERSION) {

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) = with(db) {
        execSQL(
            """
            CREATE TABLE IF NOT EXISTS document (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              title TEXT NOT NULL,
              image_path TEXT,
              full_text TEXT NOT NULL,
              words_json TEXT NOT NULL,
              image_width INTEGER NOT NULL DEFAULT 0,
              image_height INTEGER NOT NULL DEFAULT 0,
              word_count INTEGER NOT NULL DEFAULT 0,
              created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        execSQL("CREATE INDEX IF NOT EXISTS idx_document_created ON document(created_at DESC)")
        execSQL(
            """
            CREATE TABLE IF NOT EXISTS saved_word (
              word TEXT PRIMARY KEY,
              phonetic TEXT NOT NULL DEFAULT '',
              translation TEXT NOT NULL DEFAULT '',
              definition TEXT NOT NULL DEFAULT '',
              tags TEXT NOT NULL DEFAULT '',
              context TEXT NOT NULL DEFAULT '',
              source TEXT NOT NULL DEFAULT 'ocr',
              box INTEGER NOT NULL DEFAULT 0,
              due_at INTEGER NOT NULL DEFAULT 0,
              added_at INTEGER NOT NULL DEFAULT 0,
              reviewed_at INTEGER NOT NULL DEFAULT 0,
              review_count INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        execSQL("CREATE INDEX IF NOT EXISTS idx_saved_due ON saved_word(due_at)")
        execSQL(
            """
            CREATE TABLE IF NOT EXISTS web_cache (
              key TEXT PRIMARY KEY,
              payload TEXT NOT NULL,
              updated_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // 目前只有 v1；将来加列就写在这里（保持向后兼容，不删用户数据）
        if (oldVersion < 1) onCreate(db)
    }

    // ---------------- document ----------------

    suspend fun insertDocument(row: DocumentRow): Long = write { db ->
        db.insert("document", null, row.toValues())
    }

    suspend fun document(id: Long): DocumentRow? = read { db ->
        db.query("document", null, "id=?", arrayOf(id.toString()), null, null, null)
            .use { if (it.moveToFirst()) it.toDocumentRow() else null }
    }

    suspend fun recentDocuments(limit: Int = 50): List<DocumentRow> = read { db ->
        db.query("document", null, null, null, null, null, "created_at DESC", limit.toString())
            .useList { it.toDocumentRow() }
    }

    /** 全文检索：在识别出来的正文里找关键词 */
    suspend fun searchDocuments(keyword: String, limit: Int = 50): List<DocumentRow> = read { db ->
        val like = "%" + keyword.replace("%", "\\%").replace("_", "\\_") + "%"
        db.query(
            "document", null,
            "full_text LIKE ? ESCAPE '\\'", arrayOf(like),
            null, null, "created_at DESC", limit.toString()
        ).useList { it.toDocumentRow() }
    }

    suspend fun deleteDocument(id: Long) = write { db ->
        db.delete("document", "id=?", arrayOf(id.toString()))
    }

    suspend fun updateDocumentText(id: Long, title: String) = write { db ->
        db.update("document", ContentValues().apply { put("title", title) }, "id=?", arrayOf(id.toString()))
    }

    // ---------------- saved_word ----------------

    /** 已存在就只补齐缺失字段，不覆盖复习进度 */
    suspend fun saveWord(row: SavedWord): Unit = write { db ->
        val existing = db.query("saved_word", null, "word=?", arrayOf(row.word), null, null, null)
            .useList { it.toSavedWord() }.firstOrNull()
        val values = row.copy(
            phonetic = row.phonetic.ifBlank { existing?.phonetic ?: "" },
            translation = row.translation.ifBlank { existing?.translation ?: "" },
            definition = row.definition.ifBlank { existing?.definition ?: "" },
            box = existing?.box ?: row.box,
            dueAt = existing?.dueAt ?: row.dueAt,
            addedAt = existing?.addedAt?.takeIf { it > 0 } ?: row.addedAt,
            reviewedAt = existing?.reviewedAt ?: row.reviewedAt,
            reviewCount = existing?.reviewCount ?: row.reviewCount
        ).toValues()
        db.insertWithOnConflict("saved_word", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    suspend fun savedWords(query: String? = null, limit: Int = 500): List<SavedWord> = read { db ->
        val (selection, args) = if (query.isNullOrBlank()) {
            null to null
        } else {
            "word LIKE ? ESCAPE '\\'" to arrayOf("%${query.escapeLike()}%")
        }
        db.query("saved_word", null, selection, args, null, null, "added_at DESC", limit.toString())
            .useList { it.toSavedWord() }
    }

    suspend fun dueWords(now: Long, limit: Int = 20): List<SavedWord> = read { db ->
        db.query(
            "saved_word", null, "due_at<=?", arrayOf(now.toString()), null, null,
            "due_at ASC, box ASC", limit.toString()
        ).useList { it.toSavedWord() }
    }

    suspend fun wordStats(): Pair<Int, Int> = read { db ->
        val total = db.simpleCount("saved_word")
        val due = db.query(
            "saved_word", arrayOf("count(1)"), "due_at<=?",
            arrayOf(System.currentTimeMillis().toString()), null, null, null
        ).use { if (it.moveToFirst()) it.getInt(0) else 0 }
        total to due
    }

    suspend fun deleteWord(word: String) = write { db ->
        db.delete("saved_word", "word=?", arrayOf(word))
    }

    suspend fun applyReview(word: String, box: Int, dueAt: Long) = write { db ->
        db.execSQL(
            "UPDATE saved_word SET box=?, due_at=?, reviewed_at=?, review_count=review_count+1 WHERE word=?",
            arrayOf(box, dueAt, System.currentTimeMillis(), word)
        )
    }

    // ---------------- web_cache ----------------

    suspend fun cacheGet(key: String): String? = read { db ->
        db.query("web_cache", arrayOf("payload"), "key=?", arrayOf(key), null, null, null)
            .use { if (it.moveToFirst()) it.getString(0) else null }
    }

    // 显式返回 Unit：insertWithOnConflict 返回 rowId，不能让 put() 跟着变成 Long
    suspend fun cachePut(key: String, payload: String) {
        write { db ->
            val v = ContentValues().apply {
                put("key", key); put("payload", payload); put("updated_at", System.currentTimeMillis())
            }
            db.insertWithOnConflict("web_cache", null, v, SQLiteDatabase.CONFLICT_REPLACE)
            Unit
        }
    }

    suspend fun cacheClear() {
        write { db ->
            db.delete("web_cache", null, null)
            Unit
        }
    }

    // ---------------- helpers ----------------

    private suspend fun <T> read(block: (SQLiteDatabase) -> T): T =
        withContext(Dispatchers.IO) { block(readableDatabase) }

    private suspend fun <T> write(block: (SQLiteDatabase) -> T): T =
        withContext(Dispatchers.IO) { block(writableDatabase) }

    private fun DocumentRow.toValues() = ContentValues().apply {
        if (id > 0) put("id", id)
        put("title", title); put("image_path", imagePath ?: "")
        put("full_text", fullText); put("words_json", wordsJson)
        put("image_width", imageWidth); put("image_height", imageHeight)
        put("word_count", wordCount); put("created_at", createdAt)
    }

    private fun SavedWord.toValues() = ContentValues().apply {
        put("word", word); put("phonetic", phonetic); put("translation", translation)
        put("definition", definition); put("tags", tags); put("context", context)
        put("source", source); put("box", box); put("due_at", dueAt)
        put("added_at", addedAt); put("reviewed_at", reviewedAt); put("review_count", reviewCount)
    }

    private fun Cursor.toDocumentRow() = DocumentRow(
        id = getLong(getColumnIndexOrThrow("id")),
        title = getString(getColumnIndexOrThrow("title")),
        imagePath = getString(getColumnIndexOrThrow("image_path")).takeIf { it.isNotBlank() },
        fullText = getString(getColumnIndexOrThrow("full_text")),
        wordsJson = getString(getColumnIndexOrThrow("words_json")),
        imageWidth = getInt(getColumnIndexOrThrow("image_width")),
        imageHeight = getInt(getColumnIndexOrThrow("image_height")),
        wordCount = getInt(getColumnIndexOrThrow("word_count")),
        createdAt = getLong(getColumnIndexOrThrow("created_at"))
    )

    private fun Cursor.toSavedWord() = SavedWord(
        word = getString(getColumnIndexOrThrow("word")),
        phonetic = getString(getColumnIndexOrThrow("phonetic")),
        translation = getString(getColumnIndexOrThrow("translation")),
        definition = getString(getColumnIndexOrThrow("definition")),
        tags = getString(getColumnIndexOrThrow("tags")),
        context = getString(getColumnIndexOrThrow("context")),
        source = getString(getColumnIndexOrThrow("source")),
        box = getInt(getColumnIndexOrThrow("box")),
        dueAt = getLong(getColumnIndexOrThrow("due_at")),
        addedAt = getLong(getColumnIndexOrThrow("added_at")),
        reviewedAt = getLong(getColumnIndexOrThrow("reviewed_at")),
        reviewCount = getInt(getColumnIndexOrThrow("review_count"))
    )

    private fun String.escapeLike() = replace("%", "\\%").replace("_", "\\_")

    private fun SQLiteDatabase.simpleCount(table: String): Int =
        rawQuery("SELECT COUNT(1) FROM $table", null)
            .use { if (it.moveToFirst()) it.getInt(0) else 0 }

    companion object {
        private const val NAME = "wordlens.db"
        private const val VERSION = 1
    }
}

/** Cursor  Closeable.use 的变体，把每行映射成对象 */
private inline fun <R> Cursor.useList(mapping: (Cursor) -> R): List<R> {
    return use {
        val out = ArrayList<R>(count)
        while (moveToNext()) out.add(mapping(this))
        out
    }
}
