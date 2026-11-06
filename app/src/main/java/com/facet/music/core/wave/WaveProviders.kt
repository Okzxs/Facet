package com.facet.music.core.wave

import javax.inject.Inject
import kotlin.random.Random

class SystemClockProvider @Inject constructor() : Clock {
    override fun nowMs(): Long = System.currentTimeMillis()
}

class DefaultRandomProvider @Inject constructor() : RandomProvider {
    override fun newSeed(): Long = Random.Default.nextLong()

    override fun create(seed: Long): Random = Random(seed)
}
