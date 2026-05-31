package com.accordissimo.scales.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.MediaPlayer
import android.media.AudioTrack
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.accordissimo.scales.domain.Arrangement
import com.accordissimo.scales.domain.Mode
import com.accordissimo.scales.domain.Register
import com.accordissimo.scales.domain.ScaleExercise
import com.accordissimo.scales.domain.scaleSemitoneSteps
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import java.io.File

class ScaleAudioController(context: Context) {
    private val appContext = context.applicationContext
    private val player = ExoPlayer.Builder(context).build()
    private val generatedMidiFile = File(appContext.cacheDir, "generated-scale.mid")
    private var midiPlayer: MediaPlayer? = null
    private var synthTrack: AudioTrack? = null
    private var synthThread: Thread? = null

    fun play(exercise: ScaleExercise) {
        stopMidi()
        stopSynth()
        player.setMediaItem(MediaItem.fromUri("asset:///${exercise.assetPath}"))
        player.prepare()
        player.play()
    }

    fun playSynthesized(exercise: ScaleExercise, playFirstNote: Boolean, countInBars: Int) {
        stop()
        val pcm = ScaleSynthesizer.render(exercise, playFirstNote, countInBars)
        synthThread = thread(name = "scale-synth-audio") {
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(ScaleSynthesizer.SampleRate)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(pcm.size * Short.SIZE_BYTES)
                .build()
            synthTrack = track
            track.write(pcm, 0, pcm.size)
            track.play()
        }
    }

    fun playGeneratedMidi(exercise: ScaleExercise, playFirstNote: Boolean, countInBars: Int) {
        stop()
        val midiBytes = ScaleMidiRenderer.render(exercise, playFirstNote, countInBars)
        generatedMidiFile.writeBytes(midiBytes)
        runCatching {
            midiPlayer = MediaPlayer().apply {
                setDataSource(generatedMidiFile.absolutePath)
                setOnCompletionListener { stopMidi() }
                prepare()
                start()
            }
        }.onFailure {
            stopMidi()
            playSynthesized(exercise, playFirstNote, countInBars)
        }
    }

    fun pause() {
        player.pause()
        midiPlayer?.pause()
        synthTrack?.pause()
    }

    fun stop() {
        player.stop()
        player.clearMediaItems()
        stopMidi()
        stopSynth()
    }

    fun release() {
        stopMidi()
        stopSynth()
        player.release()
    }

    private fun stopMidi() {
        midiPlayer?.run {
            runCatching { stop() }
            release()
        }
        midiPlayer = null
    }

    private fun stopSynth() {
        synthThread?.interrupt()
        synthThread = null
        synthTrack?.run {
            runCatching { stop() }
            release()
        }
        synthTrack = null
    }
}

private object ScaleSynthesizer {
    const val SampleRate = 44_100
    private const val ClickHz = 1_760.0

