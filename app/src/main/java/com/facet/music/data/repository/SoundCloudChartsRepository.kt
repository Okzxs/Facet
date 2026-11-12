package com.facet.music.data.repository

import com.facet.music.data.model.Track
import kotlinx.coroutines.flow.Flow

interface SoundCloudChartsRepository {

    fun observeNewAndHot(): Flow<List<Track>>

    fun observeTop50(): Flow<List<Track>>

    suspend fun getNewAndHot(forceRefresh: Boolean = false): Result<List<Track>>

    suspend fun getTop50(forceRefresh: Boolean = false): Result<List<Track>>
}
