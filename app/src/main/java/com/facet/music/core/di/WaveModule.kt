package com.facet.music.core.di

import com.facet.music.core.wave.CandidateSource
import com.facet.music.core.wave.Clock
import com.facet.music.core.wave.DefaultRandomProvider
import com.facet.music.core.wave.RandomProvider
import com.facet.music.core.wave.SystemClockProvider
import com.facet.music.core.wave.TasteProfileProvider
import com.facet.music.core.wave.WaveConfig
import com.facet.music.core.wave.WaveEngine
import com.facet.music.core.wave.WaveHistoryStore
import com.facet.music.core.wave.WaveNetworkGate
import com.facet.music.data.local.RoomTasteProfileProvider
import com.facet.music.data.local.RoomWaveHistoryStore
import com.facet.music.data.source.wave.FamiliarSource
import com.facet.music.data.source.wave.FollowedArtistsSource
import com.facet.music.data.source.wave.RelatedTracksSource
import com.facet.music.data.source.wave.SecondHopSource
import com.facet.music.data.source.wave.SimilarArtistsSource
import com.facet.music.data.source.wave.TagSource
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class WaveBindingModule {

    @Binds
    @Singleton
    abstract fun bindClock(impl: SystemClockProvider): Clock

    @Binds
    @Singleton
    abstract fun bindRandomProvider(impl: DefaultRandomProvider): RandomProvider

    @Binds
    @Singleton
    abstract fun bindTasteProfileProvider(impl: RoomTasteProfileProvider): TasteProfileProvider

    @Binds
    @Singleton
    abstract fun bindWaveHistoryStore(impl: RoomWaveHistoryStore): WaveHistoryStore

    @Binds
    @Singleton
    @IntoSet
    abstract fun bindRelatedSource(impl: RelatedTracksSource): CandidateSource

    @Binds
    @Singleton
    @IntoSet
    abstract fun bindSimilarSource(impl: SimilarArtistsSource): CandidateSource

    @Binds
    @Singleton
    @IntoSet
    abstract fun bindSecondHopSource(impl: SecondHopSource): CandidateSource

    @Binds
    @Singleton
    @IntoSet
    abstract fun bindTagSource(impl: TagSource): CandidateSource

    @Binds
    @Singleton
    @IntoSet
    abstract fun bindFollowedSource(impl: FollowedArtistsSource): CandidateSource

    @Binds
    @Singleton
    @IntoSet
    abstract fun bindFamiliarSource(impl: FamiliarSource): CandidateSource
}

@Module
@InstallIn(SingletonComponent::class)
object WaveProvidesModule {

    @Provides
    @Singleton
    fun waveConfig(): WaveConfig = WaveConfig()

    @Provides
    @Singleton
    fun waveNetworkGate(config: WaveConfig): WaveNetworkGate =
        WaveNetworkGate(config.maxConcurrentRequests)

    @Provides
    @Singleton
    fun waveEngine(
        sources: Set<@JvmSuppressWildcards CandidateSource>,
        tasteProfileProvider: TasteProfileProvider,
        historyStore: WaveHistoryStore,
        clock: Clock,
        randomProvider: RandomProvider,
        config: WaveConfig,
    ): WaveEngine = WaveEngine(
        sources = sources,
        tasteProfileProvider = tasteProfileProvider,
        historyStore = historyStore,
        clock = clock,
        randomProvider = randomProvider,
        config = config,
    )
}
