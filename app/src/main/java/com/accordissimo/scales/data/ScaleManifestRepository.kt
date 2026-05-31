package com.accordissimo.scales.data

import android.content.Context
import com.accordissimo.scales.domain.Arrangement
import com.accordissimo.scales.domain.Mode
import com.accordissimo.scales.domain.OctaveCount
import com.accordissimo.scales.domain.PitchStandard
import com.accordissimo.scales.domain.Register
import com.accordissimo.scales.domain.ScaleExercise
import com.accordissimo.scales.domain.ScaleKey
import com.accordissimo.scales.domain.Tempo
import com.accordissimo.scales.domain.Transposition
import org.json.JSONArray

class ScaleManifestRepository(private val context: Context) {
    fun loadExercises(): List<ScaleExercise> {
        val json = context.assets.open("scales_manifest.json").bufferedReader().use { it.readText() }
        val array = JSONArray(json)
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(
                    ScaleExercise(
                        key = ScaleKey.fromToken(item.getString("key")),
                        mode = Mode.fromToken(item.getString("mode")),
                        transposition = Transposition.fromToken(item.getString("transposition")),
                        pitchStandard = PitchStandard.fromHz(item.getInt("pitchHz")),
                        octaveCount = OctaveCount.fromCount(item.getInt("octaves")),
                        register = Register.fromToken(item.getString("register")),
                        tempo = Tempo(item.getInt("tempoBpm")),
                        arrangement = Arrangement.fromToken(item.getString("arrangement")),
                        assetPath = item.getString("assetPath"),
                        durationMs = item.optLong("durationMs", 0L),
                        signature = item.optString("signature", ""),
                        leadingTone = item.optString("leadingTone", ""),
                        melodicAscending = item.optString("melodicAscending", ""),
                        melodicDescending = item.optString("melodicDescending", ""),
                    )
                )
            }
        }
    }

    fun assetExists(assetPath: String): Boolean = runCatching {
        context.assets.open(assetPath).close()
    }.isSuccess
}
