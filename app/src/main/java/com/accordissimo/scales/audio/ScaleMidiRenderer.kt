package com.accordissimo.scales.audio

import com.accordissimo.scales.domain.Arrangement
import com.accordissimo.scales.domain.Mode
import com.accordissimo.scales.domain.Register
import com.accordissimo.scales.domain.ScaleExercise
import com.accordissimo.scales.domain.scaleSemitoneSteps
import java.io.ByteArrayOutputStream
import kotlin.math.ln
import kotlin.math.roundToInt

object ScaleMidiRenderer {
    private const val PulsesPerQuarter = 480
    private const val MelodyChannel = 0
    private const val PianoChannel = 1
    private const val PercussionChannel = 9

    fun render(exercise: ScaleExercise, playFirstNote: Boolean, countInBars: Int): ByteArray {
        val events = mutableListOf<MidiEvent>()
        val beatTicks = PulsesPerQuarter
        val microsecondsPerQuarter = 60_000_000 / exercise.tempo.bpm
        val tonicMidi = registerMidi(exercise.register) + exercise.key.concertSemitone
        val melodyProgram = if (exercise.arrangement == Arrangement.InstrumentAndPiano) 21 else 0

        events += MidiEvent(0, 0, bytes(0xFF, 0x51, 0x03) + int24(microsecondsPerQuarter))
        events += MidiEvent(0, 1, bytes(0xFF, 0x58, 0x04, 0x04, 0x02, 0x18, 0x08))
        events += MidiEvent(0, 2, bytes(0xC0 or MelodyChannel, melodyProgram))
        events += MidiEvent(0, 2, bytes(0xC0 or PianoChannel, 0))
        addPitchBend(events, MelodyChannel, exercise.pitchStandard.hz)
        addPitchBend(events, PianoChannel, exercise.pitchStandard.hz)

        var cursor = 0
        if (playFirstNote) {
            addNote(events, cursor, beatTicks, MelodyChannel, tonicMidi, 82)
            cursor += beatTicks * 2
        }

        repeat(countInBars.coerceIn(1, 2) * 4) { beat ->
            addNote(
                events = events,
                startTick = cursor,
                durationTicks = beatTicks / 6,
                channel = PercussionChannel,
                note = if (beat % 4 == 0) 76 else 77,
                velocity = if (beat % 4 == 0) 112 else 88,
            )
            cursor += beatTicks
        }

        val ascending = scaleSemitoneSteps(exercise.mode, exercise.octaveCount, ascending = true)
        val descending = scaleSemitoneSteps(exercise.mode, exercise.octaveCount, ascending = false).drop(1)
        val scaleSteps = ascending + descending
        val chordProgression = chordProgression(exercise.mode)

        scaleSteps.forEachIndexed { index, step ->
            if (index % 4 == 0) {
                val chord = chordProgression[(index / 4).coerceAtMost(chordProgression.lastIndex)]
                addChord(
                    events = events,
                    startTick = cursor,
                    durationTicks = beatTicks * 4,
                    rootMidi = tonicMidi - 12,
                    intervals = chord,
                    velocity = if (exercise.arrangement == Arrangement.PianoOnly) 66 else 58,
                )
            }
            addNote(
                events = events,
                startTick = cursor,
                durationTicks = (beatTicks * 0.92).roundToInt(),
                channel = MelodyChannel,
                note = tonicMidi + step,
                velocity = if (exercise.arrangement == Arrangement.InstrumentAndPiano) 92 else 78,
            )
            cursor += beatTicks
        }

        val track = writeTrack(events)
        return ByteArrayOutputStream().apply {
            writeAscii("MThd")
            writeInt32(6)
            writeInt16(0)
            writeInt16(1)
            writeInt16(PulsesPerQuarter)
            writeAscii("MTrk")
            writeInt32(track.size)
            write(track)
        }.toByteArray()
    }

    private fun addChord(
        events: MutableList<MidiEvent>,
        startTick: Int,
        durationTicks: Int,
        rootMidi: Int,
        intervals: List<Int>,
        velocity: Int,
    ) {
        addNote(events, startTick, durationTicks, PianoChannel, rootMidi - 12 + intervals.first(), 58)
        intervals.forEach { interval ->
            addNote(events, startTick, durationTicks, PianoChannel, rootMidi + interval, velocity)
        }
    }

    private fun addNote(
        events: MutableList<MidiEvent>,
        startTick: Int,
        durationTicks: Int,
        channel: Int,
        note: Int,
        velocity: Int,
    ) {
        events += MidiEvent(startTick, 20, bytes(0x90 or channel, note.coerceIn(0, 127), velocity.coerceIn(1, 127)))
        events += MidiEvent(startTick + durationTicks, 10, bytes(0x80 or channel, note.coerceIn(0, 127), 0))
    }

    private fun addPitchBend(events: MutableList<MidiEvent>, channel: Int, pitchHz: Int) {
        val semitoneDelta = 12.0 * (ln(pitchHz / 440.0) / ln(2.0))
        val value = (8192 + semitoneDelta / 2.0 * 8192).roundToInt().coerceIn(0, 16_383)
        events += MidiEvent(0, 3, bytes(0xE0 or channel, value and 0x7F, value shr 7))
    }

    private fun writeTrack(events: List<MidiEvent>): ByteArray {
        val output = ByteArrayOutputStream()
        var previousTick = 0
        events.sortedWith(compareBy<MidiEvent> { it.tick }.thenBy { it.order }).forEach { event ->
            output.writeVariableLength(event.tick - previousTick)
            output.write(event.data)
            previousTick = event.tick
        }
        output.writeVariableLength(0)
        output.write(bytes(0xFF, 0x2F, 0x00))
        return output.toByteArray()
    }

    private fun registerMidi(register: Register): Int = when (register) {
        Register.Low -> 48
        Register.Medium -> 60
        Register.High -> 72
    }

    private fun chordProgression(mode: Mode): List<List<Int>> = when (mode) {
        Mode.Major -> listOf(
            listOf(0, 4, 7),
            listOf(5, 9, 12),
            listOf(7, 11, 14),
            listOf(0, 4, 7),
        )
        Mode.Minor -> listOf(
            listOf(0, 3, 7),
            listOf(5, 8, 12),
            listOf(7, 11, 14),
            listOf(0, 3, 7),
        )
    }

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { index -> values[index].toByte() }

    private fun int24(value: Int): ByteArray = bytes((value shr 16) and 0xFF, (value shr 8) and 0xFF, value and 0xFF)

    private fun ByteArrayOutputStream.writeAscii(value: String) {
        write(value.toByteArray(Charsets.US_ASCII))
    }

    private fun ByteArrayOutputStream.writeInt16(value: Int) {
        write(bytes((value shr 8) and 0xFF, value and 0xFF))
    }

    private fun ByteArrayOutputStream.writeInt32(value: Int) {
        write(bytes((value shr 24) and 0xFF, (value shr 16) and 0xFF, (value shr 8) and 0xFF, value and 0xFF))
    }

    private fun ByteArrayOutputStream.writeVariableLength(value: Int) {
        var buffer = value and 0x7F
        var remaining = value ushr 7
        while (remaining > 0) {
            buffer = (buffer shl 8) or ((remaining and 0x7F) or 0x80)
            remaining = remaining ushr 7
        }
        while (true) {
            write(buffer and 0xFF)
            if (buffer and 0x80 != 0) {
                buffer = buffer ushr 8
            } else {
                break
            }
        }
    }

    private data class MidiEvent(val tick: Int, val order: Int, val data: ByteArray)
}
