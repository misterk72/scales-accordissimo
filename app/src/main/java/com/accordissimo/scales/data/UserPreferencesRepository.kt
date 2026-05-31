package com.accordissimo.scales.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.accordissimo.scales.domain.Arrangement
import com.accordissimo.scales.domain.Mode
import com.accordissimo.scales.domain.OctaveCount
import com.accordissimo.scales.domain.PitchStandard
import com.accordissimo.scales.domain.Register
import com.accordissimo.scales.domain.ScaleKey
import com.accordissimo.scales.domain.Tempo
import com.accordissimo.scales.domain.Transposition
import com.accordissimo.scales.domain.scaleExerciseFromSelection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.scalesDataStore by preferencesDataStore(name = "scales_user_preferences")

data class ScaleSelection(
    val key: ScaleKey = ScaleKey.C,
    val mode: Mode = Mode.Major,
    val transposition: Transposition = Transposition.C,
    val pitchStandard: PitchStandard = PitchStandard.Hz440,
    val octaveCount: OctaveCount = OctaveCount.One,
    val register: Register = Register.Medium,
    val tempo: Tempo = Tempo(88),
    val arrangement: Arrangement = Arrangement.PianoOnly,
    val playFirstNote: Boolean = true,
    val countInBars: Int = 1,
) {
    fun toExercise() = scaleExerciseFromSelection(
        key = key,
        mode = mode,
        transposition = transposition,
        pitchStandard = pitchStandard,
        octaveCount = octaveCount,
        register = register,
        tempo = tempo,
        arrangement = arrangement,
    )
}

class UserPreferencesRepository(private val context: Context) {
    val favorites: Flow<Set<String>> = context.scalesDataStore.data.map { preferences ->
        preferences[FAVORITES].orEmpty()
    }

    val selection: Flow<ScaleSelection> = context.scalesDataStore.data.map { preferences ->
        ScaleSelection(
            key = preferences[KEY]?.let(ScaleKey::fromToken) ?: ScaleKey.C,
            mode = preferences[MODE]?.let(Mode::fromToken) ?: Mode.Major,
            transposition = preferences[TRANSPOSITION]?.let(Transposition::fromToken) ?: Transposition.C,
            pitchStandard = preferences[PITCH]?.toIntOrNull()?.let(PitchStandard::fromHz) ?: PitchStandard.Hz440,
            octaveCount = preferences[OCTAVES]?.toIntOrNull()?.let(OctaveCount::fromCount) ?: OctaveCount.One,
            register = preferences[REGISTER]?.let(Register::fromToken) ?: Register.Medium,
            tempo = preferences[TEMPO]?.toIntOrNull()?.let(::Tempo) ?: Tempo(88),
            arrangement = preferences[ARRANGEMENT]?.let(Arrangement::fromToken) ?: Arrangement.PianoOnly,
            playFirstNote = preferences[PLAY_FIRST_NOTE]?.toBooleanStrictOrNull() ?: true,
            countInBars = preferences[COUNT_IN_BARS]?.toIntOrNull()?.coerceIn(1, 2) ?: 1,
        )
    }

    suspend fun saveSelection(selection: ScaleSelection) {
        context.scalesDataStore.edit { preferences ->
            preferences[KEY] = selection.key.token
            preferences[MODE] = selection.mode.token
            preferences[TRANSPOSITION] = selection.transposition.token
            preferences[PITCH] = selection.pitchStandard.hz.toString()
            preferences[OCTAVES] = selection.octaveCount.count.toString()
            preferences[REGISTER] = selection.register.token
            preferences[TEMPO] = selection.tempo.bpm.toString()
            preferences[ARRANGEMENT] = selection.arrangement.token
            preferences[PLAY_FIRST_NOTE] = selection.playFirstNote.toString()
            preferences[COUNT_IN_BARS] = selection.countInBars.toString()
        }
    }

    suspend fun toggleFavorite(exerciseId: String) {
        context.scalesDataStore.edit { preferences ->
            val current = preferences[FAVORITES].orEmpty()
            preferences[FAVORITES] = if (exerciseId in current) current - exerciseId else current + exerciseId
        }
    }

    private companion object {
        val KEY = stringPreferencesKey("key")
        val MODE = stringPreferencesKey("mode")
        val TRANSPOSITION = stringPreferencesKey("transposition")
        val PITCH = stringPreferencesKey("pitch")
        val OCTAVES = stringPreferencesKey("octaves")
        val REGISTER = stringPreferencesKey("register")
        val TEMPO = stringPreferencesKey("tempo")
        val ARRANGEMENT = stringPreferencesKey("arrangement")
        val PLAY_FIRST_NOTE = stringPreferencesKey("play_first_note")
        val COUNT_IN_BARS = stringPreferencesKey("count_in_bars")
        val FAVORITES = stringSetPreferencesKey("favorites")
    }
}
