package com.my.music.navigation

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Высота плавающего нижнего дока (вместе с его внутренним navigationBarsPadding).
 *
 * Док вынесен из слота Scaffold и рисуется оверлеем поверх NavHost, поэтому
 * NavHost больше не меняет размер при показе/скрытии дока — именно это убирало
 * «развал» кадра при переходе в плеер и обратно. Экраны-вкладки читают это
 * значение и добавляют его в нижний contentPadding, чтобы контент не прятался
 * под плавающим доком.
 *
 * Используется compositionLocalOf (а не staticCompositionLocalOf): значение
 * меняется в рантайме (док подрастает, когда появляется строка мини-плеера),
 * и пересобрать нужно только читателей.
 */
val LocalBottomDockHeight = compositionLocalOf<Dp> { 0.dp }
