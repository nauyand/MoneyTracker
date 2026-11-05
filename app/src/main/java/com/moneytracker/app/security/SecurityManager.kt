package com.moneytracker.app.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Keamanan aplikasi:
 *  - PIN TIDAK pernah disimpan. Yang disimpan hanya hash PBKDF2-HMAC-SHA256 (120.000 iterasi) + salt acak.
 *  - Kunci database SQLCipher (32 byte acak) dienkripsi AES-GCM dengan kunci di Android Keystore (tidak bisa diekstrak).
 *  - Aplikasi tidak memiliki field untuk password internet banking atau kredensial bank apa pun.
 */
class SecurityManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("mt_security", Context.MODE_PRIVATE)

    // ---------- Kunci database ----------
    fun databasePassphrase(): ByteArray {
        val stored = prefs.getString(KEY_DB_CIPHER, null)
        val iv = prefs.getString(KEY_DB_IV, null)
        if (stored != null && iv != null) {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, keystoreKey(), GCMParameterSpec(128, b64d(iv)))
            return cipher.doFinal(b64d(stored))
        }
        val pass = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, keystoreKey())
        prefs.edit()
            .putString(KEY_DB_CIPHER, b64e(cipher.doFinal(pass)))
            .putString(KEY_DB_IV, b64e(cipher.iv))
            .apply()
        return pass
    }

    private fun keystoreKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(KEYSTORE_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(KEYSTORE_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    // ---------- PIN ----------
    val hasPin: Boolean get() = prefs.contains(KEY_PIN_HASH)

    fun setPin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString(KEY_PIN_SALT, b64e(salt))
            .putString(KEY_PIN_HASH, b64e(hash(pin, salt)))
            .putInt(KEY_FAILS, 0)
            .apply()
    }

    /** @return true bila benar. Setelah 5x gagal, terkunci 30 detik (bertambah setiap kegagalan berikutnya). */
    fun verifyPin(pin: String): Boolean {
        if (lockedUntil() > System.currentTimeMillis()) return false
        val salt = prefs.getString(KEY_PIN_SALT, null)?.let(::b64d) ?: return false
        val expected = prefs.getString(KEY_PIN_HASH, null)?.let(::b64d) ?: return false
        val ok = MessageDigest.isEqual(expected, hash(pin, salt))
        if (ok) {
            prefs.edit().putInt(KEY_FAILS, 0).putLong(KEY_LOCKED_UNTIL, 0).apply()
        } else {
            val fails = prefs.getInt(KEY_FAILS, 0) + 1
            val e = prefs.edit().putInt(KEY_FAILS, fails)
            if (fails >= 5) e.putLong(KEY_LOCKED_UNTIL, System.currentTimeMillis() + 30_000L * (fails - 4))
            e.apply()
        }
        return ok
    }

    fun lockedUntil(): Long = prefs.getLong(KEY_LOCKED_UNTIL, 0)
    fun failedAttempts(): Int = prefs.getInt(KEY_FAILS, 0)

    private fun hash(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 120_000, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded.also { spec.clearPassword() }
    }

    // ---------- Preferensi ----------
    var biometricEnabled: Boolean
        get() = prefs.getBoolean(KEY_BIOMETRIC, false)
        set(v) = prefs.edit().putBoolean(KEY_BIOMETRIC, v).apply()

    /** Kunci otomatis setelah aplikasi di background selama N detik. */
    var lockTimeoutSec: Int
        get() = prefs.getInt(KEY_LOCK_TIMEOUT, 30)
        set(v) = prefs.edit().putInt(KEY_LOCK_TIMEOUT, v).apply()

    var secureScreen: Boolean
        get() = prefs.getBoolean(KEY_SECURE_SCREEN, true)
        set(v) = prefs.edit().putBoolean(KEY_SECURE_SCREEN, v).apply()

    /** Minta refresh rate layar tertinggi (90/120 Hz) agar animasi lebih mulus. */
    var highRefreshRate: Boolean
        get() = prefs.getBoolean(KEY_HIGH_REFRESH, true)
        set(v) = prefs.edit().putBoolean(KEY_HIGH_REFRESH, v).apply()

    var hideBalances: Boolean
        get() = prefs.getBoolean(KEY_HIDE_BALANCE, false)
        set(v) = prefs.edit().putBoolean(KEY_HIDE_BALANCE, v).apply()

    /** "dark" | "light" | "system" */
    var theme: String
        get() = prefs.getString(KEY_THEME, "dark") ?: "dark"
        set(v) = prefs.edit().putString(KEY_THEME, v).apply()

    var notifiedKeys: Set<String>
        get() = prefs.getStringSet(KEY_NOTIFIED, emptySet()) ?: emptySet()
        set(v) = prefs.edit().putStringSet(KEY_NOTIFIED, v.toList().takeLast(300).toSet()).apply()

    /** Menghapus semua preferensi & kunci (dipakai saat hapus akun). */
    fun wipe() {
        prefs.edit().clear().commit()
        runCatching {
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(KEYSTORE_ALIAS)
        }
    }

    companion object {
        private const val KEYSTORE_ALIAS = "moneytracker_db_key"
        private const val KEY_DB_CIPHER = "db_cipher"
        private const val KEY_DB_IV = "db_iv"
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_PIN_SALT = "pin_salt"
        private const val KEY_FAILS = "pin_fails"
        private const val KEY_LOCKED_UNTIL = "pin_locked_until"
        private const val KEY_BIOMETRIC = "biometric"
        private const val KEY_LOCK_TIMEOUT = "lock_timeout"
        private const val KEY_SECURE_SCREEN = "secure_screen"
        private const val KEY_HIDE_BALANCE = "hide_balance"
        private const val KEY_THEME = "theme"
        private const val KEY_HIGH_REFRESH = "high_refresh"
        private const val KEY_NOTIFIED = "notified_keys"

        fun b64e(b: ByteArray): String = Base64.encodeToString(b, Base64.NO_WRAP)
        fun b64d(s: String): ByteArray = Base64.decode(s, Base64.NO_WRAP)

        // ---------- Enkripsi berbasis password (untuk file backup) ----------
        private val MAGIC = "MTBK1".toByteArray()

        fun encryptWithPassword(plain: ByteArray, password: String): ByteArray {
            val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
            val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(128, iv))
            return MAGIC + salt + iv + cipher.doFinal(plain)
        }

        fun isEncryptedBackup(data: ByteArray): Boolean = data.size > MAGIC.size && data.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)

        fun decryptWithPassword(data: ByteArray, password: String): ByteArray {
            require(isEncryptedBackup(data)) { "Bukan file backup MoneyTracker terenkripsi" }
            var p = MAGIC.size
            val salt = data.copyOfRange(p, p + 16); p += 16
            val iv = data.copyOfRange(p, p + 12); p += 12
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(128, iv))
            return cipher.doFinal(data.copyOfRange(p, data.size))
        }

        private fun deriveKey(password: String, salt: ByteArray): SecretKey {
            val spec = PBEKeySpec(password.toCharArray(), salt, 150_000, 256)
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(bytes, "AES")
        }
    }
}
