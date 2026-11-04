package com.facet.music.core.player

import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MediaController за интерфейсом [PlayerConnection]. Держит подключение
 * к MusicService, переподключается после смерти сервиса и отдаёт Player через
 * StateFlow — PlayerController не знает про MediaController и SessionToken.
 */
@Singleton
class MediaSessionPlayerConnection @Inject constructor(
    @ApplicationContext private val context: Context,
) : PlayerConnection {

    private val tag = "PlayerConnection"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _player = MutableStateFlow<Player?>(null)
    override val player: StateFlow<Player?> = _player.asStateFlow()

    private var controller: MediaController? = null
    private var connecting = false
    private var attempts = 0
    private var released = false

    private val controllerListener = object : MediaController.Listener {
        override fun onDisconnected(controller: MediaController) {
            if (this@MediaSessionPlayerConnection.controller !== controller) return
            Log.w(tag, "MediaSession отключена — переподключаемся")
            this@MediaSessionPlayerConnection.controller = null
            _player.value = null
            connecting = false
            scheduleReconnect()
        }
    }

    override suspend fun connect(): Player? {
        _player.value?.let { return it }
        startConnecting()
        return withTimeoutOrNull(CONNECT_TIMEOUT_MS) {
            player.filterNotNull().first()
        }
    }

    @Synchronized
    private fun startConnecting() {
        if (released || connecting || controller != null) return
        connecting = true
        val token = SessionToken(
            context,
            ComponentName(context, MusicService::class.java),
        )
        val future = MediaController.Builder(context, token)
            .setListener(controllerListener)
            .buildAsync()
        future.addListener(
            {
                try {
                    val connected = future.get()
                    controller = connected
                    connecting = false
                    attempts = 0
                    _player.value = connected
                } catch (e: Exception) {
                    connecting = false
                    Log.w(tag, "Не удалось подключиться к MusicService", e)
                    scheduleReconnect()
                }
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    private fun scheduleReconnect() {
        if (released) return
        if (attempts >= MAX_ATTEMPTS) {
            Log.e(tag, "Подключение не удалось после $attempts попыток")
            return
        }
        val delayMs = RECONNECT_BASE_MS shl attempts.coerceAtMost(6)
        attempts++
        scope.launch {
            delay(delayMs)
            startConnecting()
        }
    }

    override fun release() {
        released = true
        runCatching { controller?.release() }
        controller = null
        _player.value = null
        scope.cancel()
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 3_000L
        const val RECONNECT_BASE_MS = 500L
        const val MAX_ATTEMPTS = 5
    }
}
