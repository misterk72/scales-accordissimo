package com.accordissimo.scales.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ScaleModelsTest {
    @Test
    fun transposesConcertPitchForBFlatInstrument() {
        assertEquals("Re", transposedDisplayKey(ScaleKey.C, Transposition.BFlat))
    }

    @Test
    fun buildsExpectedAssetPath() {
        val path = expectedAssetPath(
            transposition = Transposition.C,
            pitchStandard = PitchStandard.Hz440,
            mode = Mode.Major,
            key = ScaleKey.C,
            octaveCount = OctaveCount.One,
            register = Register.Medium,
            tempo = Tempo(88),
            arrangement = Arrangement.PianoOnly,
        )

        assertEquals("scales/ut/440/major/c/1/medium/88/piano.mp3", path)
    }

    @Test
    fun keepsTempoInPracticeRange() {
        assertEquals("132 bpm", Tempo(132).label)
    }

    @Test
    fun buildsMelodicMinorLines() {
        assertEquals(
            "La Si Do Re Mi Fa # Sol # La",
            melodicLineLabel(ScaleKey.A, Mode.Minor, ascending = true),
        )
        assertEquals(
            "La Sol Fa Mi Re Do Si La",
            melodicLineLabel(ScaleKey.A, Mode.Minor, ascending = false),
        )
    }

    @Test
    fun derivesMinorKeySignatureFromRelativeMajor() {
        assertEquals("0", signatureFor(ScaleKey.A, Mode.Minor))
        assertEquals("1#", signatureFor(ScaleKey.E, Mode.Minor))
    }

    @Test
    fun createsFallbackExerciseForAnySelection() {
        val exercise = scaleExerciseFromSelection(
            key = ScaleKey.EFlat,
            mode = Mode.Major,
            transposition = Transposition.BFlat,
            pitchStandard = PitchStandard.Hz440,
            octaveCount = OctaveCount.Two,
            register = Register.Low,
            tempo = Tempo(66),
            arrangement = Arrangement.InstrumentAndPiano,
        )

        assertEquals("Mi b Majeur", exercise.title)
        assertEquals("3b", exercise.signature)
        assertEquals("scales/sib/440/major/eflat/2/low/66/instrument_piano.mp3", exercise.assetPath)
    }
}
