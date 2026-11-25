package com.facet.music.core.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.currentState
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.action.ActionParameters
import androidx.media3.session.MediaButtonReceiver
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class FacetWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override val stateDefinition: GlanceStateDefinition<Preferences> =
        PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val stored = WidgetSnapshotStore.read(context)
            val fromState = currentState<Preferences>().toWidgetSnapshot()
            val snapshot = if (fromState.hasTrack || !stored.hasTrack) fromState else stored
            WidgetContent(context, snapshot, WidgetTheme.resolve(context))
        }
    }
}

private val pushMutex = Mutex()

suspend fun pushWidgetState(context: Context) {
    val manager = GlanceAppWidgetManager(context)
    val ids = runCatching { manager.getGlanceIds(FacetWidget::class.java) }
        .getOrDefault(emptyList())
    if (ids.isEmpty()) return
    pushMutex.withLock {
        val snapshot = WidgetSnapshotStore.read(context)
        ids.forEach { id ->
            runCatching {
                updateAppWidgetState(context, PreferencesGlanceStateDefinition, id) { prefs ->
                    prefs.toMutablePreferences().apply { putSnapshot(snapshot) }
                }
                FacetWidget().update(context, id)
            }
        }
    }
}

fun hasWidgets(context: Context): Boolean = runCatching {
    AppWidgetManager.getInstance(context)
        .getAppWidgetIds(ComponentName(context, FacetWidgetReceiver::class.java))
        .isNotEmpty()
}.getOrDefault(false)

class WidgetActionCallback : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val action = parameters[WidgetActionKey] ?: return
        if (action == ACTION_LIKE) {
            runCatching {
                context.sendBroadcast(
                    Intent(ACTION_WIDGET_LIKE).setPackage(context.packageName),
                )
            }
        } else {
            sendMediaButton(context, action)
        }
        delay(REFRESH_DELAY_MS)
        runCatching { pushWidgetState(context) }
    }

    private fun sendMediaButton(context: Context, action: String) {
        val keyCode = when (action) {
            ACTION_PREVIOUS -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            ACTION_NEXT -> KeyEvent.KEYCODE_MEDIA_NEXT
            else -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
        }
        runCatching { mediaButtonIntent(context, keyCode).send() }
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

private const val REFRESH_DELAY_MS = 350L

class FacetWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = FacetWidget()
}
