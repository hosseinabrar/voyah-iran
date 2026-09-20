package com.abrar.voyahiran.feature.voice

internal object SceneCommandPolicy {
    /** Both factory activities parse this boolean; a voice source alone does not start the scene. */
    fun launch(scene: String): List<String> {
        require(scene in setOf("camp", "nap"))
        val key = if (scene == "camp") "camp" else "rest"
        val action = if (scene == "camp") "CAMP" else "REST"
        return buildList {
            addAll(listOf("start", "--user", "0", "--display", "0", "-W", "-a",
                "com.qinggan.app.${action}_MODE", "-p", "com.qinggan.app.${key}mode",
                "--es", "key_${key}_src", "src_voice", "--ez", "key_auto_play", "true"))
            if (scene == "camp") addAll(listOf("--es", "key_camp_name", "fire"))
        }
    }
}
