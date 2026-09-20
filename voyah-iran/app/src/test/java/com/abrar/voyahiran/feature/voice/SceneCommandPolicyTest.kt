package com.abrar.voyahiran.feature.voice

import org.junit.Assert.*
import org.junit.Test

class SceneCommandPolicyTest {
    @Test fun campLaunchIncludesFactoryAutoStartBoolean() {
        val args = SceneCommandPolicy.launch("camp")
        assertTrue(args.windowed(3).contains(listOf("--ez", "key_auto_play", "true")))
        assertTrue(args.windowed(2).contains(listOf("-a", "com.qinggan.app.CAMP_MODE")))
        assertTrue(args.windowed(3).contains(listOf("--es", "key_camp_name", "fire")))
        assertFalse(args.any { it.contains("CarSettingActivity") })
    }
    @Test fun napStartsUsingSavedDurationAndTheme() {
        val args = SceneCommandPolicy.launch("nap")
        assertTrue(args.windowed(3).contains(listOf("--ez", "key_auto_play", "true")))
        assertTrue(args.windowed(3).contains(listOf("--es", "key_rest_src", "src_voice")))
        assertFalse(args.contains("key_rest_duration"))
        assertFalse(args.contains("key_camp_name"))
        assertTrue(args.windowed(2).contains(listOf("-p", "com.qinggan.app.restmode")))
    }
    @Test fun unsupportedActivityCannotBeConstructedFromInput() {
        assertThrows(IllegalArgumentException::class.java) { SceneCommandPolicy.launch("other") }
    }
}
