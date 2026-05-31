package com.accordissimo.scales.audio

import com.accordissimo.scales.domain.Arrangement
import com.accordissimo.scales.domain.Mode
import com.accordissimo.scales.domain.OctaveCount
import com.accordissimo.scales.domain.PitchStandard
import com.accordissimo.scales.domain.Register
import com.accordissimo.scales.domain.ScaleKey
import com.accordissimo.scales.domain.Tempo
import com.accordissimo.scales.domain.Transposition
import com.accordissimo.scales.domain.scaleExerciseFromSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScaleMidiRendererTest {
    @Test
    fun rendersStandardMidiFile() {
        val midi = ScaleMidiRenderer.render(
            exercise = scaleExerciseFromSelection(
                key = ScaleKey.C,
                mode = Mode.Major,
                transposition = Transposition.C,
                pitchStandard = PitchStandard.Hz440,
                octaveCount = OctaveCount.One,
                register = Register.Medium,
                tempo = Tempo(88),
                arrangement = Arrangement.InstrumentAndPiano,
            ),
            playFirstNote = true,
            countInBars = 1,
        )

        assertEquals("MThd", midi.copyOfRange(0, 4).toString(Charsets.US_ASCII))
        assertEquals("MTrk", midi.copyOfRange(14, 18).toString(Charsets.US_ASCII))
        assertTrue(midi.size > 200)
        assertTrue(midi.containsSequence(byteArrayOf(0xC0.toByte(), 21)))
        assertTrue(midi.containsSequence(byteArrayOf(0x99.toByte(), 76, 112.toByte())))
    }

    private fun ByteArray.containsSequence(sequence: ByteArray): Boolean {
        return indices.any { start ->
            start + sequence.size <= size &&
                sequence.indices.all { offset -> this[start + offset] == sequence[offset] }
        }
    }
}
