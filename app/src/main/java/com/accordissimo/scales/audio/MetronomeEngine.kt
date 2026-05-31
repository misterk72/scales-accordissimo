package com.accordissimo.scales.audio

import android.media.AudioManager
import android.media.ToneGenerator
import com.accordissimo.scales.domain.TimeSignature
import java.util.concurrent.atomic.AtomicBoolean

class MetronomeEngine {
    private val running = AtomicBoolean(false)
    private var worker: Thread? = null

    fun start(bpm: Int, signature: TimeSignature) {
        stop()
        running.set(true)
        worker = Thread {
            val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 80)
            var beat = 0
            try {
                while (running.get()) {
                    val toneType = if (beat % signature.beats == 0) {
                        ToneGenerator.TONE_PROP_ACK
                    } else {
                        ToneGenerator.TONE_PROP_BEEP
                    }
                    tone.startTone(toneType, 70)
                    Thread.sleep((60_000L / bpm).coerceAtLeast(120L))
                    beat += 1
                }
            } finally {
                tone.release()
            }
        }.also {
            it.name = "scales-metronome"
            it.isDaemon = true
            it.start()
        }
    }

    fun stop() {
        running.set(false)
        worker?.interrupt()
        worker = null
    }
}
