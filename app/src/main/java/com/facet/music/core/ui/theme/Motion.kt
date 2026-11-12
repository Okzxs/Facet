@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.facet.music.core.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/**
 * Токены движения Material 3 Expressive.
 *
 * Пространственные анимации (смещение, масштаб, форма) идут пружинами spatial,
 * эффекты (цвет, альфа) — пружинами effects или короткими tween. Кривые
 * Emphasized заменяют FastOutSlowInEasing во всех переходах приложения.
 *
 * Пружины доступны и вне @Composable (жесты, состояние свайпа) через [Motion],
 * и внутри композиции через [spatialDefault]/[effectsDefault] — последние
 * читают активную [MaterialTheme.motionScheme].
 */
object Motion {
    val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    val Standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    const val ShortMillis = 150
    const val MediumMillis = 200
    const val EnterMillis = 450
    const val ExitMillis = 200

    const val SpatialFastDamping = 0.6f
    const val SpatialFastStiffness = 800f
    const val SpatialDefaultDamping = 0.8f
    const val SpatialDefaultStiffness = 380f
    const val SpatialSlowDamping = 0.8f
    const val SpatialSlowStiffness = 200f

    const val EffectsFastDamping = 1.0f
    const val EffectsFastStiffness = 3800f
    const val EffectsDefaultDamping = 1.0f
    const val EffectsDefaultStiffness = 1600f
    const val EffectsSlowDamping = 1.0f
    const val EffectsSlowStiffness = 800f

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