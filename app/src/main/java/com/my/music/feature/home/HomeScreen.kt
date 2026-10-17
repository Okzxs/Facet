package com.my.music.feature.home

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onTrackClick: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Главная") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "🎵 Главная",
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Здесь будет лента рекомендаций",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onTrackClick) {
                Text("Открыть плеер (тест)")
            }
        }
    }
}
