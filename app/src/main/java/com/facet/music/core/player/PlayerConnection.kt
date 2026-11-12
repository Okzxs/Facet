package com.facet.music.core.player

import androidx.media3.common.Player
import kotlinx.coroutines.flow.StateFlow

interface PlayerConnection {

    val player: StateFlow<Player?>

    suspend fun connect(): Player?

    fun release()
}