    fun render(exercise: ScaleExercise, playFirstNote: Boolean, countInBars: Int): ShortArray {
        val beatSeconds = 60.0 / exercise.tempo.bpm
        val ascending = scaleSemitoneSteps(exercise.mode, exercise.octaveCount, ascending = true)
        val descending = scaleSemitoneSteps(exercise.mode, exercise.octaveCount, ascending = false).drop(1)
        val scaleSteps = ascending + descending
        val introBeats = (if (playFirstNote) 2 else 0) + countInBars.coerceIn(1, 2) * 4
        val tailBeats = 2
        val totalSeconds = (introBeats + scaleSteps.size + tailBeats) * beatSeconds
        val samples = FloatArray((totalSeconds * SampleRate).toInt() + SampleRate)
        val tonicMidi = registerMidi(exercise.register) + exercise.key.concertSemitone

        var cursor = 0.0
        if (playFirstNote) {
            addTone(samples, cursor, beatSeconds * 0.9, tonicMidi, exercise.pitchStandard.hz, 0.30f)
            cursor += beatSeconds * 2
        }

        repeat(countInBars.coerceIn(1, 2) * 4) { beat ->
            addClick(samples, cursor, accented = beat % 4 == 0)
            cursor += beatSeconds
        }

        val chordProgression = chordProgression(exercise.mode)
        scaleSteps.forEachIndexed { index, step ->
            if (index % 4 == 0) {
                val chord = chordProgression[(index / 4).coerceAtMost(chordProgression.lastIndex)]
                addChord(
                    samples = samples,
                    startSeconds = cursor,
                    durationSeconds = beatSeconds * 4.0,
                    rootMidi = tonicMidi - 12,
                    chord = chord,
                    pitchHz = exercise.pitchStandard.hz,
                    arrangement = exercise.arrangement,
                )
            }
            addTone(
                samples = samples,
                startSeconds = cursor,
                durationSeconds = beatSeconds * 0.88,
                midi = tonicMidi + step,
                pitchHz = exercise.pitchStandard.hz,
                volume = if (exercise.arrangement == Arrangement.InstrumentAndPiano) 0.36f else 0.28f,
            )
            cursor += beatSeconds
        }

        return samples.map { sample ->
            (sample.coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
        }.toShortArray()
    }

    private fun addClick(samples: FloatArray, startSeconds: Double, accented: Boolean) {
        addSine(
            samples = samples,
            startSeconds = startSeconds,
            durationSeconds = 0.055,
            frequency = if (accented) ClickHz else 1_250.0,
            volume = if (accented) 0.42f else 0.26f,
            attackSeconds = 0.002,
            releaseSeconds = 0.035,
        )
    }

    private fun addTone(
        samples: FloatArray,
        startSeconds: Double,
        durationSeconds: Double,
        midi: Int,
        pitchHz: Int,
        volume: Float,
    ) {
        val frequency = midiToFrequency(midi, pitchHz)
        addSine(samples, startSeconds, durationSeconds, frequency, volume, 0.015, 0.12)
        addSine(samples, startSeconds, durationSeconds, frequency * 2.0, volume * 0.16f, 0.015, 0.10)
        addSine(samples, startSeconds, durationSeconds, frequency * 3.0, volume * 0.07f, 0.015, 0.08)
    }

    private fun addChord(
        samples: FloatArray,
        startSeconds: Double,
        durationSeconds: Double,
        rootMidi: Int,
        chord: List<Int>,
        pitchHz: Int,
        arrangement: Arrangement,
    ) {
        val chordVolume = if (arrangement == Arrangement.PianoOnly) 0.14f else 0.11f
        chord.forEach { interval ->
            addTone(samples, startSeconds, durationSeconds * 0.96, rootMidi + interval, pitchHz, chordVolume)
        }
        addTone(samples, startSeconds, durationSeconds * 0.96, rootMidi - 12 + chord.first(), pitchHz, 0.09f)
    }

    private fun addSine(
        samples: FloatArray,
        startSeconds: Double,
        durationSeconds: Double,
        frequency: Double,
        volume: Float,
        attackSeconds: Double,
        releaseSeconds: Double,
    ) {
        val start = (startSeconds * SampleRate).toInt().coerceAtLeast(0)
        val length = (durationSeconds * SampleRate).toInt().coerceAtLeast(1)
        val end = (start + length).coerceAtMost(samples.size)
        for (sampleIndex in start until end) {
            val local = (sampleIndex - start).toDouble() / SampleRate
            val remaining = durationSeconds - local
            val attack = (local / attackSeconds).coerceIn(0.0, 1.0)
            val release = (remaining / releaseSeconds).coerceIn(0.0, 1.0)
            val decay = exp(-local * 0.55)
            val envelope = attack * release * decay
            samples[sampleIndex] += (sin(2.0 * PI * frequency * local) * volume * envelope).toFloat()
        }
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

    private fun midiToFrequency(midi: Int, pitchHz: Int): Double {
        return pitchHz.toDouble() * 2.0.pow((midi - 69) / 12.0)
    }
}
