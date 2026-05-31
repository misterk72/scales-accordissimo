package com.accordissimo.scales

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.DisposableEffect
import com.accordissimo.scales.audio.MetronomeEngine
import com.accordissimo.scales.audio.ScaleAudioController
import com.accordissimo.scales.data.ScaleManifestRepository
import com.accordissimo.scales.data.UserPreferencesRepository
import com.accordissimo.scales.ui.ScalesApp

class MainActivity : ComponentActivity() {
    private lateinit var audioController: ScaleAudioController
    private val metronomeEngine = MetronomeEngine()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        audioController = ScaleAudioController(this)
        setContent {
            DisposableEffect(Unit) {
                onDispose {
                    metronomeEngine.stop()
                    audioController.release()
                }
            }
            ScalesApp(
                manifestRepository = ScaleManifestRepository(this),
                preferencesRepository = UserPreferencesRepository(this),
                audioController = audioController,
                metronomeEngine = metronomeEngine,
            )
        }
    }
}
