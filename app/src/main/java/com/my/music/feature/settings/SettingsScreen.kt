package com.my.music.feature.settings

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LocationOff
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.my.music.core.location.LocationPermissions
import com.my.music.core.ui.theme.AccentOption
import com.my.music.core.ui.theme.AccentOptions
import com.my.music.data.repository.ThemeMode
import kotlin.math.roundToInt

/**
 * Экран настроек: секция «Оформление» —
 * переключатель динамических цветов обложки и ручная палитра акцентов.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsState()
    val russianPriority by viewModel.russianPriority.collectAsState()

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasLocationPermission by remember {
        mutableStateOf(LocationPermissions.hasPermission(context))
    }
    var showCustomColorDialog by remember { mutableStateOf(false) }

    // Статус мог измениться в системных настройках — перечитываем при возврате
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasLocationPermission = LocationPermissions.hasPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            // Фон ставим сами: корневого Scaffold больше нет, без него экран прозрачный.
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .systemBarsPadding(),
    ) {
        SettingsHeader(onBack = onBack)

        Spacer(Modifier.height(8.dp))

        Text(
            text = "ГЕОПОЗИЦИЯ",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp,
            ),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 20.dp),
        )

        Spacer(Modifier.height(10.dp))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (hasLocationPermission) {
                            Icons.Rounded.LocationOn
                        } else {
                            Icons.Rounded.LocationOff
                        },
                        contentDescription = null,
                        tint = if (hasLocationPermission) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Геопозиция: ${
                                if (hasLocationPermission) "Разрешено" else "Запрещено"
                            }",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = if (hasLocationPermission) {
                                "Координаты сохраняются при лайке и питают «Воспоминания»"
                            } else {
                                "Без доступа координаты лайков не сохраняются, а «Воспоминания» " +
                                    "не привязываются к месту"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                FilledTonalButton(
                    onClick = {
                        hasLocationPermission = LocationPermissions.hasPermission(context)
                        if (!hasLocationPermission) {
                            LocationPermissions.openAppSettings(context)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text("Проверить разрешения")
                }

                if (!hasLocationPermission) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Если доступ запрещён, кнопка откроет настройки приложения",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text = "ОФОРМЛЕНИЕ",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp,
            ),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 20.dp),
        )

        Spacer(Modifier.height(10.dp))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column {
                // ─── Тема приложения ───
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                ) {
                    Text(
                        text = "Тема приложения",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = "Светлая, тёмная или как в системе",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(14.dp))
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        ThemeMode.entries.forEachIndexed { index, mode ->
                            SegmentedButton(
                                selected = settings.themeMode == mode,
                                onClick = { viewModel.setThemeMode(mode) },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = ThemeMode.entries.size,
                                ),
                            ) {
                                Text(
                                    text = when (mode) {
                                        ThemeMode.LIGHT -> "Светлая"
                                        ThemeMode.DARK -> "Тёмная"
                                        ThemeMode.SYSTEM -> "Система"
                                    },
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 18.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                )

                // ─── Динамические цвета обложки ───
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Динамические цвета обложки",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = "Фон и акценты плеера подстраиваются под обложку трека",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = settings.dynamicCoverColors,
                        onCheckedChange = viewModel::setDynamicCoverColors,
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 18.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                )

                // ─── Акцентный цвет ───
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                ) {
                    Text(
                        text = "Акцентный цвет",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = "Палитра и «Свой цвет» перекрывают системные цвета " +
                            "на всех версиях Android. Сброс — в диалоге «Свой цвет».",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    // Готовые кружки и «Свой цвет» пишут в одно поле customAccentColor:
                    // оно имеет высший приоритет в теме и работает поверх Dynamic Color.
                    val customArgb = settings.customAccentColor
                    val isPresetSelected = AccentOptions.any { it.color.toArgb() == customArgb }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AccentOptions.forEach { option ->
                            AccentDot(
                                option = option,
                                selected = customArgb == option.color.toArgb(),
                                onClick = { viewModel.setAccentColor(option.color) },
                            )
                        }
                        CustomAccentDot(
                            color = customArgb?.let { Color(it) },
                            selected = customArgb != null && !isPresetSelected,
                            onClick = { showCustomColorDialog = true },
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text = "МОЯ ВОЛНА",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp,
            ),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 20.dp),
        )

        Spacer(Modifier.height(10.dp))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Language,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Русская музыка в приоритете",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = "Волна чаще ставит треки с кириллицей в названии или имени артиста",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = russianPriority,
                    onCheckedChange = viewModel::setRussianPriority,
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }

    if (showCustomColorDialog) {
        CustomColorDialog(
            initial = settings.customAccentColor?.let { Color(it) },
            onDismiss = { showCustomColorDialog = false },
            onApply = { color ->
                viewModel.setCustomAccentColor(color)
                showCustomColorDialog = false
            },
            onReset = {
                viewModel.setCustomAccentColor(null)
                showCustomColorDialog = false
            },
        )
    }
}

@Composable
private fun SettingsHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledTonalIconButton(
            onClick = onBack,
            modifier = Modifier.size(44.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.ArrowBackIosNew,
                contentDescription = "Назад",
            )
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Настройки",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp,
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Внешний вид, волна и разрешения",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Кружок «Свой цвет»: открывает RGB-диалог. Показывает текущий собственный цвет
 * или значок палитры, если цвет ещё не выбран. Работает на всех версиях Android.
 */
