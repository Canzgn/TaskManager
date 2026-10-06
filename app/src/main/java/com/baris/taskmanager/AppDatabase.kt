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

data class Task(
    val id: Long,
    val userId: Long,
    val title: String,
    val description: String,
    val category: String,
    val done: Boolean,
    val createdAt: Long
)

class AppDatabase(context: Context) : SQLiteOpenHelper(context, "task_manager.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE users (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, email TEXT NOT NULL UNIQUE, salt TEXT NOT NULL, password_hash TEXT NOT NULL)")
        db.execSQL("CREATE TABLE tasks (id INTEGER PRIMARY KEY AUTOINCREMENT, user_id INTEGER NOT NULL, title TEXT NOT NULL, description TEXT NOT NULL, category TEXT NOT NULL, done INTEGER NOT NULL DEFAULT 0, created_at INTEGER NOT NULL, FOREIGN KEY(user_id) REFERENCES users(id))")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

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

    fun tasks(userId: Long): List<Task> = readableDatabase.rawQuery(
        "SELECT id, user_id, title, description, category, done, created_at FROM tasks WHERE user_id = ?",
        arrayOf(userId.toString())
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) add(Task(
                cursor.getLong(0), cursor.getLong(1), cursor.getString(2), cursor.getString(3),
                cursor.getString(4), cursor.getInt(5) == 1, cursor.getLong(6)
            ))
        }
    }

    fun saveTask(userId: Long, task: Task?, title: String, description: String, category: String): Boolean {
        val values = ContentValues().apply {
            put("title", title.trim())
            put("description", description.trim())
            put("category", category)
            if (task == null) {
                put("user_id", userId)
                put("done", 0)
                put("created_at", System.currentTimeMillis())
            }
        }
        return if (task == null) writableDatabase.insert("tasks", null, values) > 0
        else writableDatabase.update("tasks", values, "id = ? AND user_id = ?", arrayOf(task.id.toString(), userId.toString())) > 0
    }

    fun setDone(userId: Long, taskId: Long, done: Boolean) {
        writableDatabase.update("tasks", ContentValues().apply { put("done", if (done) 1 else 0) },
            "id = ? AND user_id = ?", arrayOf(taskId.toString(), userId.toString()))
    }

    fun deleteTask(userId: Long, taskId: Long) {
        writableDatabase.delete("tasks", "id = ? AND user_id = ?", arrayOf(taskId.toString(), userId.toString()))
    }
}
