package com.accordissimo.scales.domain

enum class Mode(val token: String, val label: String) {
    Major("major", "Majeur"),
    Minor("minor", "Mineur");

    companion object {
        fun fromToken(token: String): Mode = entries.first { it.token == token }
    }
}

enum class Transposition(val token: String, val label: String, val semitoneOffset: Int) {
    C("ut", "Ut", 0),
    BFlat("sib", "Si b", 2),
    EFlat("mib", "Mi b", 9),
    F("fa", "Fa", 7);

    companion object {
        fun fromToken(token: String): Transposition = entries.first { it.token == token }
    }
}

enum class PitchStandard(val hz: Int, val label: String) {
    Hz440(440, "440 Hz"),
    Hz415(415, "415 Hz");

    companion object {
        fun fromHz(hz: Int): PitchStandard = entries.first { it.hz == hz }
    }
}

enum class OctaveCount(val count: Int, val label: String) {
    One(1, "1 octave"),
    Two(2, "2 octaves"),
    Three(3, "3 octaves");

    companion object {
        fun fromCount(count: Int): OctaveCount = entries.first { it.count == count }
    }
}

enum class Register(val token: String, val label: String) {
    Low("low", "Grave"),
    Medium("medium", "Medium"),
    High("high", "Aigu");

    companion object {
        fun fromToken(token: String): Register = entries.first { it.token == token }
    }
}

enum class Arrangement(val token: String, val label: String) {
    InstrumentAndPiano("instrument_piano", "Instrument + piano"),
    PianoOnly("piano", "Piano seul");

    companion object {
        fun fromToken(token: String): Arrangement = entries.first { it.token == token }
    }
}

enum class ScaleKey(
    val token: String,
    val label: String,
    val concertSemitone: Int,
    val signature: String,
) {
    C("c", "Do", 0, "0"),
    G("g", "Sol", 7, "1#"),
    D("d", "Re", 2, "2#"),
    A("a", "La", 9, "3#"),
    E("e", "Mi", 4, "4#"),
    B("b", "Si", 11, "5#"),
    FSharp("fsharp", "Fa #", 6, "6#"),
    CSharp("csharp", "Do #", 1, "7#"),
    F("f", "Fa", 5, "1b"),
    BFlat("bflat", "Si b", 10, "2b"),
    EFlat("eflat", "Mi b", 3, "3b"),
    AFlat("aflat", "La b", 8, "4b"),
    DFlat("dflat", "Re b", 1, "5b"),
    GFlat("gflat", "Sol b", 6, "6b"),
    CFlat("cflat", "Do b", 11, "7b");

    companion object {
        fun fromToken(token: String): ScaleKey = entries.first { it.token == token }
    }
}

enum class TimeSignature(val beats: Int, val label: String) {
    One(1, "1 temps"),
    Two(2, "2 temps"),
    Three(3, "3 temps"),
    Four(4, "4 temps");

    companion object {
        fun fromBeats(beats: Int): TimeSignature = entries.first { it.beats == beats }
    }
}

data class Tempo(val bpm: Int) {
    init {
        require(bpm in 30..240) { "Tempo must stay in a musical practice range." }
    }

    val label: String = "$bpm bpm"

    companion object {
        val presets = listOf(44, 66, 88, 110, 132, 152).map(::Tempo)
    }
}

data class ScaleExercise(
    val key: ScaleKey,
    val mode: Mode,
    val transposition: Transposition,
    val pitchStandard: PitchStandard,
    val octaveCount: OctaveCount,
    val register: Register,
    val tempo: Tempo,
    val arrangement: Arrangement,
    val assetPath: String,
    val durationMs: Long,
    val signature: String,
    val leadingTone: String,
    val melodicAscending: String,
    val melodicDescending: String,
) {
    val id: String = listOf(
        transposition.token,
        pitchStandard.hz,
        mode.token,
        key.token,
        octaveCount.count,
        register.token,
        tempo.bpm,
        arrangement.token
    ).joinToString(":")

    val title: String = "${key.label} ${mode.label}"
}

data class FavoriteScale(val exerciseId: String)

fun scaleExerciseFromSelection(
    key: ScaleKey,
    mode: Mode,
    transposition: Transposition,
    pitchStandard: PitchStandard,
    octaveCount: OctaveCount,
    register: Register,
    tempo: Tempo,
    arrangement: Arrangement,
): ScaleExercise {
    return ScaleExercise(
        key = key,
        mode = mode,
        transposition = transposition,
        pitchStandard = pitchStandard,
        octaveCount = octaveCount,
        register = register,
        tempo = tempo,
        arrangement = arrangement,
        assetPath = expectedAssetPath(
            transposition = transposition,
            pitchStandard = pitchStandard,
            mode = mode,
            key = key,
            octaveCount = octaveCount,
            register = register,
            tempo = tempo,
            arrangement = arrangement,
        ),
        durationMs = estimatedDurationMs(octaveCount, tempo),
        signature = signatureFor(key, mode),
        leadingTone = spellPitchForDegree(key, degreeIndex = 6, semitoneFromTonic = 11),
        melodicAscending = melodicLineLabel(key, mode, ascending = true, octaveCount = octaveCount),
        melodicDescending = melodicLineLabel(key, mode, ascending = false, octaveCount = octaveCount),
    )
}