@Composable
private fun CustomAccentDot(
    color: Color?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val dotSize by animateDpAsState(
        targetValue = if (selected) 40.dp else 34.dp,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "customDotSize",
    )

    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(dotSize)
                .clip(CircleShape)
                .background(
                    color ?: MaterialTheme.colorScheme.surfaceContainerHighest,
                )
                .border(
                    width = 2.dp,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                    },
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = "Свой цвет выбран",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            } else if (color == null) {
                Icon(
                    imageVector = Icons.Rounded.Palette,
                    contentDescription = "Свой цвет",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/**
 * Диалог выбора произвольного цвета: три RGB-слайдера + живой превью.
 * «Сбросить» убирает собственный цвет и возвращает тему к системной логике.
 */
@Composable
private fun CustomColorDialog(
    initial: Color?,
    onDismiss: () -> Unit,
    onApply: (Color) -> Unit,
    onReset: () -> Unit,
) {
    var red by remember { mutableFloatStateOf(initial?.red ?: 0.65f) }
    var green by remember { mutableFloatStateOf(initial?.green ?: 0.36f) }
    var blue by remember { mutableFloatStateOf(initial?.blue ?: 0.55f) }
    val preview = Color(red, green, blue)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Свой цвет") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(preview)
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(16.dp),
                            ),
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(
                        text = formatHexColor(preview),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(18.dp))
                RgbSlider(
                    label = "Красный",
                    value = red,
                    accent = Color(0xFFE5484D),
                    onValueChange = { red = it },
                )
                RgbSlider(
                    label = "Зелёный",
                    value = green,
                    accent = Color(0xFF43A047),
                    onValueChange = { green = it },
                )
                RgbSlider(
                    label = "Синий",
                    value = blue,
                    accent = Color(0xFF3F6BD8),
                    onValueChange = { blue = it },
                )
                if (initial != null) {
                    Spacer(Modifier.height(6.dp))
                    TextButton(
                        onClick = onReset,
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Text("Сбросить")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(preview) }) {
                Text("Применить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        },
    )
}

/** Один RGB-слайдер с подписью канала и текущим значением 0–255. */
@Composable
private fun RgbSlider(
    label: String,
    value: Float,
    accent: Color,
    onValueChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = (value * 255f).roundToInt().toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = accent,
                inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f),
            ),
        )
    }
}

/** ARGB-превью в HEX-виде, например #A85CD9. */
private fun formatHexColor(color: Color): String {
    val r = (color.red * 255f).roundToInt()
    val g = (color.green * 255f).roundToInt()
    val b = (color.blue * 255f).roundToInt()
    return "#%02X%02X%02X".format(r, g, b)
}

/**
 * Кружок акцентного цвета: выбранный раздувается и получает
 * контур с галочкой. Доступен на всех версиях Android.
 */
@Composable
private fun AccentDot(
    option: AccentOption,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val dotSize by animateDpAsState(
        targetValue = if (selected) 40.dp else 34.dp,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "accentDotSize",
    )

    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(dotSize)
                .clip(CircleShape)
                .background(option.color)
                .border(
                    width = 2.dp,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                    },
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = option.name,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
