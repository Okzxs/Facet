package com.facet.music.core.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.facet.music.MainActivity
import com.facet.music.R

internal val WidgetActionKey = ActionParameters.Key<String>("widget_action")

internal const val ACTION_PREVIOUS = "previous"
internal const val ACTION_NEXT = "next"
internal const val ACTION_TOGGLE = "toggle"
internal const val ACTION_LIKE = "like"

private val NarrowWidth = 240.dp
private val BarHeight = 120.dp

@Composable
internal fun WidgetContent(context: Context, snapshot: WidgetSnapshot, palette: WidgetPalette) {
    val size = LocalSize.current
    val openIntent = widgetOpenIntent(context)
    val openAction = actionStartActivity(openIntent)
    val cover = if (snapshot.hasArtwork) WidgetArtworkStore.cover(context) else null
    val backdrop = if (snapshot.hasArtwork) WidgetArtworkStore.backdrop(context) else null

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(palette.cornerRadius)
            .background(ColorProvider(palette.background))
            .clickable(openAction),
    ) {
        if (snapshot.hasTrack && backdrop != null) {
            Image(
                provider = ImageProvider(backdrop),
                contentDescription = null,
                modifier = GlanceModifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .cornerRadius(palette.cornerRadius)
                    .background(ColorProvider(palette.scrim)),
            ) {
            }
        }
        when {
            !snapshot.hasTrack -> EmptyLayout(context, palette, size)
            size.height < BarHeight -> BarLayout(context, snapshot, palette, size, cover)
            size.width < NarrowWidth -> NarrowLayout(context, snapshot, palette, size)
            else -> WideLayout(context, snapshot, palette, size, cover)
        }
    }
}

@Composable
private fun WideLayout(
    context: Context,
    snapshot: WidgetSnapshot,
    palette: WidgetPalette,
    size: androidx.compose.ui.unit.DpSize,
    cover: Bitmap?,
) {
    val large = size.height >= 165.dp
    val huge = size.height >= 200.dp
    val padding = (size.height * 0.08f).coerceIn(8.dp, 20.dp)
    val gap = if (large) 18.dp else 10.dp
    val coverSize = (size.height * 0.44f).coerceIn(44.dp, 112.dp)
    val titleSize = if (large) 20.sp else 14.sp
    val artistSize = if (large) 13.sp else 10.sp
    val playSize = if (large) 44.dp else 28.dp
    val sideSize = if (large) 26.dp else 20.dp
    val likeSize = if (large) 22.dp else 16.dp
    val titleLines = if (huge) 2 else 1
    val columnWidth = (size.width - padding * 2 - coverSize - gap).coerceAtLeast(1.dp)

    Row(
        modifier = GlanceModifier.fillMaxSize().padding(padding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WidgetCover(cover, coverSize, palette, large)
        Spacer(GlanceModifier.width(gap))
        Column(modifier = GlanceModifier.fillMaxHeight().defaultWeight()) {
            Column {
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "FACET",
                        style = TextStyle(
                            color = ColorProvider(palette.accent),
                            fontSize = if (large) 10.sp else 8.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif,
                        ),
                    )
                    if (large) {
                        Spacer(GlanceModifier.width(8.dp))
                        Text(
                            text = stateLabel(context, snapshot.isPlaying),
                            style = TextStyle(
                                color = ColorProvider(palette.muted),
                                fontSize = 8.sp,
                                fontFamily = FontFamily.SansSerif,
                            ),
                        )
                    }
                    Spacer(GlanceModifier.defaultWeight())
                    WidgetLikeButton(snapshot.isLiked, palette, likeSize)
                }
                Spacer(GlanceModifier.height(if (large) 8.dp else 2.dp))
                Text(
                    text = snapshot.title,
                    maxLines = titleLines,
                    style = TextStyle(
                        color = ColorProvider(palette.onBackground),
                        fontSize = titleSize,
                        fontWeight = FontWeight.Medium,
                        fontFamily = titleFamily(palette),
                    ),
                )
                Spacer(GlanceModifier.height(2.dp))
                Text(
                    text = snapshot.artist,
                    maxLines = 1,
                    style = TextStyle(
                        color = ColorProvider(palette.muted),
                        fontSize = artistSize,
                        fontFamily = FontFamily.SansSerif,
                    ),
                )
            }
            Spacer(GlanceModifier.defaultWeight())
            Column {
                if (huge) {
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = WidgetFormat.time(snapshot.positionMs),
                            style = TextStyle(
                                color = ColorProvider(palette.muted),
                                fontSize = 9.sp,
                                fontFamily = FontFamily.SansSerif,
                            ),
                        )
                        Spacer(GlanceModifier.defaultWeight())
                        Text(
                            text = WidgetFormat.time(snapshot.durationMs),
                            style = TextStyle(
                                color = ColorProvider(palette.muted),
                                fontSize = 9.sp,
                                fontFamily = FontFamily.SansSerif,
                            ),
                        )
                    }
                    Spacer(GlanceModifier.height(5.dp))
                }
                WidgetProgress(snapshot.progress, columnWidth, palette, if (large) 12.dp else 10.dp)
                Spacer(GlanceModifier.height(if (large) 8.dp else 2.dp))
                WidgetControls(
                    isPlaying = snapshot.isPlaying,
                    palette = palette,
                    context = context,
                    playSize = playSize,
                    sideSize = sideSize,
                )
            }
        }
    }
}

