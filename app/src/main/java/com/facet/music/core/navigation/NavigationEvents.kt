package com.facet.music.core.navigation

import com.facet.music.core.service.ServiceType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

data class SearchIntent(
    val query: String,
    val service: ServiceType,
)

@Singleton
class NavigationEvents @Inject constructor() {

    private val _searchIntents = MutableSharedFlow<SearchIntent>(
        replay = 1,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val searchIntents: SharedFlow<SearchIntent> = _searchIntents.asSharedFlow()

    fun requestSearch(query: String, service: ServiceType) {
        _searchIntents.tryEmit(SearchIntent(query = query, service = service))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun consumeSearchIntent() {
        _searchIntents.resetReplayCache()
    }
}
