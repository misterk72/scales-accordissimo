package com.accordissimo.scales.ui

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.accordissimo.scales.audio.MetronomeEngine
import com.accordissimo.scales.audio.ScaleAudioController
import com.accordissimo.scales.data.ScaleManifestRepository
import com.accordissimo.scales.data.ScaleSelection
import com.accordissimo.scales.data.UserPreferencesRepository
import com.accordissimo.scales.domain.Mode
import com.accordissimo.scales.domain.OctaveCount
import com.accordissimo.scales.domain.PitchStandard
import com.accordissimo.scales.domain.Register
import com.accordissimo.scales.domain.ScaleExercise
import com.accordissimo.scales.domain.ScaleKey
import com.accordissimo.scales.domain.Tempo
import com.accordissimo.scales.domain.TimeSignature
import com.accordissimo.scales.domain.Transposition
import com.accordissimo.scales.domain.signatureFor
import com.accordissimo.scales.domain.transposedDisplayKey
import com.accordissimo.scales.domain.Arrangement as ScaleArrangement
import kotlinx.coroutines.launch

private val Accent = Color(0xFF1BB3B0)
private val Background = Color(0xFF0A0A0A)
private val SurfaceDark = Color(0xFF171717)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ScalesApp(
    manifestRepository: ScaleManifestRepository,
    preferencesRepository: UserPreferencesRepository,
    audioController: ScaleAudioController,
    metronomeEngine: MetronomeEngine,
) {
    val exercises = remember { manifestRepository.loadExercises() }
    val storedSelection by preferencesRepository.selection.collectAsState(initial = ScaleSelection())
    val favorites by preferencesRepository.favorites.collectAsState(initial = emptySet())
    var selection by remember { mutableStateOf(storedSelection) }
    var tab by remember { mutableStateOf(Tab.Home) }
    var isMetronomeRunning by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(storedSelection) {
        selection = storedSelection
    }

    KeepScreenOn(isMetronomeRunning)

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent,
            secondary = Accent,
            background = Background,
            surface = SurfaceDark,
            onPrimary = Color.Black,
            onBackground = Color.White,
            onSurface = Color.White,
        )
    ) {
        Scaffold(
            containerColor = Background,
            bottomBar = {
                NavigationBar(containerColor = SurfaceDark) {
                    Tab.entries.forEach { item ->
                        NavigationBarItem(
                            selected = tab == item,
                            onClick = { tab = item },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                        )
                    }
                }
            }
        ) { padding ->
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                color = Background,
            ) {
                when (tab) {
                    Tab.Home -> HomeScreen()
                    Tab.Scales -> ScalesScreen(
                        exercises = exercises,
                        selection = selection,
                        favorites = favorites,
                        assetExists = manifestRepository::assetExists,
                        onSelectionChange = { updated ->
                            selection = updated
                            scope.launch { preferencesRepository.saveSelection(updated) }
                        },
                        onToggleFavorite = { exercise ->
                            scope.launch { preferencesRepository.toggleFavorite(exercise.id) }
                        },
                        onPlay = { exercise ->
                            audioController.playGeneratedMidi(
                                exercise = exercise,
                                playFirstNote = selection.playFirstNote,
                                countInBars = selection.countInBars,
                            )
                        },
                        onPause = audioController::pause,
                        onStop = audioController::stop,
                    )
                    Tab.Metronome -> MetronomeScreen(
                        onStart = { bpm, signature ->
                            metronomeEngine.start(bpm, signature)
                            isMetronomeRunning = true
                        },
                        onStop = {
                            metronomeEngine.stop()
                            isMetronomeRunning = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun KeepScreenOn(enabled: Boolean) {
    val activity = LocalContext.current as? Activity
    DisposableEffect(enabled) {
        if (enabled) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}

@Composable
private fun HomeScreen() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                "Scales - Accordissimo",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Echauffez-vous, entrainez-vous, pratiquez vos gammes avec Accordissimo.",
                color = Color.White.copy(alpha = 0.72f),
            )
        }
        item {
            SectionCard("Fonctionnalites") {
                val features = listOf(
                    "Accompagnements des 30 gammes majeures et mineures",
                    "Transposition : ut, si b, mi b, fa",
                    "Diapason en ut : 440 ou 415 Hz",
                    "Tonalite, nombre d'octaves et tessiture",
                    "Tempos : 44, 66, 88, 110, 132, 152 bpm",
                    "Arrangement instrument + piano ou piano seul",
                    "Premiere note, decompte et informations de gamme",
                    "Favoris et filtre par armure",
                )
                features.forEach { feature ->
                    Text("• $feature", color = Color.White.copy(alpha = 0.82f))
                }
            }
        }
        item {
            SectionCard("Acces") {
                Text("Application Android native hors ligne pour smartphone et tablette.")
            }
        }
        item {
            SectionCard("Contact") {
                Text("Suggestions ou difficultes d'utilisation : contactez Accordissimo.")
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ElevatedButton(onClick = {}) {
                        Icon(Icons.Default.Phone, contentDescription = null)
                        Text(" Appeler")
                    }
                    ElevatedButton(onClick = {}) {
                        Icon(Icons.Default.Email, contentDescription = null)
                        Text(" Email")
                    }
                }
            }
        }
    }
}

@Composable
private fun ScalesScreen(
    exercises: List<ScaleExercise>,
    selection: ScaleSelection,
    favorites: Set<String>,
    assetExists: (String) -> Boolean,
    onSelectionChange: (ScaleSelection) -> Unit,
    onToggleFavorite: (ScaleExercise) -> Unit,
    onPlay: (ScaleExercise) -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
) {
    val matching = exercises.firstOrNull { exercise ->
        exercise.key == selection.key &&
            exercise.mode == selection.mode &&
            exercise.transposition == selection.transposition &&
            exercise.pitchStandard == selection.pitchStandard &&
            exercise.octaveCount == selection.octaveCount &&
            exercise.register == selection.register &&
            exercise.tempo.bpm == selection.tempo.bpm &&
            exercise.arrangement == selection.arrangement
    }
    val selectedExercise = matching ?: selection.toExercise()
    val selectedAssetExists = assetExists(selectedExercise.assetPath)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Gammes", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        SectionCard("Selection") {
            KeySelector(selection.key, selection.mode) {
                onSelectionChange(selection.copy(key = it))
            }
            Selector("Mode", Mode.entries, selection.mode, { it.label }) {
                onSelectionChange(selection.copy(mode = it))
            }
            Selector("Transposition", Transposition.entries, selection.transposition, { it.label }) {
                onSelectionChange(selection.copy(transposition = it))
            }
            Selector("Diapason", PitchStandard.entries, selection.pitchStandard, { it.label }) {
                onSelectionChange(selection.copy(pitchStandard = it))
            }
            Selector("Octaves", OctaveCount.entries, selection.octaveCount, { it.label }) {
                onSelectionChange(selection.copy(octaveCount = it))
            }
            Selector("Tessiture", Register.entries, selection.register, { it.label }) {
                onSelectionChange(selection.copy(register = it))
            }
            Selector("Tempo", Tempo.presets, selection.tempo, { it.label }) {
                onSelectionChange(selection.copy(tempo = it))
            }
            Selector("Arrangement", ScaleArrangement.entries, selection.arrangement, { it.label }) {
                onSelectionChange(selection.copy(arrangement = it))
            }
        }

        SectionCard("Options") {
            ToggleRow("Ecouter la premiere note", selection.playFirstNote) {
                onSelectionChange(selection.copy(playFirstNote = it))
            }
            Selector("Decompte", listOf(1, 2), selection.countInBars, { "$it mesure(s)" }) {
                onSelectionChange(selection.copy(countInBars = it))
            }
        }

        SectionCard("Lecture") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(selectedExercise.title, style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Sonne ${transposedDisplayKey(selectedExercise.key, selectedExercise.transposition)} en ${selectedExercise.transposition.label}",
                        color = Color.White.copy(alpha = 0.68f),
                    )
                }
                IconButton(onClick = { onToggleFavorite(selectedExercise) }) {
                    Icon(
                        if (selectedExercise.id in favorites) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favori",
                        tint = Accent,
                    )
                }
            }
            Text("Armure : ${selectedExercise.signature}")
            Text("Note sensible : ${selectedExercise.leadingTone}")
            Text("Melodique ascendant : ${selectedExercise.melodicAscending}")
            Text("Melodique descendant : ${selectedExercise.melodicDescending}")
            Text(
                if (selectedAssetExists) {
                    "Asset local disponible : ${selectedExercise.assetPath}"
                } else {
                    "Asset officiel manquant : ${selectedExercise.assetPath}. Lecture MIDI generee avec accords."
                },
                color = if (selectedAssetExists) Color.White.copy(alpha = 0.68f) else Color(0xFFFFD666),
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { onPlay(selectedExercise) }) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Text(" Lire")
                }
                ElevatedButton(onClick = onPause) {
                    Icon(Icons.Default.Pause, contentDescription = null)
                }
                ElevatedButton(onClick = onStop) {
                    Icon(Icons.Default.Stop, contentDescription = null)
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun MetronomeScreen(
    onStart: (Int, TimeSignature) -> Unit,
    onStop: () -> Unit,
) {
    var bpm by remember { mutableIntStateOf(88) }
    var signature by remember { mutableStateOf(TimeSignature.Four) }
    var running by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Metronome", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        SectionCard("Mesure") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TimeSignature.entries.toList().forEach { option ->
                    FilterChip(
                        selected = signature == option,
                        onClick = { signature = option },
                        label = { Text(option.label) },
                    )
                }
            }
        }
        SectionCard("Tempo") {
            Text("$bpm bpm", style = MaterialTheme.typography.titleLarge)
            Slider(
                value = bpm.toFloat(),
                onValueChange = { bpm = it.toInt().coerceIn(30, 240) },
                valueRange = 30f..240f,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tempo.presets.forEach { preset ->
                    FilterChip(
                        selected = bpm == preset.bpm,
                        onClick = { bpm = preset.bpm },
                        label = { Text(preset.label) },
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = {
                    running = true
                    onStart(bpm, signature)
                },
                enabled = !running,
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Text(" Demarrer")
            }
            ElevatedButton(
                onClick = {
                    running = false
                    onStop()
                },
                enabled = running,
            ) {
                Icon(Icons.Default.Stop, contentDescription = null)
                Text(" Arreter")
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun KeySelector(selected: ScaleKey, mode: Mode, onSelected: (ScaleKey) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Tonalite", color = Color.White.copy(alpha = 0.68f))
        listOf("0", "1#", "2#", "3#", "4#", "5#", "6#", "7#", "1b", "2b", "3b", "4b", "5b", "6b", "7b").forEach { signature ->
            val keys = ScaleKey.entries.filter { signatureFor(it, mode) == signature }
            if (keys.isNotEmpty()) {
                Text("Armure $signature", color = Color.White.copy(alpha = 0.52f))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    keys.forEach { key ->
                        FilterChip(
                            selected = selected == key,
                            onClick = { onSelected(key) },
                            label = { Text(key.label) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun <T> Selector(
    label: String,
    options: List<T>,
    selected: T,
    optionLabel: (T) -> String,
    onSelected: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = Color.White.copy(alpha = 0.68f))
        TextButton(onClick = { expanded = true }) {
            Text(optionLabel(selected), color = Color.White)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        expanded = false
                        onSelected(option)
                    },
                )
            }
        }
    }
}

@Composable
private fun MissingContent(message: String) {
    Text(message, color = Color(0xFFFFD666), style = MaterialTheme.typography.bodyMedium)
}

private enum class Tab(val label: String, val icon: ImageVector) {
    Home("Accueil", Icons.Default.Home),
    Scales("Gammes", Icons.Default.MusicNote),
    Metronome("Metronome", Icons.Default.Timer),
}
