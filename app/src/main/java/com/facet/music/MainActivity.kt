package com.facet.music

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import com.facet.music.core.di.ApplicationScope
import com.facet.music.core.i18n.AppLocale
import com.facet.music.core.spotify.SpotifyAuthEvent
import com.facet.music.core.ui.theme.FacetTheme
import com.facet.music.data.repository.AppSettings
import com.facet.music.data.repository.SettingsRepository
import com.facet.music.core.analytics.AnalyticsEvents
import com.facet.music.core.analytics.AnalyticsTracker
import com.facet.music.data.repository.SpotifyAuthRepository
import com.facet.music.feature.onboarding.OnboardingScreen
import com.facet.music.navigation.AppNavGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var spotifyAuthRepository: SpotifyAuthRepository

    @Inject
    lateinit var analyticsTracker: AnalyticsTracker

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    private var appliedLanguage: String = AppLocale.LANGUAGE_SYSTEM

    override fun attachBaseContext(newBase: Context) {
        appliedLanguage = AppLocale.savedLanguage(newBase)
        super.attachBaseContext(AppLocale.wrap(newBase, appliedLanguage))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) {
            analyticsTracker.track(AnalyticsEvents.APP_OPEN)
            handleSpotifyRedirect(intent)
        }
        setContent {
            val settings by settingsRepository.settings.collectAsState(initial = AppSettings())
            var settingsLoaded by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                settingsRepository.settings.first()
                settingsLoaded = true
            }
            LaunchedEffect(settings.appLanguage) {
                if (settings.appLanguage != appliedLanguage) {
                    appliedLanguage = settings.appLanguage
                    recreate()
                }
            }
            val accentOverride = if (
                settings.accentEnabled && Build.VERSION.SDK_INT < Build.VERSION_CODES.S
            ) {
                Color(settings.accentColorArgb)
            } else {
                null
            }
            val customAccentOverride = if (settings.accentEnabled) {
                settings.customAccentColor?.let { Color(it) }
            } else {
                null
            }

            FacetTheme(
                themeMode = settings.themeMode,
                accentColor = accentOverride,
                customAccentColor = customAccentOverride,
            ) {
                val context = LocalContext.current
                LaunchedEffect(Unit) {
                    spotifyAuthRepository.events.collect { event ->
                        val message = when (event) {
                            SpotifyAuthEvent.SignedIn -> R.string.spotify_signed_in_toast
                            SpotifyAuthEvent.Failed -> R.string.spotify_sign_in_failed
                        }
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
                }
                val notificationPermission = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) { }
                LaunchedEffect(settingsLoaded, settings.onboardingCompleted) {
                    val android13 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    val granted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS,
                    ) == PackageManager.PERMISSION_GRANTED
                    if (settingsLoaded && settings.onboardingCompleted && android13 && !granted) {
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                when {
                    !settingsLoaded -> Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
                    )

                    !settings.onboardingCompleted -> OnboardingScreen()

                    else -> AppNavGraph()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSpotifyRedirect(intent)
    }

    private fun handleSpotifyRedirect(intent: Intent?) {
        val data = intent?.dataString ?: return
        if (!spotifyAuthRepository.isRedirect(data)) return
        intent.data = null
        setIntent(intent)
        applicationScope.launch {
            spotifyAuthRepository.completeAuthorization(data)
        }
    }
}
