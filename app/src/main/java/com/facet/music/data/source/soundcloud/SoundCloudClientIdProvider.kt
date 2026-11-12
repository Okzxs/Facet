package com.facet.music.data.source.soundcloud

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.facet.music.core.network.ApiClient
import com.facet.music.core.network.NewPipeDownloader
import com.facet.music.core.network.executeCancellable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.schabi.newpipe.extractor.services.soundcloud.SoundcloudParsingHelper
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.soundCloudClientDataStore by preferencesDataStore(name = "soundcloud_client")

internal data class CachedClientId(
    val value: String,
    val fetchedAtMs: Long,
)

@Singleton
class SoundCloudClientIdProvider @Inject constructor(
    @ApplicationContext context: Context,
    private val newPipeDownloader: NewPipeDownloader,
    @ApiClient private val client: OkHttpClient,
) {

    private val store = SoundCloudClientStore(context.applicationContext)

    private val mutex = Mutex()

    @Volatile
    private var memory: String? = null

    suspend fun get(): String = mutex.withLock {
        memory?.let { return@withLock it }

        val cached = store.read()
        if (cached != null && isFresh(cached.fetchedAtMs)) {
            memory = cached.value
            return@withLock cached.value
        }

        val fresh = fetch()
        runCatching { store.write(fresh, System.currentTimeMillis()) }
        memory = fresh
        fresh
    }

    suspend fun invalidate() {
        mutex.withLock {
            memory = null
            runCatching { store.clear() }
        }
    }

    suspend fun warmUp() {
        runCatching { get() }
    }

    private suspend fun fetch(): String {
        fetchFromBundles()?.let { return it }
        return withContext(Dispatchers.IO) {
            newPipeDownloader.withCancellation {
                SoundcloudParsingHelper.clientId()
            } ?: throw IOException("SoundCloud не выдал client_id")
        }
    }

    private suspend fun fetchFromBundles(): String? {
        val html = try {
            client.executeCancellable(htmlRequest(HOMEPAGE)) { response ->
                if (!response.isSuccessful) return@executeCancellable null
                response.body?.string().orEmpty()
            }
        } catch (e: IOException) {
            return null
        } ?: return null

        val scripts = SCRIPT_REGEX.findAll(html)
            .map { it.value }
            .distinct()
            .take(MAX_SCRIPTS)
            .toList()

        for (script in scripts) {
            val js = try {
                client.executeCancellable(htmlRequest(script)) { response ->
                    if (!response.isSuccessful) return@executeCancellable null
                    response.body?.string().orEmpty()
                }
            } catch (e: IOException) {
                continue
            } ?: continue

            val id = CLIENT_ID_REGEX.find(js)?.groupValues?.getOrNull(1)
            if (!id.isNullOrBlank()) return id
        }
        return null
    }

    private fun htmlRequest(url: String): Request = Request.Builder()
        .url(url)
        .header("Accept", "text/html")
        .header("User-Agent", CHROME_USER_AGENT)
        .build()

    private fun isFresh(fetchedAtMs: Long): Boolean {
        val age = System.currentTimeMillis() - fetchedAtMs
        return age in 0..TTL_MS
    }

    private companion object {
        const val HOMEPAGE = "https://soundcloud.com/"
        const val TTL_MS = 24 * 60 * 60 * 1000L
        const val MAX_SCRIPTS = 16
        const val CHROME_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

        val SCRIPT_REGEX = Regex("""https://a-v2\.sndcdn\.com/assets/[A-Za-z0-9._-]+\.js""")
        val CLIENT_ID_REGEX = Regex("""client_id\s*[:=]\s*["']([A-Za-z0-9]{20,40})["']""")
    }
}

internal class SoundCloudClientStore(
    private val context: Context,
) {

    suspend fun read(): CachedClientId? {
        val prefs = try {
            context.soundCloudClientDataStore.data.first()
        } catch (e: IOException) {
            return null
        }
        val value = prefs[KEY_VALUE]?.takeIf { it.isNotBlank() } ?: return null
        val fetchedAt = prefs[KEY_FETCHED_AT] ?: return null
        return CachedClientId(value, fetchedAt)
    }

    suspend fun write(value: String, fetchedAtMs: Long) {
        context.soundCloudClientDataStore.edit { prefs ->
            prefs[KEY_VALUE] = value
            prefs[KEY_FETCHED_AT] = fetchedAtMs
        }
    }

    suspend fun clear() {
        context.soundCloudClientDataStore.edit { prefs ->
            prefs.remove(KEY_VALUE)
            prefs.remove(KEY_FETCHED_AT)
        }
    }

    private companion object {
        val KEY_VALUE = stringPreferencesKey("client_id")
        val KEY_FETCHED_AT = longPreferencesKey("fetched_at")
    }
}
