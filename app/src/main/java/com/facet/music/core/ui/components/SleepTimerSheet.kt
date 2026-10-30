package com.facet.music.core.ui.components

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.TimerOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.facet.music.R
import com.facet.music.core.player.SleepTimerPresetsMinutes
import com.facet.music.core.player.SleepTimerState
import com.facet.music.core.player.sleepTimerRemainingMinutes
import java.util.Date

/**
 * Шторка таймера сна: пресеты 15/30/60 минут и отключение.
 *
 * Если таймер уже идёт, сверху показано время остановки и остаток минут —
 * по мотивам Riff («Sleep timer — Off, 15/30/45/60, End of track») и Dotify
 * (`player.timer.stops_at`). Стиль — как у шторки плеера: тот же ModalBottomSheet
 * и `surfaceContainerLow`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTimerSheet(
    state: SleepTimerState?,
    onSelectMinutes: (Int) -> Unit,
    onCancelTimer: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    // Время остановки — в формате локали устройства (12/24 часа решает система).
    val endsAtText = remember(state?.endsAtMillis) {
        state?.let { DateFormat.getTimeFormat(context).format(Date(it.endsAtMillis)) }
    }
    val remainingMinutes = state?.let {
        sleepTimerRemainingMinutes(it.endsAtMillis, System.currentTimeMillis())
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Bedtime,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.size(12.dp))
                Text(
                    text = stringResource(R.string.sleep_timer_title),
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (state != null && endsAtText != null) {
                    stringResource(R.string.sleep_timer_remaining, endsAtText, remainingMinutes ?: 0)
                } else {
                    stringResource(R.string.sleep_timer_hint)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))

            SleepTimerPresetsMinutes.forEach { minutes ->
                val selected = state?.minutes == minutes
                ListItem(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .clickable { onSelectMinutes(minutes) },
                    headlineContent = {
                        Text(stringResource(R.string.sleep_timer_option_minutes, minutes))
                    },
                    trailingContent = if (selected) {
                        {
                            Icon(
                                imageVector = Icons.Rounded.Bedtime,
                                contentDescription = stringResource(R.string.sleep_timer_selected_cd),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    } else {
                        null
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }

            if (state != null) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                ListItem(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .clickable { onCancelTimer() },
                    headlineContent = {
                        Text(
                            text = stringResource(R.string.sleep_timer_cancel),
                            color = MaterialTheme.colorScheme.error,
                        )
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Rounded.TimerOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }
    }
}
