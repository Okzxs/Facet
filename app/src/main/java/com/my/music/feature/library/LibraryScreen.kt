package com.my.music.feature.library

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(onTrackClick: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Библиотека") },
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
                Text("📚 Библиотека", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Плейлисты, лайки и история появятся здесь",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
