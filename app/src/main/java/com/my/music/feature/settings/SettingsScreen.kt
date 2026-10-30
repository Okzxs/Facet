package com.my.music.feature.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.ContextWrapper
import android.net.Uri
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LocationOff
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.my.music.BuildConfig
import com.my.music.R
import com.my.music.core.i18n.AppLocale
import com.my.music.core.location.LocationPermissions
import com.my.music.core.ui.theme.AccentOption
import com.my.music.core.ui.theme.AccentOptions
import com.my.music.core.spotify.SpotifyAuthState
import com.my.music.core.ui.components.TrackArtwork
import com.my.music.data.repository.AppSettings
import com.my.music.data.repository.ThemeMode
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Находит Activity в цепочке ContextWrapper: `LocalContext` может быть
 * обёрнут темой или конфигурацией локали, и прямой cast тогда не сработает.
 */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

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
    val spotifyAuth by viewModel.spotifyAuth.collectAsState()
    val spotifyAuthUrl by viewModel.spotifyAuthUrl.collectAsState()
    val spotifyAuthError by viewModel.spotifyAuthError.collectAsState()

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasLocationPermission by remember {
        mutableStateOf(LocationPermissions.hasPermission(context))
    }
    var showCustomColorDialog by remember { mutableStateOf(false) }
    var showSpotifyGuide by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Ссылка согласия открывается в браузере: WebView внутри приложения
    // Spotify не любит, а системный браузер уже авторизован у пользователя.
    LaunchedEffect(spotifyAuthUrl) {
        val url = spotifyAuthUrl ?: return@LaunchedEffect
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
        viewModel.consumeSpotifyAuthUrl()
    }

    val spotifyErrorText = stringResource(R.string.spotify_sign_in_failed)
    LaunchedEffect(spotifyAuthError) {
        if (!spotifyAuthError) return@LaunchedEffect
        snackbarHostState.showSnackbar(
            message = spotifyErrorText,
            duration = SnackbarDuration.Long,
        )
        viewModel.consumeSpotifyAuthError()
    }

    // Смена языка уже пересоздаёт Activity через MainActivity, но если по какой-то
    // причине этого не случилось — пользователь может перезапустить экран вручную.
    val languageChangedMessage = stringResource(R.string.settings_language_changed)
    val restartLabel = stringResource(R.string.settings_language_restart)
    val onLanguageSelect: (String) -> Unit = { language ->
        viewModel.setAppLanguage(language)
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = languageChangedMessage,
                actionLabel = restartLabel,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) {
                context.findActivity()?.recreate()
            }
        }
    }

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

    Box(modifier = Modifier.fillMaxSize()) {
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
            text = stringResource(R.string.settings_appearance),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        Spacer(Modifier.height(10.dp))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column {
                // ─── Тема приложения ───
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 18.dp),
                ) {
                    Text(
                        text = stringResource(R.string.settings_theme),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = stringResource(R.string.settings_theme_hint),
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
                                        ThemeMode.LIGHT -> stringResource(R.string.settings_theme_light)
                                        ThemeMode.DARK -> stringResource(R.string.settings_theme_dark)
                                        ThemeMode.SYSTEM -> stringResource(R.string.settings_theme_system)
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
                        .padding(horizontal = 16.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_dynamic_cover),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = stringResource(R.string.settings_dynamic_cover_hint),
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
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                )

                // ─── Акцентный цвет: включение ───
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_accent),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = if (settings.accentEnabled) {
                                stringResource(R.string.settings_accent_on)
                            } else {
                                stringResource(R.string.settings_accent_off)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = settings.accentEnabled,
                        onCheckedChange = viewModel::setAccentEnabled,
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                )

                // ─── Палитра акцента ───
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(if (settings.accentEnabled) 1f else 0.38f),
                ) {
                    Spacer(Modifier.height(16.dp))
                    // Готовые кружки и «Свой цвет» пишут в одно поле customAccentColor:
                    // оно имеет высший приоритет в теме и работает поверх Dynamic Color.
                    // Список скроллится горизонтально: на узких экранах кружки сохраняют
                    // размер вместо сплющивания, а крайние Spacer держат отступ от границ.
                    val customArgb = settings.customAccentColor
                    val isPresetSelected = AccentOptions.any { it.color.toArgb() == customArgb }
                    val recents = settings.recentAccentColors
                    if (recents.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.settings_recent),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                recents.forEach { argb ->
                                    RecentColorDot(
                                        color = Color(argb),
                                        selected = customArgb == argb,
                                        onClick = { viewModel.setAccentColor(Color(argb)) },
                                    )
                                }
                            }
                            Spacer(Modifier.height(14.dp))
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Spacer(Modifier.width(8.dp))
                        AccentOptions.forEach { option ->
                            AccentDot(
                                option = option,
                                selected = customArgb == option.color.toArgb(),
                                onClick = { viewModel.setAccentColor(option.color) },
                            )
                        }
                        CustomColorDot(
                            color = customArgb?.let { Color(it) },
                            selected = customArgb != null && !isPresetSelected,
                            onClick = { showCustomColorDialog = true },
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }

        Spacer(Modifier.height(40.dp))

        // ─── Язык интерфейса ─────────────────────────────────────────────
        Text(
            text = stringResource(R.string.settings_language_section),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(10.dp))
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.settings_language),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = stringResource(R.string.settings_language_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(14.dp))
                SegmentedButtonRow(
                    selected = settings.appLanguage,
                    onSelect = onLanguageSelect,
                )
            }
        }

        Spacer(Modifier.height(40.dp))

        // ─── Интеграции: Spotify ─────────────────────────────────────────
        Text(
            text = stringResource(R.string.settings_integrations),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(10.dp))
        SpotifyIntegrationCard(
            settings = settings,
            auth = spotifyAuth,
            onSave = viewModel::saveSpotifyCredentials,
            onSignIn = viewModel::signInSpotify,
            onSignOut = viewModel::signOutSpotify,
            onOpenGuide = { showSpotifyGuide = true },
        )

        Spacer(Modifier.height(40.dp))

        Text(
            text = stringResource(R.string.settings_wave),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        Spacer(Modifier.height(10.dp))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 18.dp),
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
                        text = stringResource(R.string.settings_russian_first),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = stringResource(R.string.settings_russian_first_hint),
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

        Spacer(Modifier.height(32.dp))

        Text(
            text = stringResource(R.string.settings_permissions),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        Spacer(Modifier.height(10.dp))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp)) {
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
                            text = if (hasLocationPermission) {
                                stringResource(R.string.settings_location_allowed)
                            } else {
                                stringResource(R.string.settings_location_denied)
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = if (hasLocationPermission) {
                                stringResource(R.string.settings_location_allowed_hint)
                            } else {
                                stringResource(R.string.settings_location_denied_hint)
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
                    modifier = Modifier.height(48.dp),
                    shape = CircleShape,
                ) {
                    Text(stringResource(R.string.settings_check_permissions))
                }

                if (!hasLocationPermission) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.settings_location_footer),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.height(32.dp))

        // Футер с версией сборки: читается как подпись экрана, а не как элемент списка.
        Text(
            text = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
        )
    }

        // Снекбар смены языка живёт над прокручиваемым содержимым, но внутри
        // того же Box, поэтому виден на любой позиции скролла.
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .systemBarsPadding(),
        )
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

    if (showSpotifyGuide) {
        SpotifyGuideDialog(onDismiss = { showSpotifyGuide = false })
    }
}

