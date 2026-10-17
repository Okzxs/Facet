package com.my.music.feature.wave

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaveScreen(onTrackClick: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Моя волна") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🌊 Моя волна", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Персональные рекомендации на основе твоих лайков",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