@Composable
private fun NarrowLayout(
    context: Context,
    snapshot: WidgetSnapshot,
    palette: WidgetPalette,
    size: androidx.compose.ui.unit.DpSize,
) {
    val tall = size.height >= 165.dp
    val compact = size.height < 140.dp
    val padding = when {
        tall -> 16.dp
        compact -> 10.dp
        else -> 12.dp
    }
    val playSize = when {
        tall -> 38.dp
        compact -> 28.dp
        else -> 32.dp
    }
    val sideSize = if (tall) 24.dp else 20.dp
    val titleLines = if (compact) 1 else 2
    val titleSize = when {
        tall -> 18.sp
        compact -> 14.sp
        else -> 15.sp
    }
    val artistSize = if (tall) 12.sp else 10.sp
    val width = (size.width - padding * 2).coerceAtLeast(1.dp)

    Column(modifier = GlanceModifier.fillMaxSize().padding(padding)) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "FACET",
                style = TextStyle(
                    color = ColorProvider(palette.accent),
                    fontSize = if (tall) 10.sp else 8.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif,
                ),
            )
            if (tall) {
                Spacer(GlanceModifier.width(8.dp))
                Text(
                    text = stateLabel(context, snapshot.isPlaying),
                    style = TextStyle(
                        color = ColorProvider(palette.muted),
                        fontSize = 8.sp,
                        fontFamily = FontFamily.SansSerif,
                    ),
                )
            }
            Spacer(GlanceModifier.defaultWeight())
            WidgetLikeButton(snapshot.isLiked, palette, if (tall) 22.dp else 18.dp)
        }
        Spacer(GlanceModifier.defaultWeight())
        Column {
            Text(
                text = snapshot.title,
                maxLines = titleLines,
                style = TextStyle(
                    color = ColorProvider(palette.onBackground),
                    fontSize = titleSize,
                    fontWeight = FontWeight.Medium,
                    fontFamily = titleFamily(palette),
                ),
            )
            Spacer(GlanceModifier.height(2.dp))
            Text(
                text = snapshot.artist,
                maxLines = 1,
                style = TextStyle(
                    color = ColorProvider(palette.muted),
                    fontSize = artistSize,
                    fontFamily = FontFamily.SansSerif,
                ),
            )
        }
        Spacer(GlanceModifier.defaultWeight())
        Column {
            WidgetProgress(snapshot.progress, width, palette, if (tall) 12.dp else 10.dp)
            Spacer(GlanceModifier.height(if (tall) 8.dp else 4.dp))
            WidgetControls(
                isPlaying = snapshot.isPlaying,
                palette = palette,
                context = context,
                playSize = playSize,
                sideSize = sideSize,
            )
        }
    }
}

