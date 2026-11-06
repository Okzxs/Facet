package com.facet.music.core.wave

import com.facet.music.core.text.MusicNormalizer
import com.facet.music.data.model.Source
import com.facet.music.data.model.Track
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Ядро «Моей волны»: собирает кандидатов из источников, режет недопустимое,
 * считает оценку, переранжирует MMR и отдаёт пачку. Никаких Android-зависимостей.
 */
class WaveEngine(
    private val sources: Set<CandidateSource>,
    private val tasteProfileProvider: TasteProfileProvider,
    private val historyStore: WaveHistoryStore,
    private val clock: Clock,
    private val randomProvider: RandomProvider,
    private val config: WaveConfig = WaveConfig(),
) {

    suspend fun startSession(
        isRadio: Boolean = false,
        radioSeed: Track? = null,
    ): WaveSessionPlan {
        val now = clock.nowMs()
        val random = randomProvider.create(randomProvider.newSeed())
        val profile = tasteProfileProvider.profile()
        val history = historyStore.loadHistory(now)
        val previous = historyStore.previousSessionSeedKeys(config.previousSessionsToPenalize)
        val previousArtists = historyStore.previousSessionSeedArtistKeys(
            config.previousSessionsToPenalize,
        )
        val seedRows = historyStore.seedTracks(now - config.seedWindowDays * DAY_MS)

        val seedTracks = if (isRadio && radioSeed != null) {
            listOf(
                TrackSeed(
                    track = radioSeed,
                    weight = 1.0,
                    trackKey = keyOf(radioSeed),
                    artistKey = artistKeyOf(radioSeed),
                ),
            )
        } else {
            buildSeedTracks(seedRows, previous, now, random)
        }

        val seedArtists = if (isRadio && radioSeed != null) {
            radioSeed.artist.trim().takeIf { it.isNotBlank() }?.let {
                listOf(SeedArtist(MusicNormalizer.normalizeArtist(it), it, 1.0))
            }.orEmpty()
        } else {
            softmaxArtists(profile, random)
        }

        val seedGenres = if (isRadio && radioSeed != null) {
            radioSeed.genre?.trim()?.takeIf { it.isNotBlank() }?.let(::listOf).orEmpty()
        } else {
            weightedGenres(profile, random)
        }

        return WaveSessionPlan(
            sessionId = "wave-${now}-${random.nextInt(100_000, 999_999)}",
            createdAt = now,
            randomSeed = random.nextLong(),
            profile = profile,
            history = history,
            seedArtists = seedArtists.ifEmpty {
                seedTracks.map { SeedArtist(it.artistKey, it.track.artist, it.weight) }
                    .distinctBy { it.key }
                    .take(config.similarSeedMax)
            },
            seedGenres = seedGenres,
            seedTracks = seedTracks,
            previousSeedKeys = previous,
            previousSeedArtistKeys = previousArtists,
            isRadio = isRadio,
            radioSeed = radioSeed,
        )
    }

    suspend fun nextBatch(
        plan: WaveSessionPlan,
        state: WaveSessionState,
        size: Int = config.startBatchSize,
    ): WaveGeneration {
        val now = clock.nowMs()
        val target = size.coerceIn(1, config.maxBatchSize)
        val freshPlan = plan.copy(history = historyStore.loadHistory(now))
        val random = randomProvider.create(plan.randomSeed + state.batchNumber * BATCH_SEED_STEP)
        var candidates = loadCandidates(freshPlan, random)
        var selected = select(candidates, target, freshPlan, state, now, random)

        if (selected.size < target) {
            val retryRandom = randomProvider.create(
                plan.randomSeed + state.batchNumber * BATCH_SEED_STEP + RETRY_SEED_STEP,
            )
            candidates = candidates + loadCandidates(freshPlan, retryRandom)
            selected = select(candidates, target, freshPlan, state, now, retryRandom)
        }

        if (selected.isNotEmpty()) {
            historyStore.recordShown(plan.sessionId, selected.map { it.candidate.candidate })
        }

        val origins = selected.groupingBy { it.candidate.candidate.origin }.eachCount()
        val nextState = state.copy(
            recentArtistKeys = (state.recentArtistKeys + selected.map { it.candidate.artistKey })
                .takeLast(RECENT_KEYS_KEPT),
            recentSeedKeys = (state.recentSeedKeys + selected.mapNotNull { it.candidate.candidate.seedKey })
                .takeLast(maxOf(config.seedRunMax, RECENT_KEYS_KEPT)),
            batchNumber = state.batchNumber + 1,
            shownTrackKeys = state.shownTrackKeys + selected.map { it.candidate.trackKey },
        )
        return WaveGeneration(
            tracks = selected.map { it.candidate.candidate.track },
            origins = origins,
            state = nextState,
            requestedSize = target,
        )
    }

    private fun select(
        candidates: List<Candidate>,
        target: Int,
        plan: WaveSessionPlan,
        state: WaveSessionState,
        now: Long,
        random: Random,
    ): List<ScoredCandidate> {
        val eligible = filterCandidates(candidates, plan, state, now)
        val scored = eligible.map { score(it, plan, state, now, random) }
        return assemble(scored, target, plan, state, random)
    }

    private suspend fun loadCandidates(
        plan: WaveSessionPlan,
        sessionRandom: Random,
    ): List<Candidate> = supervisorScope {
        val seeds = sources.associateWith { source ->
            plan.randomSeed xor source.javaClass.name.hashCode().toLong() xor sessionRandom.nextLong()
        }
        sources.map { source ->
            async {
                val seed = seeds.getValue(source)
                try {
                    withTimeoutOrNull(config.candidateTimeoutMs) {
                        source.candidates(plan, seed)
                    }.orEmpty()
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    emptyList()
                }
            }
        }.awaitAll().flatten()
    }

    private fun filterCandidates(
        candidates: List<Candidate>,
        plan: WaveSessionPlan,
        state: WaveSessionState,
        now: Long,
    ): List<EligibleCandidate> {
        val history = plan.history
        val impressions = history.impressions
        val shownRecent = impressions
            .filter { it.shownAt >= now - config.impressionBlockDays * DAY_MS }
            .associate { it.trackKey to it.shownAt }
        val shownFamiliar = impressions
            .filter { it.shownAt >= now - config.familiarMinUnshownDays * DAY_MS }
            .associate { it.trackKey to it.shownAt }
        val playedByKey = history.plays.associate { keyOf(it.track) to it.atMs }
        val artistShows = impressions
            .filter { it.shownAt >= now - config.penaltyWindowDays * DAY_MS }
            .groupingBy { it.artistKey }
            .eachCount()
        val likedMarkers = plan.profile.likedMarkers
        val profileGenres = plan.profile.genres.keys.map { it to MusicNormalizer.genreTokens(it) }

        val deduped = LinkedHashMap<String, Candidate>()
        for (candidate in candidates) {
            val track = candidate.track
            if (track.id.isBlank()) continue
            val key = keyOf(track)
            if (plan.radioSeed?.let { keyOf(it) == key } == true) continue
            val existing = deduped[key]
            if (existing == null || prefer(candidate, existing)) deduped[key] = candidate
        }

        return deduped.values.mapNotNull { candidate ->
            val track = candidate.track
            val key = keyOf(track)
            val artistKey = artistKeyOf(track)
            if (key in history.dislikedTrackKeys || track.id in history.dislikedTrackIds) {
                return@mapNotNull null
            }
            if (artistKey in history.blockedArtistKeys) return@mapNotNull null
            if (track.id in history.queueTrackIds || key in history.queueTrackKeys) {
                return@mapNotNull null
            }
            if (track.isSubscriptionOnly) return@mapNotNull null
            if (track.durationMs > 0 &&
                (track.durationMs < config.minDurationMs || track.durationMs > config.maxDurationMs)
            ) {
                return@mapNotNull null
            }
            if (MusicNormalizer.markersIn(track.title).any { it !in likedMarkers }) {
                return@mapNotNull null
            }

            val lastShown = shownRecent[key]
            val lastPlayed = playedByKey[key]
            if (candidate.origin == CandidateOrigin.FAMILIAR) {
                if (key in state.shownTrackKeys) return@mapNotNull null
                if (lastShown != null &&
                    now - lastShown < config.familiarMinUnshownDays * DAY_MS
                ) {
                    return@mapNotNull null
                }
                if (lastPlayed != null &&
                    now - lastPlayed < config.familiarMinUnplayedDays * DAY_MS
                ) {
                    return@mapNotNull null
                }
                if (key in shownFamiliar &&
                    now - shownFamiliar.getValue(key) < config.familiarMinUnshownDays * DAY_MS
                ) {
                    return@mapNotNull null
                }
            } else {
                if (key in state.shownTrackKeys) return@mapNotNull null
                if (lastShown != null &&
                    now - lastShown < config.impressionBlockDays * DAY_MS
                ) {
                    return@mapNotNull null
                }
                if (lastPlayed != null &&
                    now - lastPlayed < config.playedBlockDays * DAY_MS
                ) {
                    return@mapNotNull null
                }
            }

            val genreKey = genreKeyOf(track)
            val genreAffinity = genreAffinity(genreKey, plan.profile, profileGenres)
            EligibleCandidate(
                candidate = candidate,
                trackKey = key,
                artistKey = artistKey,
                genreKey = genreKey,
                artistAffinity = plan.profile.artists[artistKey] ?: 0.0,
                genreAffinity = genreAffinity,
                artistShows = artistShows[artistKey] ?: 0,
            )
        }
    }

    private fun score(
        candidate: EligibleCandidate,
        plan: WaveSessionPlan,
        state: WaveSessionState,
        now: Long,
        random: Random,
    ): ScoredCandidate {
        val track = candidate.candidate.track
        val playedBefore = plan.history.plays.any { keyOf(it.track) == candidate.trackKey }
        val artistKnown = plan.profile.artists.containsKey(candidate.artistKey) ||
            plan.history.plays.any { artistKeyOf(it.track) == candidate.artistKey }
        val trackSeedWeights = plan.seedTracks.associate { it.trackKey to it.weight }
        val artistSeedWeights = plan.seedArtists.associate { it.key to it.weight }
        val maxSeed = (trackSeedWeights.values + artistSeedWeights.values)
            .maxOrNull()
            ?.coerceAtLeast(0.0001) ?: 1.0
        val rawSeed = candidate.candidate.seedKey
            ?.let { trackSeedWeights[it] ?: artistSeedWeights[it] }
            ?: 0.0
        val seedWeight = (rawSeed / maxSeed).coerceIn(0.0, 1.0)
        val popularity = if (track.playCount > 0) {
            (log10(1.0 + track.playCount) / 6.0).coerceIn(0.0, 1.0)
        } else {
            0.0
        }
        val penalty = (candidate.artistShows * config.penaltyPerImpression)
            .coerceAtMost(config.maxArtistPenalty)
        val u = random.nextDouble().coerceIn(1e-9, 1.0 - 1e-9)
        val noise = -config.gumbelSigma * ln(-ln(u))
        val novelty = if (playedBefore) 0.0 else 1.0
        val explorationNovelty = novelty + if (!artistKnown) config.newArtistBonus else 0.0

        fun score(exploration: Boolean): Double =
            config.weightArtistAffinity * candidate.artistAffinity +
                config.weightGenreAffinity * candidate.genreAffinity +
                config.weightOriginPrior * candidate.candidate.origin.prior +
                config.weightNovelty * if (exploration) explorationNovelty else novelty +
                config.weightSeed * seedWeight +
                config.weightPopularity * popularity -
                penalty + noise

        return ScoredCandidate(
            candidate = candidate,
            isNewArtist = !artistKnown,
            baseScore = score(exploration = false),
            explorationScore = score(exploration = true),
        )
    }

    private fun assemble(
        scored: List<ScoredCandidate>,
        size: Int,
        plan: WaveSessionPlan,
        state: WaveSessionState,
        random: Random,
    ): List<ScoredCandidate> {
        if (scored.isEmpty() || size <= 0) return emptyList()

        val familiar = scored.filter { it.candidate.candidate.origin == CandidateOrigin.FAMILIAR }
        val core = scored.filter { it.candidate.candidate.origin != CandidateOrigin.FAMILIAR }
        val exploration = core
            .filter { it.candidate.candidate.origin.exploration }
            .ifEmpty { core.filter { it.isNewArtist } }
        if (core.isEmpty() && familiar.isEmpty()) return emptyList()

        val familiarTarget = floor(size * config.familiarShare).toInt()
            .coerceAtMost(familiar.size)
        val explorationTarget = (size * config.explorationShare).roundToInt()
            .coerceIn(
                ceil(size * config.explorationMinShare).toInt(),
                floor(size * config.explorationMaxShare).toInt(),
            )
            .coerceAtMost(maxOf(0, size - 1))
        val slots = buildSlots(size, familiarTarget, explorationTarget, random)

        val selected = mutableListOf<ScoredCandidate>()
        val remainingCore = core.toMutableList()
        val remainingExploration = exploration.toMutableList()
        val remainingFamiliar = familiar.toMutableList()

        for ((index, slot) in slots.withIndex()) {
            val first = index == 0 && state.batchNumber == 0
            val pool = when (slot) {
                SlotType.CORE -> remainingCore
                SlotType.EXPLORE -> remainingExploration
                SlotType.FAMILIAR -> remainingFamiliar
            }
            val explorationSlot = slot == SlotType.EXPLORE
            val pick = pick(
                pool = pool,
                selected = selected,
                size = size,
                plan = plan,
                state = state,
                explorationSlot = explorationSlot,
                firstOfSession = first,
            ) ?: if (slot != SlotType.CORE) {
                pick(
                    pool = remainingCore,
                    selected = selected,
                    size = size,
                    plan = plan,
                    state = state,
                    explorationSlot = false,
                    firstOfSession = false,
                )
            } else {
                null
            } ?: break

            selected += pick
            val pickedCandidate = pick.candidate.candidate
            remainingCore.removeAll { it.candidate.candidate.track.id == pickedCandidate.track.id }
            remainingExploration.removeAll {
                it.candidate.candidate.track.id == pickedCandidate.track.id
            }
            remainingFamiliar.removeAll {
                it.candidate.candidate.track.id == pickedCandidate.track.id
            }
        }

        return selected
    }

    private fun pick(
        pool: List<ScoredCandidate>,
        selected: List<ScoredCandidate>,
        size: Int,
        plan: WaveSessionPlan,
        state: WaveSessionState,
        explorationSlot: Boolean,
        firstOfSession: Boolean,
    ): ScoredCandidate? {
        val artistLimit = if (size >= 20) config.artistMaxPerTwenty else 1
        val allowed = pool.asSequence()
            .filter { candidate -> allowedByLimits(candidate, selected, state, artistLimit) }
            .toList()
        if (allowed.isEmpty()) return null

        val eligible = if (firstOfSession) {
            val related = allowed.filter {
                it.candidate.candidate.origin == CandidateOrigin.RELATED
            }
            val confident = related.filter { it.candidate.artistAffinity > 0.0 }
            confident.ifEmpty { related }.ifEmpty { allowed }
        } else {
            allowed
        }

        if (firstOfSession) {
            return eligible.maxByOrNull { candidate ->
                candidate.baseScore + candidate.candidate.artistAffinity * FIRST_TRACK_AFFINITY_BONUS
            }
        }

        return eligible.maxByOrNull { candidate ->
            val score = if (explorationSlot) candidate.explorationScore else candidate.baseScore
            val similarity = maxSimilarity(candidate, selected)
            config.mmrLambda * score - (1.0 - config.mmrLambda) * similarity
        }
    }

    private fun allowedByLimits(
        candidate: ScoredCandidate,
        selected: List<ScoredCandidate>,
        state: WaveSessionState,
        artistLimit: Int,
    ): Boolean {
        val artistKey = candidate.candidate.artistKey
        val batchCount = selected.count { it.candidate.artistKey == artistKey }
        val rollingWindow = (state.recentArtistKeys.takeLast(ROLLING_TWENTY - 1) +
            selected.map { it.candidate.artistKey })
            .takeLast(ROLLING_TWENTY - 1)
        val rollingCount = rollingWindow.count { it == artistKey }
        if (batchCount >= artistLimit || rollingCount >= artistLimit) return false
        val window = (state.recentArtistKeys.takeLast(config.artistWindow - 1) +
            selected.map { it.candidate.artistKey })
            .takeLast(config.artistWindow - 1)
        if (window.any { it == artistKey }) return false

        val seedKey = candidate.candidate.candidate.seedKey
        if (seedKey != null) {
            val recentSeeds = (state.recentSeedKeys.takeLast(config.seedRunMax - 1) +
                selected.mapNotNull { it.candidate.candidate.seedKey })
            if (recentSeeds.size >= config.seedRunMax &&
                recentSeeds.takeLast(config.seedRunMax).all { it == seedKey }
            ) {
                return false
            }
        }
        return true
    }

    private fun maxSimilarity(
        candidate: ScoredCandidate,
        selected: List<ScoredCandidate>,
    ): Double = selected.maxOfOrNull { other ->
        when {
            other.candidate.artistKey == candidate.candidate.artistKey -> 1.0
            candidate.candidate.candidate.seedKey != null &&
                candidate.candidate.candidate.seedKey == other.candidate.candidate.seedKey -> 0.5
            candidate.candidate.genreKey != null &&
                candidate.candidate.genreKey == other.candidate.genreKey -> 0.3
            else -> 0.0
        }
    } ?: 0.0

    private fun buildSlots(
        size: Int,
        familiarTarget: Int,
        explorationTarget: Int,
        random: Random,
    ): List<SlotType> {
        val slots = MutableList(size) { SlotType.CORE }
        val free = (1 until size).toMutableList()
        repeat(familiarTarget.coerceAtMost(free.size)) {
            val positions = free.filter { position ->
                slots[position - 1] != SlotType.FAMILIAR &&
                    (position + 1 >= size || slots[position + 1] != SlotType.FAMILIAR)
            }
            if (positions.isEmpty()) return@repeat
            val position = positions[random.nextInt(positions.size)]
            slots[position] = SlotType.FAMILIAR
            free.remove(position)
        }
        repeat(explorationTarget.coerceAtMost(free.size)) {
            if (free.isEmpty()) return@repeat
            val position = free[random.nextInt(free.size)]
            slots[position] = SlotType.EXPLORE
            free.remove(position)
        }
        return slots
    }

    private fun buildSeedTracks(
        rows: List<PlayedTrack>,
        previous: Set<String>,
        now: Long,
        random: Random,
    ): List<TrackSeed> {
        val weighted = rows.mapNotNull { row ->
            val track = row.track
            val key = keyOf(track)
            if (key.isBlank()) return@mapNotNull null
            val ageDays = ((now - row.atMs).coerceAtLeast(0L) / DAY_MS.toDouble())
            val freshness = 0.5.pow(ageDays / config.seedHalfLifeDays)
            val previousFactor = if (key in previous) {
                config.previousSessionSeedPenalty
            } else {
                1.0
            }
            TrackSeed(
                track = track,
                weight = freshness * previousFactor,
                trackKey = key,
                artistKey = artistKeyOf(track),
            )
        }.groupBy { it.trackKey }
            .map { (_, group) -> group.maxBy { it.weight } }

        if (weighted.isEmpty()) return emptyList()
        val count = random.nextInt(config.relatedSeedMin, config.relatedSeedMax + 1)
            .coerceAtMost(weighted.size)
        return weightedSample(weighted, count, random) { it.weight }
    }

    private fun softmaxArtists(
        profile: WaveTasteProfile,
        random: Random,
    ): List<SeedArtist> {
        val entries = profile.artists.entries
            .filter { it.value > 0.0 && it.key.isNotBlank() }
            .sortedByDescending { it.value }
        if (entries.isEmpty()) return emptyList()
        val count = random.nextInt(config.similarSeedMin, config.similarSeedMax + 1)
            .coerceAtMost(entries.size)
        val remaining = entries.toMutableList()
        val result = mutableListOf<SeedArtist>()
        repeat(count) {
            if (remaining.isEmpty()) return@repeat
            val max = remaining.maxOf { it.value }
            val weights = remaining.map { exp((it.value - max) / config.softmaxTemperature) }
            val total = weights.sum()
            var point = random.nextDouble() * total
            var index = 0
            while (index < remaining.lastIndex && point > weights[index]) {
                point -= weights[index]
                index++
            }
            val chosen = remaining.removeAt(index)
            result += SeedArtist(chosen.key, chosen.key, chosen.value)
        }
        return result
    }

    private fun weightedGenres(
        profile: WaveTasteProfile,
        random: Random,
    ): List<String> {
        val entries = profile.genres.entries.filter { it.value > 0.0 }
        if (entries.isEmpty()) return emptyList()
        val count = random.nextInt(config.tagSeedMin, config.tagSeedMax + 1)
            .coerceAtMost(entries.size)
        return weightedSample(entries, count, random) { it.value }.map { it.key }
    }

    private fun <T> weightedSample(
        values: List<T>,
        count: Int,
        random: Random,
        weight: (T) -> Double,
    ): List<T> {
        val remaining = values.toMutableList()
        val result = mutableListOf<T>()
        repeat(count.coerceAtMost(remaining.size)) {
            val total = remaining.sumOf { weight(it).coerceAtLeast(0.0) }
            val index = if (total <= 0.0) {
                random.nextInt(remaining.size)
            } else {
                var point = random.nextDouble() * total
                var chosen = 0
                for (i in remaining.indices) {
                    point -= weight(remaining[i]).coerceAtLeast(0.0)
                    if (point <= 0.0) {
                        chosen = i
                        break
                    }
                }
                chosen
            }
            result += remaining.removeAt(index)
        }
        return result
    }

    private fun genreAffinity(
        genreKey: String?,
        profile: WaveTasteProfile,
        profileGenres: List<Pair<String, Set<String>>>,
    ): Double {
        if (genreKey.isNullOrBlank()) return 0.0
        profile.genres[genreKey]?.let { return it }
        val tokens = MusicNormalizer.genreTokens(genreKey)
        if (tokens.isEmpty()) return 0.0
        return profileGenres
            .filter { (_, other) -> other.isNotEmpty() && tokens.any { it in other } }
            .maxOfOrNull { (key, _) -> profile.genres[key] ?: 0.0 }
            ?: 0.0
    }

    private fun prefer(candidate: Candidate, existing: Candidate): Boolean {
        val candidateFull = candidate.track.source == Source.YOUTUBE ||
            candidate.track.source == Source.SOUNDCLOUD
        val existingFull = existing.track.source == Source.YOUTUBE ||
            existing.track.source == Source.SOUNDCLOUD
        if (candidateFull != existingFull) return candidateFull
        if (candidate.origin.prior != existing.origin.prior) {
            return candidate.origin.prior > existing.origin.prior
        }
        return candidate.sourceRank < existing.sourceRank
    }

    private fun keyOf(track: Track): String =
        MusicNormalizer.trackKey(track.artist, track.title).ifBlank { "id:${track.id}" }

    private fun artistKeyOf(track: Track): String =
        MusicNormalizer.normalizeArtist(track.artist).ifBlank { "track:${track.id}" }

    private fun genreKeyOf(track: Track): String? =
        MusicNormalizer.normalizeWords(track.genre.orEmpty()).takeIf { it.isNotBlank() }

    private fun Double.pow(exponent: Double): Double = Math.pow(this, exponent)

    private data class EligibleCandidate(
        val candidate: Candidate,
        val trackKey: String,
        val artistKey: String,
        val genreKey: String?,
        val artistAffinity: Double,
        val genreAffinity: Double,
        val artistShows: Int,
    )

    private data class ScoredCandidate(
        val candidate: EligibleCandidate,
        val isNewArtist: Boolean,
        val baseScore: Double,
        val explorationScore: Double,
    )

    private enum class SlotType { CORE, EXPLORE, FAMILIAR }

    private companion object {
        const val DAY_MS = 86_400_000L
        const val BATCH_SEED_STEP = 7_919L
        const val RETRY_SEED_STEP = 104_729L
        const val RECENT_KEYS_KEPT = 40
        const val ROLLING_TWENTY = 20
        const val FIRST_TRACK_AFFINITY_BONUS = 0.25
    }
}
