@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.facet.music.core.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

object Motion {
    val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    val Standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    const val ShortMillis = 180
    const val MediumMillis = 280
    const val EnterMillis = 460
    const val ExitMillis = 240

    const val PressDamping = 0.62f
    const val PressStiffness = 650f
    const val RevealDamping = 0.68f
    const val RevealStiffness = 340f
    const val BounceDamping = 0.55f
    const val BounceStiffness = 500f

    const val SpatialFastDamping = 0.66f
    const val SpatialFastStiffness = 620f
    const val SpatialDefaultDamping = 0.68f
    const val SpatialDefaultStiffness = 360f
    const val SpatialSlowDamping = 0.72f
    const val SpatialSlowStiffness = 220f

    const val EffectsFastDamping = 1.0f
    const val EffectsFastStiffness = 3800f
    const val EffectsDefaultDamping = 1.0f
    const val EffectsDefaultStiffness = 1600f
    const val EffectsSlowDamping = 1.0f
    const val EffectsSlowStiffness = 800f

    fun <T> press(): SpringSpec<T> =
        spring(dampingRatio = PressDamping, stiffness = PressStiffness)

    fun <T> reveal(): SpringSpec<T> =
        spring(dampingRatio = RevealDamping, stiffness = RevealStiffness)

    fun <T> bounce(): SpringSpec<T> =
        spring(dampingRatio = BounceDamping, stiffness = BounceStiffness)

    fun <T> spatialFast(): SpringSpec<T> =
        spring(dampingRatio = SpatialFastDamping, stiffness = SpatialFastStiffness)

    fun <T> spatialDefault(): SpringSpec<T> =
        spring(dampingRatio = SpatialDefaultDamping, stiffness = SpatialDefaultStiffness)

    fun <T> spatialSlow(): SpringSpec<T> =
        spring(dampingRatio = SpatialSlowDamping, stiffness = SpatialSlowStiffness)

    fun <T> effectsFast(): SpringSpec<T> =
        spring(dampingRatio = EffectsFastDamping, stiffness = EffectsFastStiffness)

    fun <T> effectsDefault(): SpringSpec<T> =
        spring(dampingRatio = EffectsDefaultDamping, stiffness = EffectsDefaultStiffness)

    fun <T> effectsSlow(): SpringSpec<T> =
        spring(dampingRatio = EffectsSlowDamping, stiffness = EffectsSlowStiffness)
}

@Composable
fun <T> spatialDefaultSpec(): FiniteAnimationSpec<T> =
    MaterialTheme.motionScheme.defaultSpatialSpec()

@Composable
fun <T> spatialFastSpec(): FiniteAnimationSpec<T> =
    MaterialTheme.motionScheme.fastSpatialSpec()

@Composable
fun <T> effectsDefaultSpec(): FiniteAnimationSpec<T> =
    MaterialTheme.motionScheme.defaultEffectsSpec()

@Composable
fun <T> effectsFastSpec(): FiniteAnimationSpec<T> =
    MaterialTheme.motionScheme.fastEffectsSpec()
