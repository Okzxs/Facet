package com.facet.music.core.security

import android.app.Application
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import com.google.crypto.tink.Aead
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SecureStoreTest {

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val securePrefs: SharedPreferences =
        context.getSharedPreferences("test_secure", 0)
    private val legacyPrefs: SharedPreferences =
        context.getSharedPreferences("test_legacy", 0)
    private val store by lazy { SecureStore(securePrefs) { FakeAead } }

    @Before
    fun clearPrefs() {
        securePrefs.edit().clear().commit()
        legacyPrefs.edit().clear().commit()
    }

    @Test
    fun `legacy string and long values move into secure storage`() {
        legacyPrefs.edit()
            .putString("access_token", "secret")
            .putLong("expires_at", 42L)
            .commit()

        store.migrateLegacy(legacyPrefs, listOf("access_token", "expires_at"))

        assertFalse(legacyPrefs.contains("access_token"))
        assertFalse(legacyPrefs.contains("expires_at"))
        assertEquals("secret", store.getString("access_token"))
        assertEquals(42L, store.getLong("expires_at"))
        val raw = securePrefs.getString("access_token", null).orEmpty()
        assertFalse(raw.contains("secret"))
    }

    @Test
    fun `empty legacy value is dropped instead of stored`() {
        legacyPrefs.edit().putString("spotify_client_secret", "").commit()

        store.migrateLegacy(legacyPrefs, listOf("spotify_client_secret"))

        assertFalse(legacyPrefs.contains("spotify_client_secret"))
        assertFalse(store.contains("spotify_client_secret"))
    }

    @Test
    fun `migration is idempotent`() {
        legacyPrefs.edit().putString("code_verifier", "verifier").commit()

        store.migrateLegacy(legacyPrefs, listOf("code_verifier"))
        store.migrateLegacy(legacyPrefs, listOf("code_verifier"))

        assertEquals("verifier", store.getString("code_verifier"))
        assertFalse(legacyPrefs.contains("code_verifier"))
    }

    @Test
    fun `put and remove work`() {
        assertTrue(store.putString("client_id", "abc"))
        assertTrue(store.contains("client_id"))
        assertEquals("abc", store.getString("client_id"))

        store.remove("client_id")

        assertFalse(store.contains("client_id"))
        assertNull(store.getString("client_id"))
    }

    @Test
    fun `corrupted value is ignored`() {
        securePrefs.edit().putString("broken", "not-base64").commit()

        assertNull(store.getString("broken"))
    }

    @Test
    fun `missing keystore does not crash and reports absence`() {
        val brokenStore = SecureStore(securePrefs) { null }

        assertFalse(brokenStore.putString("key", "value"))
        assertNull(brokenStore.getString("key"))
    }

    private object FakeAead : Aead {
        override fun encrypt(plaintext: ByteArray, associatedData: ByteArray): ByteArray =
            associatedData + byteArrayOf(0) + plaintext

        override fun decrypt(ciphertext: ByteArray, associatedData: ByteArray): ByteArray {
            require(ciphertext.size >= associatedData.size + 1) { "too short" }
            require(
                ciphertext.copyOfRange(0, associatedData.size).contentEquals(associatedData),
            ) { "associated data mismatch" }
            return ciphertext.copyOfRange(associatedData.size + 1, ciphertext.size)
        }
    }
}