@Composable
private fun BarLayout(
    context: Context,
    snapshot: WidgetSnapshot,
    palette: WidgetPalette,
    size: androidx.compose.ui.unit.DpSize,
    cover: Bitmap?,
) {
    val tall = size.height >= 96.dp
    val padding = if (tall) 12.dp else 8.dp
    val showProgress = size.height >= 76.dp
    val coverSize = if (tall) 44.dp else 34.dp
    val playSize = if (tall) 32.dp else 26.dp
    val sideSize = if (tall) 22.dp else 18.dp
    val likeSize = if (tall) 20.dp else 18.dp
    val titleSize = if (tall) 13.sp else 12.sp
    val progressHeight = if (tall) 10.dp else 8.dp
    val progressGap = if (tall) 4.dp else 2.dp
    val width = (size.width - padding * 2).coerceAtLeast(1.dp)

    if (size.width >= NarrowWidth) {
        val likeReserve = likeSize + if (tall) 10.dp else 8.dp
        val contentWidth = (width - likeReserve).coerceAtLeast(1.dp)
        val coverGap = if (tall) 12.dp else 8.dp
        val controlsGap = if (tall) 8.dp else 4.dp
        val textWidth = (
            contentWidth - coverSize - coverGap - 8.dp -
                sideSize - controlsGap - playSize - controlsGap - sideSize
            ).coerceAtLeast(40.dp)
        val rowHeight = (
            size.height - padding * 2 - progressHeight -
                if (showProgress) progressGap else 0.dp
            ).coerceAtLeast(1.dp)
        Box(
            modifier = GlanceModifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Column(modifier = GlanceModifier.fillMaxSize().padding(end = likeReserve)) {
                Row(
                    modifier = GlanceModifier.fillMaxWidth().height(rowHeight),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    WidgetCover(cover, coverSize, palette, false)
                    Spacer(GlanceModifier.width(coverGap))
                    Column(modifier = GlanceModifier.width(textWidth)) {
                        Text(
                            text = snapshot.title,
                            maxLines = 1,
                            style = TextStyle(
                                color = ColorProvider(palette.onBackground),
                                fontSize = titleSize,
                                fontWeight = FontWeight.Medium,
                                fontFamily = FontFamily.SansSerif,
                            ),
                        )
                        Text(
                            text = snapshot.artist,
                            maxLines = 1,
                            style = TextStyle(
                                color = ColorProvider(palette.muted),
                                fontSize = if (tall) 10.sp else 9.sp,
                                fontFamily = FontFamily.SansSerif,
                            ),
                        )
                    }
                    Spacer(GlanceModifier.width(8.dp))
                    WidgetIconButton(
                        iconRes = R.drawable.ic_widget_previous,
                        contentDescription = context.getString(R.string.common_previous_cd),
                        action = ACTION_PREVIOUS,
                        tint = palette.muted,
                        target = sideSize,
                        icon = sideSize,
                    )
                    Spacer(GlanceModifier.width(controlsGap))
                    WidgetPlayButton(snapshot.isPlaying, palette, context, playSize)
                    Spacer(GlanceModifier.width(controlsGap))
                    WidgetIconButton(
                        iconRes = R.drawable.ic_widget_next,
                        contentDescription = context.getString(R.string.common_next_cd),
                        action = ACTION_NEXT,
                        tint = palette.muted,
                        target = sideSize,
                        icon = sideSize,
                    )
                }
                if (showProgress) {
                    Spacer(GlanceModifier.height(progressGap))
                    WidgetProgress(
                        progress = snapshot.progress,
                        width = contentWidth,
                        palette = palette,
                        height = progressHeight,
                    )
                }
            }
            WidgetLikeButton(snapshot.isLiked, palette, likeSize)
        }
    } else {
        Column(modifier = GlanceModifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WidgetCover(cover, coverSize, palette, false)
                Spacer(GlanceModifier.width(8.dp))
                Column(
                    modifier = GlanceModifier.width(
                        (width - coverSize - 8.dp).coerceAtLeast(1.dp),
                    ),
                ) {
                    Text(
                        text = snapshot.title,
                        maxLines = 1,
                        style = TextStyle(
                            color = ColorProvider(palette.onBackground),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = FontFamily.SansSerif,
                        ),
                    )
                    Text(
                        text = snapshot.artist,
                        maxLines = 1,
                        style = TextStyle(
                            color = ColorProvider(palette.muted),
                            fontSize = 9.sp,
                            fontFamily = FontFamily.SansSerif,
                        ),
                    )
                }
            }
            Spacer(GlanceModifier.defaultWeight())
            if (showProgress) {
                WidgetProgress(
                    progress = snapshot.progress,
                    width = width,
                    palette = palette,
                    height = 8.dp,
                )
                Spacer(GlanceModifier.defaultWeight())
            }
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WidgetIconButton(
                    iconRes = R.drawable.ic_widget_previous,
                    contentDescription = context.getString(R.string.common_previous_cd),
                    action = ACTION_PREVIOUS,
                    tint = palette.muted,
                    target = sideSize,
                    icon = sideSize,
                )
                Spacer(GlanceModifier.width(if (playSize >= 32.dp) 6.dp else 4.dp))
                WidgetPlayButton(snapshot.isPlaying, palette, context, playSize)
                Spacer(GlanceModifier.width(if (playSize >= 32.dp) 6.dp else 4.dp))
                WidgetIconButton(
                    iconRes = R.drawable.ic_widget_next,
                    contentDescription = context.getString(R.string.common_next_cd),
                    action = ACTION_NEXT,
                    tint = palette.muted,
                    target = sideSize,
                    icon = sideSize,
                )
                Spacer(GlanceModifier.width(if (playSize >= 32.dp) 6.dp else 4.dp))
                WidgetLikeButton(
                    liked = snapshot.isLiked,
                    palette = palette,
                    size = if (playSize >= 32.dp) 18.dp else 16.dp,
                )
            }
        }
    }
}

