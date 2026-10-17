package com.my.music

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import com.my.music.core.ui.theme.MyMusicTheme
import com.my.music.navigation.AppNavGraph

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyMusicTheme {
                AppNavGraph()
            }
        }
    }
}