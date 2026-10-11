package com.abrar.voyahiran.feature.media

import android.content.Context

object ClusterMusicPreferences {
    fun enabled(context: Context) = context.getSharedPreferences("media_controls", Context.MODE_PRIVATE)
        .getBoolean("cluster_music", true)
    fun save(context: Context, enabled: Boolean) {
        check(context.getSharedPreferences("media_controls", Context.MODE_PRIVATE).edit()
            .putBoolean("cluster_music", enabled).commit())
    }
}