@Composable
private fun EmptyLayout(
    context: Context,
    palette: WidgetPalette,
    size: androidx.compose.ui.unit.DpSize,
) {
    if (size.height < BarHeight) {
        Row(
            modifier = GlanceModifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "FACET",
                style = TextStyle(
                    color = ColorProvider(palette.accent),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif,
                ),
            )
            Spacer(GlanceModifier.width(10.dp))
            Text(
                text = context.getString(R.string.widget_hint),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight(),
                style = TextStyle(
                    color = ColorProvider(palette.muted),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.SansSerif,
                ),
            )
            WidgetOpenPill(context, palette, 11.sp)
        }
    } else {
        Column(
            modifier = GlanceModifier.fillMaxSize().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(GlanceModifier.defaultWeight())
            Text(
                text = "FACET",
                style = TextStyle(
                    color = ColorProvider(palette.accent),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif,
                ),
            )
            Spacer(GlanceModifier.height(4.dp))
            Text(
                text = context.getString(R.string.widget_no_track),
                style = TextStyle(
                    color = ColorProvider(palette.onBackground),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = titleFamily(palette),
                ),
            )
            Spacer(GlanceModifier.height(3.dp))
            Text(
                text = context.getString(R.string.widget_hint),
                maxLines = 2,
                modifier = GlanceModifier.fillMaxWidth(),
                style = TextStyle(
                    color = ColorProvider(palette.muted),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    fontFamily = FontFamily.SansSerif,
                ),
            )
            Spacer(GlanceModifier.height(10.dp))
            Box(
                modifier = GlanceModifier
                    .cornerRadius(18.dp)
                    .background(ColorProvider(palette.accent))
                    .padding(horizontal = 16.dp, vertical = 7.dp)
                    .clickable(actionStartActivity(widgetOpenIntent(context))),
            ) {
                Text(
                    text = context.getString(R.string.widget_open),
                    style = TextStyle(
                        color = ColorProvider(palette.onAccent),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.SansSerif,
                    ),
                )
            }
            Spacer(GlanceModifier.defaultWeight())
        }
    }
}

@Composable
private fun WidgetOpenPill(context: Context, palette: WidgetPalette, fontSize: androidx.compose.ui.unit.TextUnit) {
    Box(
        modifier = GlanceModifier
            .cornerRadius(16.dp)
            .background(ColorProvider(palette.accent))
            .padding(horizontal = 12.dp, vertical = 5.dp)
            .clickable(actionStartActivity(widgetOpenIntent(context))),
    ) {
        Text(
            text = context.getString(R.string.widget_open),
            style = TextStyle(
                color = ColorProvider(palette.onAccent),
                fontSize = fontSize,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.SansSerif,
            ),
        )
    }
}

@Composable
private fun WidgetCover(
    bitmap: Bitmap?,
    size: Dp,
    palette: WidgetPalette,
    large: Boolean,
) {
    val radius = if (large) 18.dp else 10.dp
    if (bitmap != null) {
        Image(
            provider = ImageProvider(bitmap),
            contentDescription = null,
            modifier = GlanceModifier.size(size).cornerRadius(radius),
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            modifier = GlanceModifier
                .size(size)
                .cornerRadius(radius)
                .background(ColorProvider(palette.accentSoft)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "F",
                style = TextStyle(
                    color = ColorProvider(palette.onAccent),
                    fontSize = if (large) 24.sp else 15.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Serif,
                ),
            )
        }
    }
}