@Composable
private fun SettingsHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledTonalIconButton(
            onClick = onBack,
            modifier = Modifier.size(44.dp),
            shape = MaterialTheme.shapes.large,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.settings_back),
            )
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.settings_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Кружок «Свой цвет» в конце списка акцентов: радужный RGB-градиент с иконкой
 * палитры, а после применения собственного цвета — заливка этим цветом с галочкой.
 * Размер и зона нажатия как у готовых кружков; открывает RGB-диалог со слайдерами.
 */
@Composable
private fun CustomColorDot(
    color: Color?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val dotSize by animateDpAsState(
        targetValue = if (selected) 40.dp else 34.dp,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "customColorDotSize",
    )
    // Замкнутый радужный контур: первый и последний цвет совпадают, чтобы стык не мерцал.
    val rainbowBrush = remember {
        Brush.sweepGradient(
            listOf(
                Color(0xFFE5484D),
                Color(0xFFF5A524),
                Color(0xFF43A047),
                Color(0xFF00A2A9),
                Color(0xFF3F6BD8),
                Color(0xFF8B5CF6),
                Color(0xFFE5484D),
            ),
        )
    }

    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            // Штатная рябь вместо indication = null: круг должен читаться как кнопка.
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(dotSize)
                .clip(CircleShape)
                .then(
                    if (selected && color != null) {
                        Modifier.background(color)
                    } else {
                        Modifier.background(rainbowBrush)
                    },
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
            Icon(
                imageVector = if (selected) Icons.Rounded.Check else Icons.Rounded.Palette,
                contentDescription = if (selected) {
                    stringResource(R.string.settings_custom_color_cd_selected)
                } else {
                    stringResource(R.string.settings_custom_color_cd)
                },
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
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
    val startColor = initial ?: Color(0.65f, 0.36f, 0.55f)
    var red by remember { mutableFloatStateOf(startColor.red) }
    var green by remember { mutableFloatStateOf(startColor.green) }
    var blue by remember { mutableFloatStateOf(startColor.blue) }
    val preview = Color(red, green, blue)
    // HEX-поле и слайдеры связаны в обе стороны: корректный код двигает слайдеры,
    // слайдеры переписывают код. Некорректный ввод цвет не применяет.
    var hexInput by remember { mutableStateOf(formatHexColor(startColor)) }
    var hexError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_custom_color)) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(MaterialTheme.shapes.large)
                            .background(preview)
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant,
                                shape = MaterialTheme.shapes.large,
                            ),
                    )
                    Spacer(Modifier.width(14.dp))
                    OutlinedTextField(
                        value = hexInput,
                        onValueChange = { value ->
                            val normalized = value.take(MAX_HEX_INPUT_LENGTH)
                            hexInput = normalized
                            val parsed = parseHexColor(normalized)
                            if (parsed != null) {
                                red = parsed.red
                                green = parsed.green
                                blue = parsed.blue
                                hexError = false
                            } else {
                                hexError = true
                            }
                        },
                        modifier = Modifier.weight(1f),
                        label = { Text(stringResource(R.string.settings_hex_label)) },
                        singleLine = true,
                        isError = hexError,
                        supportingText = {
                            Text(
                                if (hexError) {
                                    stringResource(R.string.settings_hex_error)
                                } else {
                                    stringResource(R.string.settings_hex_hint)
                                },
                            )
                        },
                        shape = MaterialTheme.shapes.large,
                    )
                }
                Spacer(Modifier.height(18.dp))
                RgbSlider(
                    label = stringResource(R.string.settings_red),
                    value = red,
                    accent = Color(0xFFE5484D),
                    onValueChange = { v ->
                        red = v
                        hexInput = formatHexColor(Color(v, green, blue))
                        hexError = false
                    },
                )
                RgbSlider(
                    label = stringResource(R.string.settings_green),
                    value = green,
                    accent = Color(0xFF43A047),
                    onValueChange = { v ->
                        green = v
                        hexInput = formatHexColor(Color(red, v, blue))
                        hexError = false
                    },
                )
                RgbSlider(
                    label = stringResource(R.string.settings_blue),
                    value = blue,
                    accent = Color(0xFF3F6BD8),
                    onValueChange = { v ->
                        blue = v
                        hexInput = formatHexColor(Color(red, green, v))
                        hexError = false
                    },
                )
                if (initial != null) {
                    Spacer(Modifier.height(6.dp))
                    TextButton(
                        onClick = onReset,
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Text(stringResource(R.string.settings_reset))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(preview) }) {
                Text(stringResource(R.string.settings_apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_cancel))
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

/** Допустимые символы HEX-кода без учёта регистра. */
private const val HEX_DIGITS = "0123456789abcdefABCDEF"

/** Предельная длина ввода HEX-поля: «#» плюс 6 цифр. */
private const val MAX_HEX_INPUT_LENGTH = 7

/**
 * Разбирает HEX-код вида #RRGGBB или RRGGBB (регистр не важен).
 * Возвращает null на некорректном коде — диалог подсветит ошибку
 * и оставит слайдеры и превью нетронутыми.
 */
private fun parseHexColor(input: String): Color? {
    val hex = input.removePrefix("#").trim()
    if (hex.length != 6 || !hex.all { HEX_DIGITS.contains(it) }) return null
    val rgb = hex.toLongOrNull(radix = 16) ?: return null
    return Color((rgb or 0xFF000000L).toInt())
}

/**
 * Маленький кружок недавнего цвета: тап применяет цвет мгновенно.
 * Размер меньше кружков основной палитры, чтобы ряд читался вторичным.
 */
@Composable
private fun RecentColorDot(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val dotSize by animateDpAsState(
        targetValue = if (selected) 26.dp else 22.dp,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "recentDotSize",
    )

    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(dotSize)
                .clip(CircleShape)
                .background(color)
                .border(
                    width = 1.5.dp,
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
                    contentDescription = stringResource(R.string.settings_recent_color_cd),
                    tint = Color.White,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
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
                    contentDescription = stringResource(option.nameRes),
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/** Сегментированный выбор языка интерфейса. */
@Composable
private fun SegmentedButtonRow(selected: String, onSelect: (String) -> Unit) {
    val options = listOf(
        AppLocale.LANGUAGE_SYSTEM to stringResource(R.string.settings_language_system),
        AppLocale.LANGUAGE_RU to stringResource(R.string.settings_language_ru),
        AppLocale.LANGUAGE_EN to stringResource(R.string.settings_language_en),
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = selected == value,
                onClick = { onSelect(value) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
            ) {
                Text(label)
            }
        }
    }
}

/** Карточка интеграции Spotify: ключи, вход по OAuth и понятный статус. */
@Composable
private fun SpotifyIntegrationCard(
    settings: AppSettings,
    auth: SpotifyAuthState,
    onSave: (String, String) -> Unit,
    onSignIn: (String, String) -> Unit,
    onSignOut: () -> Unit,
    onOpenGuide: () -> Unit,
) {
    var clientId by remember(settings.spotifyClientId) {
        mutableStateOf(settings.spotifyClientId)
    }
    var clientSecret by remember(settings.spotifyClientSecret) {
        mutableStateOf(settings.spotifyClientSecret)
    }
    val connected = auth.connected
    val displayName = auth.displayName?.takeIf { it.isNotBlank() } ?: "Spotify"

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Spotify",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                // Инструкция всегда под рукой: без неё ключи взять неоткуда.
                TextButton(onClick = onOpenGuide) {
                    Text(
                        text = stringResource(R.string.spotify_guide_open),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
            Spacer(Modifier.height(3.dp))
            Text(
                text = stringResource(R.string.settings_spotify_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = clientId,
                onValueChange = { clientId = it },
                label = { Text(stringResource(R.string.settings_spotify_client_id)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = clientSecret,
                onValueChange = { clientSecret = it },
                label = { Text(stringResource(R.string.settings_spotify_client_secret)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (connected || settings.isSpotifyConfigured) {
                        stringResource(R.string.settings_spotify_connected)
                    } else {
                        stringResource(R.string.settings_spotify_not_connected)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (connected || settings.isSpotifyConfigured) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.weight(1f),
                )
                FilledTonalButton(onClick = { onSave(clientId, clientSecret) }) {
                    Text(stringResource(R.string.settings_save))
                }
            }

            Spacer(Modifier.height(12.dp))
            if (connected) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TrackArtwork(
                        url = auth.imageUrl,
                        contentDescription = null,
                        size = 40,
                        cornerDp = 20,
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = stringResource(R.string.spotify_signed_in_as, displayName),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = onSignOut) {
                        Text(stringResource(R.string.spotify_sign_out))
                    }
                }
            } else {
                // Вход без секрета: PKCE-поток открывает браузер и возвращает
                // пользователя сам. Секрет нужен только поиску без входа.
                Button(
                    onClick = { onSignIn(clientId, clientSecret) },
                    enabled = clientId.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = CircleShape,
                ) {
                    Text(stringResource(R.string.spotify_sign_in))
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.spotify_redirect_uri),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            // Предупреждение о лимите Spotify: один client_id работает не более
            // чем на 10 устройствах, дальше нужен Extended Quota Mode.
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.settings_spotify_quota_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Инструкция «как получить ключи Spotify»: четыре шага, адрес возврата
 * и кнопка открытия Developer Dashboard.
 *
 * Redirect URI показываем отдельной строкой: его копируют в Dashboard
 * символ в символ, иначе Spotify откажет в авторизации.
 */
@Composable
private fun SpotifyGuideDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.spotify_guide_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                GuideStep(
                    number = 1,
                    titleRes = R.string.spotify_guide_step1_title,
                    bodyRes = R.string.spotify_guide_step1_body,
                )
                GuideStep(
                    number = 2,
                    titleRes = R.string.spotify_guide_step2_title,
                    bodyRes = R.string.spotify_guide_step2_body,
                )
                GuideStep(
                    number = 3,
                    titleRes = R.string.spotify_guide_step3_title,
                    bodyRes = R.string.spotify_guide_step3_body,
                )
                GuideStep(
                    number = 4,
                    titleRes = R.string.spotify_guide_step4_title,
                    bodyRes = R.string.spotify_guide_step4_body,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.spotify_redirect_uri),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.spotify_guide_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(SPOTIFY_DASHBOARD_URL)),
                        )
                    }
                },
            ) {
                Text(stringResource(R.string.spotify_guide_open_dashboard))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_close))
            }
        },
    )
}

/** Один шаг инструкции: номер в кружке, заголовок и пояснение. */
@Composable
private fun GuideStep(number: Int, titleRes: Int, bodyRes: Int) {
    Row(modifier = Modifier.padding(bottom = 12.dp)) {
        Surface(
            modifier = Modifier.size(24.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = number.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(bodyRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Адрес Spotify Developer Dashboard для кнопки инструкции. */
private const val SPOTIFY_DASHBOARD_URL = "https://developer.spotify.com/dashboard"
