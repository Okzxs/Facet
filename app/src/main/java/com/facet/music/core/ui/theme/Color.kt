package com.facet.music.core.ui.theme

import androidx.compose.ui.graphics.Color

// Нейтральная палитра: строгие серые без цветового уклона.
// Акцент на Android 12+ приходит из Dynamic Color (обои), здесь — fallback
// в духе Google Blue для старых устройств.

val Primary = Color(0xFF0B57D0)
val OnPrimary = Color(0xFFFFFFFF)
val PrimaryContainer = Color(0xFFD3E3FD)
val OnPrimaryContainer = Color(0xFF041E49)

val Secondary = Color(0xFF00639B)
val OnSecondary = Color(0xFFFFFFFF)
val SecondaryContainer = Color(0xFFCFE5FF)
val OnSecondaryContainer = Color(0xFF001D35)

val Tertiary = Color(0xFF7C4DFF)
val OnTertiary = Color(0xFFFFFFFF)
val TertiaryContainer = Color(0xFFEADDFF)
val OnTertiaryContainer = Color(0xFF21005D)

val Error = Color(0xFFB3261E)
val OnError = Color(0xFFFFFFFF)
val ErrorContainer = Color(0xFFF9DEDC)
val OnErrorContainer = Color(0xFF410E0B)

// Светлые поверхности: чистый нейтральный серый без тёплых оттенков.
val Background = Color(0xFFFAFAFA)
val OnBackground = Color(0xFF1A1A1A)
val Surface = Color(0xFFFAFAFA)
val OnSurface = Color(0xFF1A1A1A)
val SurfaceVariant = Color(0xFFE6E6E6)
val OnSurfaceVariant = Color(0xFF5C5C5C)
val Outline = Color(0xFF8A8A8A)
val OutlineVariant = Color(0xFFDCDCDC)
val SurfaceDim = Color(0xFFEFEFEF)
val SurfaceBright = Color(0xFFFFFFFF)
val SurfaceContainerLowest = Color(0xFFFFFFFF)
val SurfaceContainerLow = Color(0xFFF2F2F2)
val SurfaceContainer = Color(0xFFEDEDED)
val SurfaceContainerHigh = Color(0xFFE8E8E8)
val SurfaceContainerHighest = Color(0xFFE2E2E2)
val InverseSurface = Color(0xFF303030)
val InverseOnSurface = Color(0xFFF1F1F1)
val InversePrimary = Color(0xFFA8C7FA)

// Тёмная палитра: канонический тёмно-серый Material 3, без коричневого.
val PrimaryDark = Color(0xFFA8C7FA)
val OnPrimaryDark = Color(0xFF062E6F)
val PrimaryContainerDark = Color(0xFF0842A0)
val OnPrimaryContainerDark = Color(0xFFD3E3FD)

val SecondaryDark = Color(0xFF9ECAFF)
val OnSecondaryDark = Color(0xFF003258)
val SecondaryContainerDark = Color(0xFF00497D)
val OnSecondaryContainerDark = Color(0xFFD1E4FF)

val TertiaryDark = Color(0xFFD0BCFF)
val OnTertiaryDark = Color(0xFF381E72)
val TertiaryContainerDark = Color(0xFF4F378B)
val OnTertiaryContainerDark = Color(0xFFEADDFF)

val ErrorDark = Color(0xFFF2B8B5)
val OnErrorDark = Color(0xFF601410)
val ErrorContainerDark = Color(0xFF8C1D18)
val OnErrorContainerDark = Color(0xFFF9DEDC)

val BackgroundDark = Color(0xFF121212)
val OnBackgroundDark = Color(0xFFE8E8E8)
val SurfaceDark = Color(0xFF121212)
val OnSurfaceDark = Color(0xFFE8E8E8)
val SurfaceVariantDark = Color(0xFF3A3A3A)
val OnSurfaceVariantDark = Color(0xFFA6A6A6)
val OutlineDark = Color(0xFF8C8C8C)
val OutlineVariantDark = Color(0xFF3A3A3A)
val SurfaceDimDark = Color(0xFF0E0E0E)
val SurfaceBrightDark = Color(0xFF373737)
val SurfaceContainerLowestDark = Color(0xFF0D0D0D)
val SurfaceContainerLowDark = Color(0xFF1A1A1A)
val SurfaceContainerDark = Color(0xFF1E1E1E)
val SurfaceContainerHighDark = Color(0xFF242424)
val SurfaceContainerHighestDark = Color(0xFF2A2A2A)
val InverseSurfaceDark = Color(0xFFE8E8E8)
val InverseOnSurfaceDark = Color(0xFF303030)
val InversePrimaryDark = Color(0xFF0B57D0)

// Семантические цвета статусов: зелёный не зависит от акцента и Dynamic Color.
val StatusOnlineLight = Color(0xFF1E8E3E)
val StatusOnlineDark = Color(0xFF81C995)
