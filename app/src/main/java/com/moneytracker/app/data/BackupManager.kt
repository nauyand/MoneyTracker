package com.moneytracker.app.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import com.moneytracker.app.security.SecurityManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Backup generik: setiap tabel Room diserialisasi ke JSON (nama kolom apa adanya),
 * lalu dienkripsi AES-GCM dengan kunci turunan password (PBKDF2). Restore mengganti seluruh data
 * dalam satu transaksi database (semua-atau-tidak-sama-sekali).
 */
class BackupManager(private val context: Context, private val db: AppDatabase) {

    suspend fun exportJson(): JSONObject = withContext(Dispatchers.IO) {
        val root = JSONObject()
        root.put("app", "MoneyTracker")
        root.put("format", 1)
        root.put("schemaVersion", 1)
        root.put("exportedAt", System.currentTimeMillis())
        val tables = JSONObject()
        val sdb = db.openHelper.readableDatabase
        for (t in AppDatabase.TABLES) {
            val arr = JSONArray()
            sdb.query("SELECT * FROM `$t`").use { c ->
                while (c.moveToNext()) {
                    val o = JSONObject()
                    for (i in 0 until c.columnCount) {
                        val name = c.getColumnName(i)
                        when (c.getType(i)) {
                            Cursor.FIELD_TYPE_NULL -> o.put(name, JSONObject.NULL)
                            Cursor.FIELD_TYPE_INTEGER -> o.put(name, c.getLong(i))
                            Cursor.FIELD_TYPE_FLOAT -> o.put(name, c.getDouble(i))
                            Cursor.FIELD_TYPE_STRING -> o.put(name, c.getString(i))
                            Cursor.FIELD_TYPE_BLOB -> o.put(name, SecurityManager.b64e(c.getBlob(i)))
                        }
                    }
                    arr.put(o)
                }
            }
            tables.put(t, arr)
        }
        root.put("tables", tables)
        root
    }

    suspend fun writeBackup(uri: Uri, password: String?) = withContext(Dispatchers.IO) {
        val bytes = exportJson().toString().toByteArray(Charsets.UTF_8)
        val out = if (password.isNullOrEmpty()) bytes else SecurityManager.encryptWithPassword(bytes, password)
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(out) } ?: error("Tidak bisa menulis file")
    }

    /** @throws IllegalArgumentException bila password salah / file tidak valid. */
    suspend fun restore(uri: Uri, password: String?): Int = withContext(Dispatchers.IO) {
        val raw = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Tidak bisa membaca file")
        val json = if (SecurityManager.isEncryptedBackup(raw)) {
            if (password.isNullOrEmpty()) throw IllegalArgumentException("File backup terenkripsi. Masukkan password backup.")
            try { SecurityManager.decryptWithPassword(raw, password) } catch (e: Exception) { throw IllegalArgumentException("Password salah atau file rusak") }
        } else raw
        val root = try { JSONObject(String(json, Charsets.UTF_8)) } catch (e: Exception) { throw IllegalArgumentException("Format file tidak dikenali") }
        if (root.optString("app") != "MoneyTracker") throw IllegalArgumentException("Bukan file backup MoneyTracker")
        val tables = root.getJSONObject("tables")
        var count = 0
        db.runInTransaction {
            val w = db.openHelper.writableDatabase
            for (t in AppDatabase.TABLES.reversed()) w.execSQL("DELETE FROM `$t`")
            for (t in AppDatabase.TABLES) {
                val arr = tables.optJSONArray(t) ?: continue
                val columns = w.query("PRAGMA table_info(`$t`)").use { c -> buildSet { while (c.moveToNext()) add(c.getString(c.getColumnIndexOrThrow("name"))) } }
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val cv = ContentValues()
                    for (k in o.keys()) {
                        if (k !in columns) continue // kolom dari versi lain diabaikan
                        when (val v = o.get(k)) {
                            JSONObject.NULL -> cv.putNull(k)
                            is Int -> cv.put(k, v.toLong())
                            is Long -> cv.put(k, v)
                            is Double -> cv.put(k, v)
                            is Boolean -> cv.put(k, if (v) 1L else 0L)
                            else -> cv.put(k, v.toString())
                        }
                    }
                    w.insert(t, SQLiteDatabase.CONFLICT_REPLACE, cv)
                    count++
                }
            }
        }
        count
    }
}
