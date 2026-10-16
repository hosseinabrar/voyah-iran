package com.abrar.voyahiran.core.bridge

import android.os.Bundle
import com.abrar.voyahiran.feature.dow.AmbientSnapshot
import com.abrar.voyahiran.feature.dow.DowContract

internal object DowAmbientBundle {
    // OEM assembles a shared lighting packet; keep all warning controls explicit so
    // a pulse cannot reintroduce pre-warning values from its not-yet-updated cache.
    fun warning(level: Int = 10): Map<String, Int> {
        require(level == 0 || level == 10)
        return linkedMapOf("SWITCH" to if (level == 0) 1 else 2, "MUSICAL_RHYTHM_SWITCH" to 1,
            "COLOR" to DowContract.RED, "BRIGHTNESS_LEVEL" to 10)
    }

    fun restore(s: AmbientSnapshot): Map<String, Int> {
        require(s.valid())
        return linkedMapOf("SWITCH" to s.power, "MUSICAL_RHYTHM_SWITCH" to s.music,
            "COLOR" to s.color, "BRIGHTNESS_LEVEL" to s.brightness)
    }

    fun create(values: Map<String, Int>, resolve: (String) -> String): Bundle {
        require(values.isNotEmpty())
        return Bundle().apply {
            values.forEach { (suffix, value) ->
                val range = when (suffix) {
                    "SWITCH", "MUSICAL_RHYTHM_SWITCH" -> 1..2
                    "COLOR" -> 1..64
                    "BRIGHTNESS_LEVEL" -> 1..10
                    else -> error("Unsupported ambient bundle field: $suffix")
                }
                require(value in range)
                putInt(resolve(suffix), value)
            }
        }
    }
}
