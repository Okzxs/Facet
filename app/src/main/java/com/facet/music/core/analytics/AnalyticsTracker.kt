package com.facet.music.core.analytics

import android.content.Context
import android.util.Log
import com.facet.music.BuildConfig
import com.facet.music.core.network.ApiClient
import com.facet.music.data.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Имена событий; другие данные на сервер не уходят. */
object AnalyticsEvents {
    const val APP_OPEN = "app_open"
    const val TRACK_PLAY = "track_play"
    const val WAVE_SUMMARY = "wave_summary"
}

/**
 * Анонимная статистика использования.
 *
 * Согласие выключено по умолчанию и хранится в настройках. На сервер уходят
 * только название события, случайный идентификатор установки и версия
 * приложения — без аккаунта, треков, поисковых запросов и геолокации.
 * События копятся в памяти и отправляются пакетом.
 */
@Singleton
class AnalyticsTracker @Inject constructor(
    @ApplicationContext context: Context,
    @ApiClient private val client: OkHttpClient,
    private val settingsRepository: SettingsRepository,
    private val json: Json,
) {

    private val tag = "Analytics"

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queueMutex = Mutex()
    private val queue = ArrayDeque<String>()

    @Volatile
    private var enabled = false

    private var flushJob: Job? = null

    init {
        // Отписка от статистики должна не только запрещать новые события,
        // но и выбрасывать уже накопленные, ещё не отправленные.
        scope.launch {
            settingsRepository.settings.collect { settings ->
                enabled = settings.analyticsEnabled
                if (!enabled) {
                    queueMutex.withLock { queue.clear() }
                }
            }
        }
    }

    /** Случайный id установки: генерируется один раз, к личности не привязан. */
    private val installId: String by lazy {
        prefs.getString(KEY_INSTALL_ID, null) ?: UUID.randomUUID().toString().also { id ->
            prefs.edit().putString(KEY_INSTALL_ID, id).apply()
        }
    }

    /** Добавить событие в очередь; при выключенном согласии ничего не делает. */
    fun track(event: String) {
        if (event.isBlank()) return
        scope.launch {
            // Согласие читаем из настроек, а не только из кэша: так событие
            // не проскочит в момент выключения статистики. На холодном старте
            // это заодно дожидается первого значения и не теряет app_open.
            val allowed = settingsRepository.settings.first().analyticsEnabled
            enabled = allowed
            if (!allowed) return@launch
            val shouldFlush = queueMutex.withLock {
                // Ограничиваем очередь: при долгом отсутствии сети не растим память.
                if (queue.size >= MAX_QUEUE) queue.removeFirst()
                queue.addLast(event)
                queue.size >= BATCH_SIZE
            }
            if (shouldFlush) {
                flush()
            } else {
                scheduleFlush()
            }
        }
    }

    /** Отправить накопленное сейчас (например, при выходе из приложения). */
    fun flushAsync() {
        scope.launch { flush() }
    }

    private fun scheduleFlush() {
        if (flushJob?.isActive == true) return
        flushJob = scope.launch {
            delay(FLUSH_DELAY_MS)
            flush()
        }
    }

    private suspend fun flush() {
        if (!enabled) return
        val batch = queueMutex.withLock {
            if (queue.isEmpty()) return
            buildList {
                repeat(minOf(queue.size, BATCH_SIZE)) { add(queue.removeFirst()) }
            }
        }

        val payload = buildJsonArray {
            batch.forEach { event ->
                add(
                    buildJsonObject {
                        put("event", event)
                        put("install_id", installId)
                        put("app_version", BuildConfig.VERSION_NAME)
                    },
                )
            }
        }.toString()

        val request = Request.Builder()
            .url(EVENTS_URL)
            .header("apikey", API_KEY)
            .header("Authorization", "Bearer $API_KEY")
            .header("Content-Type", "application/json")
            .header("Prefer", "return=minimal")
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val sent = runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(tag, "Статистика не отправлена: HTTP ${response.code}")
                }
                response.isSuccessful
            }
        }.getOrElse { error ->
            Log.w(tag, "Статистика не отправлена: ${error.message}")
            false
        }

        if (sent) {
            Log.d(tag, "Отправлено событий: ${batch.size}")
        } else {
            // Сеть пропала — вернём пачку в начало, чтобы не потерять события.
            queueMutex.withLock {
                batch.asReversed().forEach { event ->
                    if (queue.size < MAX_QUEUE) queue.addFirst(event)
                }
            }
        }
    }

    private companion object {
        const val PREFS_NAME = "facet_analytics"
        const val KEY_INSTALL_ID = "install_id"
        const val BATCH_SIZE = 10
        const val MAX_QUEUE = 50
        const val FLUSH_DELAY_MS = 8_000L

        const val EVENTS_URL = "https://fybzqjosijpdvbhckkxu.supabase.co/rest/v1/events"

        // Publishable-ключ Supabase: рассчитан на клиент, доступ ограничен RLS
        // (только вставка событий, чтение закрыто).
        const val API_KEY = "sb_publishable_8IXOORUEvtiZnQ23Efmvbw_tg8IX31p"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