@Composable
private fun WidgetProgress(
    progress: Float,
    width: Dp,
    palette: WidgetPalette,
    height: Dp,
) {
    val context = LocalContext.current
    val bitmap = WidgetWave.render(
        context = context,
        width = width,
        height = height,
        progress = progress,
        accent = palette.accent,
        track = palette.track,
    )
    if (bitmap != null) {
        Image(
            provider = ImageProvider(bitmap),
            contentDescription = null,
            modifier = GlanceModifier.width(width).height(height),
            contentScale = ContentScale.FillBounds,
        )
    }
}

@Composable
private fun WidgetControls(
    isPlaying: Boolean,
    palette: WidgetPalette,
    context: Context,
    playSize: Dp,
    sideSize: Dp,
) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WidgetIconButton(
            iconRes = R.drawable.ic_widget_previous,
            contentDescription = context.getString(R.string.common_previous_cd),
            action = ACTION_PREVIOUS,
            tint = palette.muted,
            target = sideSize,
            icon = sideSize,
        )
        Spacer(GlanceModifier.width(if (playSize >= 40.dp) 18.dp else 12.dp))
        WidgetPlayButton(isPlaying, palette, context, playSize)
        Spacer(GlanceModifier.width(if (playSize >= 40.dp) 18.dp else 12.dp))
        WidgetIconButton(
            iconRes = R.drawable.ic_widget_next,
            contentDescription = context.getString(R.string.common_next_cd),
            action = ACTION_NEXT,
            tint = palette.muted,
            target = sideSize,
            icon = sideSize,
        )
    }
}

@Composable
private fun WidgetPlayButton(
    isPlaying: Boolean,
    palette: WidgetPalette,
    context: Context,
    size: Dp,
) {
    Box(
        modifier = GlanceModifier
            .size(size)
            .cornerRadius(size / 2)
            .background(ColorProvider(palette.accent))
            .clickable(
                actionRunCallback<WidgetActionCallback>(
                    actionParametersOf(WidgetActionKey to ACTION_TOGGLE),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(
                if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
            ),
            contentDescription = context.getString(
                if (isPlaying) R.string.common_pause_cd else R.string.common_play_cd,
            ),
            modifier = GlanceModifier.size(size * 0.44f),
            colorFilter = androidx.glance.ColorFilter.tint(ColorProvider(palette.onAccent)),
        )
    }
}

@Composable
private fun WidgetIconButton(
    iconRes: Int,
    contentDescription: String,
    action: String,
    tint: Color,
    target: Dp,
    icon: Dp,
) {
    Box(
        modifier = GlanceModifier
            .size(target)
            .clickable(
                actionRunCallback<WidgetActionCallback>(
                    actionParametersOf(WidgetActionKey to action),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(iconRes),
            contentDescription = contentDescription,
            modifier = GlanceModifier.size(icon),
            colorFilter = androidx.glance.ColorFilter.tint(ColorProvider(tint)),
        )
    }
}

@Composable
private fun WidgetLikeButton(
    liked: Boolean,
    palette: WidgetPalette,
    size: Dp,
) {
    val tint = if (liked) palette.accent else palette.muted
    Box(
        modifier = GlanceModifier
            .size(size)
            .clickable(
                actionRunCallback<WidgetActionCallback>(
                    actionParametersOf(WidgetActionKey to ACTION_LIKE),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(
                if (liked) R.drawable.ic_widget_like_filled else R.drawable.ic_widget_like,
            ),
            contentDescription = LocalContext.current.getString(
                if (liked) R.string.widget_unlike_cd else R.string.widget_like_cd,
            ),
            modifier = GlanceModifier.size(size),
            colorFilter = androidx.glance.ColorFilter.tint(ColorProvider(tint)),
        )
    }
}

private fun titleFamily(palette: WidgetPalette): FontFamily =
    if (palette.serifTitle) FontFamily.Serif else FontFamily.SansSerif

private fun stateLabel(context: Context, isPlaying: Boolean): String = context.getString(
    if (isPlaying) R.string.widget_state_playing else R.string.widget_state_paused,
)

private fun widgetOpenIntent(context: Context): Intent =
    Intent(context, MainActivity::class.java).apply {
        putExtra(MainActivity.EXTRA_OPEN_PLAYER, true)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
    }

