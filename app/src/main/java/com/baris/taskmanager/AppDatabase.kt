package com.baris.taskmanager

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.security.SecureRandom
import java.security.MessageDigest
import java.security.spec.KeySpec
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import android.util.Base64
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun dayKey(time: Long = System.currentTimeMillis()): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date(time))

data class Task(
    val id: Long,
    val userId: Long,
    val title: String,
    val description: String,
    val category: String,
    val done: Boolean,
    val createdAt: Long,
    val repeatDaily: Boolean,
    val plannedDate: String
)

class AppDatabase(context: Context) : SQLiteOpenHelper(context, "task_manager.db", null, 2) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE users (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, email TEXT NOT NULL UNIQUE, salt TEXT NOT NULL, password_hash TEXT NOT NULL)")
        db.execSQL("CREATE TABLE tasks (id INTEGER PRIMARY KEY AUTOINCREMENT, user_id INTEGER NOT NULL, title TEXT NOT NULL, description TEXT NOT NULL, category TEXT NOT NULL, done INTEGER NOT NULL DEFAULT 0, created_at INTEGER NOT NULL, repeat_daily INTEGER NOT NULL DEFAULT 0, planned_date TEXT NOT NULL, FOREIGN KEY(user_id) REFERENCES users(id))")
        db.execSQL("CREATE TABLE task_completions (task_id INTEGER NOT NULL, day TEXT NOT NULL, PRIMARY KEY(task_id, day), FOREIGN KEY(task_id) REFERENCES tasks(id))")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            val today = dayKey()
            db.execSQL("ALTER TABLE tasks ADD COLUMN repeat_daily INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE tasks ADD COLUMN planned_date TEXT NOT NULL DEFAULT '$today'")
            db.execSQL("CREATE TABLE task_completions (task_id INTEGER NOT NULL, day TEXT NOT NULL, PRIMARY KEY(task_id, day), FOREIGN KEY(task_id) REFERENCES tasks(id))")
            db.execSQL("INSERT INTO task_completions(task_id, day) SELECT id, ? FROM tasks WHERE done = 1", arrayOf(today))
        }
    }

    private fun hash(password: String, salt: ByteArray): String {
        val spec: KeySpec = PBEKeySpec(password.toCharArray(), salt, 120_000, 256)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    fun register(name: String, email: String, password: String): Long? {
        val normalized = email.trim().lowercase()
        if (findUserId(normalized) != null) return null
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val values = ContentValues().apply {
            put("name", name.trim())
            put("email", normalized)
            put("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            put("password_hash", hash(password, salt))
        }
        return writableDatabase.insert("users", null, values).takeIf { it > 0 }
    }

    private fun findUserId(email: String): Long? = readableDatabase.rawQuery(
        "SELECT id FROM users WHERE email = ?", arrayOf(email)
    ).use { if (it.moveToFirst()) it.getLong(0) else null }

    fun login(email: String, password: String): Long? = readableDatabase.rawQuery(
        "SELECT id, salt, password_hash FROM users WHERE email = ?", arrayOf(email.trim().lowercase())
    ).use {
        if (!it.moveToFirst()) return@use null
        val salt = Base64.decode(it.getString(1), Base64.NO_WRAP)
        val actual = Base64.decode(hash(password, salt), Base64.NO_WRAP)
        val expected = Base64.decode(it.getString(2), Base64.NO_WRAP)
        if (MessageDigest.isEqual(actual, expected)) it.getLong(0) else null
    }

    fun userName(userId: Long): String = readableDatabase.rawQuery(
        "SELECT name FROM users WHERE id = ?", arrayOf(userId.toString())
    ).use { if (it.moveToFirst()) it.getString(0) else "" }

    fun userExists(userId: Long): Boolean = readableDatabase.rawQuery(
        "SELECT 1 FROM users WHERE id = ?", arrayOf(userId.toString())
    ).use { it.moveToFirst() }

    fun tasks(userId: Long, day: String): List<Task> = readableDatabase.rawQuery(
        """SELECT t.id, t.user_id, t.title, t.description, t.category,
                  CASE WHEN c.task_id IS NULL THEN 0 ELSE 1 END, t.created_at, t.repeat_daily, t.planned_date
           FROM tasks t LEFT JOIN task_completions c ON c.task_id = t.id AND c.day = ?
           WHERE t.user_id = ? AND ((t.repeat_daily = 1 AND t.planned_date <= ?) OR (t.repeat_daily = 0 AND t.planned_date = ?))""",
        arrayOf(day, userId.toString(), day, day)
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) add(Task(
                cursor.getLong(0), cursor.getLong(1), cursor.getString(2), cursor.getString(3),
                cursor.getString(4), cursor.getInt(5) == 1, cursor.getLong(6),
                cursor.getInt(7) == 1, cursor.getString(8)
            ))
        }
    }

    fun saveTask(userId: Long, task: Task?, title: String, description: String, category: String,
                 repeatDaily: Boolean, plannedDate: String): Boolean {
        val values = ContentValues().apply {
            put("title", title.trim())
            put("description", description.trim())
            put("category", category)
            put("repeat_daily", if (repeatDaily) 1 else 0)
            put("planned_date", plannedDate)
            if (task == null) {
                put("user_id", userId)
                put("done", 0)
                put("created_at", System.currentTimeMillis())
            }
        }
        return if (task == null) writableDatabase.insert("tasks", null, values) > 0
        else {
            val updated = writableDatabase.update("tasks", values, "id = ? AND user_id = ?",
                arrayOf(task.id.toString(), userId.toString())) > 0
            if (updated && !repeatDaily) writableDatabase.delete("task_completions",
                "task_id = ? AND day != ?", arrayOf(task.id.toString(), plannedDate))
            updated
        }
    }

    fun setDone(userId: Long, taskId: Long, day: String, done: Boolean) {
        if (!readableDatabase.rawQuery("SELECT 1 FROM tasks WHERE id = ? AND user_id = ?",
                arrayOf(taskId.toString(), userId.toString())).use { it.moveToFirst() }) return
        if (done) writableDatabase.insertWithOnConflict("task_completions", null,
            ContentValues().apply { put("task_id", taskId); put("day", day) }, SQLiteDatabase.CONFLICT_IGNORE)
        else writableDatabase.delete("task_completions", "task_id = ? AND day = ?", arrayOf(taskId.toString(), day))
    }

    fun deleteTask(userId: Long, taskId: Long) {
        writableDatabase.execSQL("DELETE FROM task_completions WHERE task_id = ? AND task_id IN " +
            "(SELECT id FROM tasks WHERE user_id = ?)", arrayOf(taskId, userId))
        writableDatabase.delete("tasks", "id = ? AND user_id = ?", arrayOf(taskId.toString(), userId.toString()))
    }

    fun markedDays(userId: Long, monthPrefix: String): Set<String> = readableDatabase.rawQuery(
        """SELECT DISTINCT c.day FROM task_completions c JOIN tasks t ON t.id = c.task_id
           WHERE t.user_id = ? AND c.day LIKE ?""", arrayOf(userId.toString(), "$monthPrefix%")
    ).use { cursor -> buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) } }

    fun plannedDays(userId: Long, monthPrefix: String): Set<String> = readableDatabase.rawQuery(
        "SELECT DISTINCT planned_date FROM tasks WHERE user_id = ? AND repeat_daily = 0 AND planned_date LIKE ?",
        arrayOf(userId.toString(), "$monthPrefix%")
    ).use { cursor -> buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) } }
}
