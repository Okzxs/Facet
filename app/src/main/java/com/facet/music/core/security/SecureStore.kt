package com.facet.music.core.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager

/**
 * Шифрованное хранилище небольших секретов.
 *
 * Значения шифруются AES-256-GCM ключом Tink, а сам набор ключей защищён ключом
 * Android Keystore: в SharedPreferences не остаётся ни открытых токенов,
 * ни материала ключа. Имя значения передаётся как associated data, поэтому
 * зашифрованное значение нельзя переставить на другой ключ.
 *
 * Ошибки шифрования не пробрасываются в UI: при недоступном Keystore
 * приложение просто считает секрет отсутствующим и предлагает войти заново.
 */
class SecureStore internal constructor(
    private val prefs: SharedPreferences,
    private val aeadFactory: () -> Aead?,
) {

    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
        { createAead(context.applicationContext) },
    )

    private val aead: Aead? by lazy(aeadFactory)

    /** Есть ли значение по ключу (без расшифровки). */
    fun contains(key: String): Boolean = prefs.contains(key)

    /** Расшифрованное значение или null, если его нет либо оно повреждено. */
    fun getString(key: String): String? {
        val stored = prefs.getString(key, null) ?: return null
        val cipher = aead ?: return null
        return runCatching {
            String(
                cipher.decrypt(
                    Base64.decode(stored, Base64.NO_WRAP),
                    key.toByteArray(Charsets.UTF_8),
                ),
                Charsets.UTF_8,
            )
        }.onFailure { error ->
            Log.w(TAG, "Значение $key не расшифровалось: ${error.javaClass.simpleName}")
        }.getOrNull()
    }

    /** Шифрует и сохраняет значение. false — записать не удалось. */
    fun putString(key: String, value: String): Boolean {
        val cipher = aead ?: return false
        return runCatching {
            val encrypted = cipher.encrypt(
                value.toByteArray(Charsets.UTF_8),
                key.toByteArray(Charsets.UTF_8),
            )
            prefs.edit()
                .putString(key, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                .apply()
        }.onFailure { error ->
            Log.e(TAG, "Значение $key не зашифровалось: ${error.javaClass.simpleName}", error)
        }.isSuccess
    }

    fun getLong(key: String, defaultValue: Long = 0L): Long =
        getString(key)?.toLongOrNull() ?: defaultValue

    fun putLong(key: String, value: Long): Boolean = putString(key, value.toString())

    fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    /**
     * Одноразовый перенос открытых значений из старого файла настроек.
     *
     * Успешно зашифрованный ключ удаляется из источника; если шифрование
     * не удалось, открытое значение остаётся и миграция повторится при
     * следующем запуске.
     */
    fun migrateLegacy(source: SharedPreferences, keys: Collection<String>) {
        val present = keys.filter { source.contains(it) }
        if (present.isEmpty()) return
        val editor = source.edit()
        present.forEach { key ->
            val raw = source.all[key] ?: return@forEach
            val text = when (raw) {
                is String -> raw
                is Long -> raw.toString()
                is Int -> raw.toString()
                else -> return@forEach
            }
            if (prefs.contains(key)) {
                editor.remove(key)
                return@forEach
            }
            if (text.isEmpty()) {
                editor.remove(key)
                return@forEach
            }
            if (putString(key, text)) editor.remove(key)
        }
        editor.apply()
    }

    private companion object {
        const val TAG = "SecureStore"
        const val PREFS_NAME = "facet_secure"
        const val KEYSET_NAME = "facet_keyset"
        const val KEYSET_PREFS = "facet_keyset_prefs"
        const val MASTER_KEY_URI = "android-keystore://facet_master_key"
        const val KEY_TEMPLATE = "AES256_GCM"

        fun createAead(appContext: Context): Aead? = runCatching {
            AeadConfig.register()
            val keyset = AndroidKeysetManager.Builder()
                .withSharedPref(appContext, KEYSET_NAME, KEYSET_PREFS)
                .withKeyTemplate(KeyTemplates.get(KEY_TEMPLATE))
                .withMasterKeyUri(MASTER_KEY_URI)
                .build()
                .keysetHandle
            keyset.getPrimitive(Aead::class.java)
        }.onFailure { error ->
            Log.e(TAG, "Шифрованное хранилище недоступно: ${error.javaClass.simpleName}", error)
        }.getOrNull()
    }
}
