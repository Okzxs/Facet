package com.facet.music.core.player

import androidx.media3.common.Player
import kotlinx.coroutines.flow.StateFlow

/**
 * Абстракция подключения к MediaSession. Позволяет тестам подставить
 * фейковый [Player] и убирает MediaController из PlayerController.
 */
interface PlayerConnection {

    val player: StateFlow<Player?>

    /** Подключиться и дождаться плеера; null — сервис не поднялся за таймаут. */
    suspend fun connect(): Player?

    fun release()
}