fun transposedDisplayKey(key: ScaleKey, transposition: Transposition): String {
    return noteLabel(key.concertSemitone + transposition.semitoneOffset)
}

fun scaleSemitoneSteps(mode: Mode, octaveCount: OctaveCount, ascending: Boolean): List<Int> {
    val octaveRange = 0 until octaveCount.count
    val base = when (mode) {
        Mode.Major -> listOf(0, 2, 4, 5, 7, 9, 11)
        Mode.Minor -> if (ascending) {
            listOf(0, 2, 3, 5, 7, 9, 11)
        } else {
            listOf(0, 2, 3, 5, 7, 8, 10)
        }
    }
    val ascendingSteps = octaveRange.flatMap { octave -> base.map { it + 12 * octave } } +
        (12 * octaveCount.count)
    return if (ascending) ascendingSteps else ascendingSteps.reversed()
}

fun melodicLineLabel(
    key: ScaleKey,
    mode: Mode,
    ascending: Boolean,
    octaveCount: OctaveCount = OctaveCount.One,
): String {
    return scaleDegreeSteps(mode, octaveCount, ascending)
        .map { (degreeIndex, semitone) -> spellPitchForDegree(key, degreeIndex, semitone) }
        .joinToString(" ")
}

fun noteLabel(semitone: Int): String {
    val notes = listOf("Do", "Do #", "Re", "Mi b", "Mi", "Fa", "Fa #", "Sol", "La b", "La", "Si b", "Si")
    return notes[semitone.floorMod(12)]
}

fun signatureFor(key: ScaleKey, mode: Mode): String {
    if (mode == Mode.Major) return key.signature
    val relativeMajorSemitone = (key.concertSemitone + 3).floorMod(12)
    return ScaleKey.entries.firstOrNull { it.concertSemitone == relativeMajorSemitone }?.signature ?: key.signature
}

fun estimatedDurationMs(octaveCount: OctaveCount, tempo: Tempo): Long {
    val notes = (scaleSemitoneSteps(Mode.Major, octaveCount, ascending = true).size * 2) - 1
    return notes * 60_000L / tempo.bpm
}

private fun scaleDegreeSteps(
    mode: Mode,
    octaveCount: OctaveCount,
    ascending: Boolean,
): List<Pair<Int, Int>> {
    val ascendingBase = when (mode) {
        Mode.Major -> listOf(0, 2, 4, 5, 7, 9, 11)
        Mode.Minor -> listOf(0, 2, 3, 5, 7, 9, 11)
    }
    val descendingBase = when (mode) {
        Mode.Major -> ascendingBase
        Mode.Minor -> listOf(0, 2, 3, 5, 7, 8, 10)
    }
    return if (ascending) {
        (0 until octaveCount.count).flatMap { octave ->
            ascendingBase.mapIndexed { degree, semitone -> degree to semitone + 12 * octave }
        } + (0 to 12 * octaveCount.count)
    } else {
        listOf(0 to 12 * octaveCount.count) +
            (octaveCount.count - 1 downTo 0).flatMap { octave ->
                descendingBase.mapIndexed { degree, semitone -> degree to semitone + 12 * octave }
                    .asReversed()
            }
    }
}

private fun spellPitchForDegree(key: ScaleKey, degreeIndex: Int, semitoneFromTonic: Int): String {
    val degreeLetters = listOf("Do", "Re", "Mi", "Fa", "Sol", "La", "Si")
    val naturalSemitones = listOf(0, 2, 4, 5, 7, 9, 11)
    val tonicLetterIndex = tonicLetterIndex(key)
    val letterIndex = (tonicLetterIndex + degreeIndex).floorMod(7)
    val target = (key.concertSemitone + semitoneFromTonic).floorMod(12)
    val natural = naturalSemitones[letterIndex]
    val accidental = (target - natural + 6).floorMod(12) - 6
    return when (accidental) {
        -2 -> "${degreeLetters[letterIndex]} bb"
        -1 -> "${degreeLetters[letterIndex]} b"
        0 -> degreeLetters[letterIndex]
        1 -> "${degreeLetters[letterIndex]} #"
        2 -> "${degreeLetters[letterIndex]} ##"
        else -> noteLabel(target)
    }
}

private fun tonicLetterIndex(key: ScaleKey): Int = when (key) {
    ScaleKey.C, ScaleKey.CSharp, ScaleKey.CFlat -> 0
    ScaleKey.D, ScaleKey.DFlat -> 1
    ScaleKey.E, ScaleKey.EFlat -> 2
    ScaleKey.F, ScaleKey.FSharp -> 3
    ScaleKey.G, ScaleKey.GFlat -> 4
    ScaleKey.A, ScaleKey.AFlat -> 5
    ScaleKey.B, ScaleKey.BFlat -> 6
}

fun expectedAssetPath(
    transposition: Transposition,
    pitchStandard: PitchStandard,
    mode: Mode,
    key: ScaleKey,
    octaveCount: OctaveCount,
    register: Register,
    tempo: Tempo,
    arrangement: Arrangement,
    extension: String = "mp3",
): String = "scales/${transposition.token}/${pitchStandard.hz}/${mode.token}/${key.token}/" +
    "${octaveCount.count}/${register.token}/${tempo.bpm}/${arrangement.token}.$extension"

private fun Int.floorMod(divisor: Int): Int = ((this % divisor) + divisor) % divisor
