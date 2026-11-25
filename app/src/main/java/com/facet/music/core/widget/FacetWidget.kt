package com.facet.music.core.widget

import android.content.Context
import android.content.Intent
import android.app.PendingIntent
import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.unit.ColorProvider
import androidx.glance.GlanceId
import androidx.glance.ColorFilter
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.glance.GlanceModifier
import androidx.glance.currentState
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState

import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.updateAll
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.media3.session.MediaButtonReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.LocalContext
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.facet.music.MainActivity
import com.facet.music.R
import com.facet.music.core.player.MusicService

private val WideSize = DpSize(250.dp, 110.dp)
private val CompactSize = DpSize(150.dp, 150.dp)

private val Ink = Color(0xFF12141A)
private val Bone = Color(0xFFF2EFE8)
private val Muted = Color(0xFFB9B3A8)

private val ActionKey = ActionParameters.Key<String>("action")

class FacetWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(WideSize, CompactSize))

    override val stateDefinition: GlanceStateDefinition<Preferences> =
        PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val prefs = currentState<Preferences>()
            val stored = WidgetSnapshotStore.read(context)
            val snapshot = if ((prefs[TITLE_KEY] ?: "").isBlank() && stored.hasTrack) {
                stored
            } else {
                WidgetSnapshot(
                    title = prefs[TITLE_KEY].orEmpty(),
                    artist = prefs[ARTIST_KEY].orEmpty(),
                    isPlaying = prefs[PLAYING_KEY] ?: false,
                    accentArgb = prefs[ACCENT_KEY] ?: WidgetSnapshot.DEFAULT_ACCENT,
                )
            }
            WidgetContent(snapshot, context)
        }
    }
}

suspend fun pushWidgetState(context: Context, snapshot: WidgetSnapshot) {
    WidgetSnapshotStore.write(context, snapshot)
    val manager = GlanceAppWidgetManager(context)
    val ids = runCatching { manager.getGlanceIds(FacetWidget::class.java) }
        .getOrDefault(emptyList())
    ids.forEach { id ->
        runCatching {
            updateAppWidgetState(context, PreferencesGlanceStateDefinition, id) { prefs ->
                prefs.toMutablePreferences().apply {
                    this[TITLE_KEY] = snapshot.title
                    this[ARTIST_KEY] = snapshot.artist
                    this[PLAYING_KEY] = snapshot.isPlaying
                    this[ACCENT_KEY] = snapshot.accentArgb
                }
            }
            FacetWidget().update(context, id)
        }
    }
}

val TITLE_KEY = stringPreferencesKey("title")
val ARTIST_KEY = stringPreferencesKey("artist")
val PLAYING_KEY = booleanPreferencesKey("playing")
val ACCENT_KEY = intPreferencesKey("accent")

@Composable
private fun WidgetContent(snapshot: WidgetSnapshot, context: Context) {
    val size = LocalSize.current
    val wide = size.width >= WideSize.width
    val accent = Color(snapshot.accentArgb)

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(24.dp)
            .background(ColorProvider(Ink))
            .padding(16.dp)
            .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
        verticalAlignment = Alignment.Top,
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "FACET",
                modifier = GlanceModifier.defaultWeight(),
                style = TextStyle(
                    color = ColorProvider(accent),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif,
                ),
            )
            if (snapshot.hasTrack) {
                Text(
                    text = context.getString(
                        if (snapshot.isPlaying) {
                            R.string.widget_state_playing
                        } else {
                            R.string.widget_state_paused
                        },
                    ),
                    style = TextStyle(
                        color = ColorProvider(Muted),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.SansSerif,
                    ),
                )
            }
        }
        Spacer(GlanceModifier.height(10.dp))
        Text(
            text = if (snapshot.hasTrack) {
                snapshot.title
            } else {
                context.getString(R.string.widget_no_track)
            },
            maxLines = if (wide) 1 else 2,
            style = TextStyle(
                color = ColorProvider(Bone),
                fontSize = if (wide) 16.sp else 14.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.SansSerif,
            ),
        )
        Spacer(GlanceModifier.height(2.dp))
        Text(
            text = if (snapshot.hasTrack) {
                snapshot.artist
            } else {
                context.getString(R.string.widget_hint)
            },
            maxLines = 1,
            style = TextStyle(
                color = ColorProvider(Muted),
                fontSize = 12.sp,
            ),
        )
        Spacer(GlanceModifier.defaultWeight())
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WidgetButton(
                iconRes = R.drawable.ic_widget_previous,
                contentDescription = context.getString(R.string.common_previous_cd),
                action = ACTION_PREVIOUS,
                tint = Muted,
            )
            Spacer(GlanceModifier.width(18.dp))
            WidgetButton(
                iconRes = if (snapshot.isPlaying) {
                    R.drawable.ic_widget_pause
                } else {
                    R.drawable.ic_widget_play
                },
                contentDescription = context.getString(
                    if (snapshot.isPlaying) R.string.common_pause_cd else R.string.common_play_cd,
                ),
                action = ACTION_TOGGLE,
                tint = accent,
                large = true,
            )
            Spacer(GlanceModifier.width(18.dp))
            WidgetButton(
                iconRes = R.drawable.ic_widget_next,
                contentDescription = context.getString(R.string.common_next_cd),
                action = ACTION_NEXT,
                tint = Muted,
            )
        }
    }
}

@Composable
private fun WidgetButton(
    iconRes: Int,
    contentDescription: String,
    action: String,
    tint: Color,
    large: Boolean = false,
) {
    Image(
        provider = ImageProvider(iconRes),
        contentDescription = contentDescription,
        modifier = GlanceModifier
            .size(if (large) 36.dp else 28.dp)
            .clickable(
                actionRunCallback<WidgetActionCallback>(
                    actionParametersOf(ActionKey to action),
                ),
            ),
        colorFilter = ColorFilter.tint(ColorProvider(tint)),
    )
}

class WidgetActionCallback : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val action = parameters[ActionKey] ?: return
        val keyCode = when (action) {
            ACTION_PREVIOUS -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            ACTION_NEXT -> KeyEvent.KEYCODE_MEDIA_NEXT
            else -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
        }
        runCatching { mediaButtonIntent(context, keyCode).send() }
        kotlinx.coroutines.delay(WIDGET_REFRESH_DELAY_MS)
        runCatching {
            pushWidgetState(context, WidgetSnapshotStore.read(context))
        }.onFailure { android.util.Log.w("FacetWidget", "push failed", it) }
    }
}

private fun mediaButtonIntent(context: Context, keyCode: Int): PendingIntent {
    val event = Intent(Intent.ACTION_MEDIA_BUTTON)
        .setClass(context, MediaButtonReceiver::class.java)
        .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
    return PendingIntent.getBroadcast(
        context,
        keyCode,
        event,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

private const val ACTION_PREVIOUS = "previous"
private const val ACTION_NEXT = "next"
private const val ACTION_TOGGLE = "toggle"
private const val WIDGET_REFRESH_DELAY_MS = 450L

class FacetWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FacetWidget()
}
