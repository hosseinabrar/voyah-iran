package com.abrar.voyahiran.feature.launcher

import android.content.Context
import com.abrar.voyahiran.core.shell.ProcessCommandRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object LauncherPreferences {
    fun clusterMapVisible(c: Context) = c.getSharedPreferences("launcher",0).getBoolean("cluster_map_visible",true)
    fun setClusterMapVisible(c: Context, value: Boolean) {
        check((c.applicationContext as com.abrar.voyahiran.VoyahIranApplication).subscriptions.vipAllowed)
        c.getSharedPreferences("launcher",0).edit().putBoolean("cluster_map_visible",value).apply()
    }
    fun clusterMap(c: Context) = c.getSharedPreferences("launcher",0).getBoolean("cluster_map",false)
    fun setClusterMap(c: Context, value: Boolean) {
        check(!value || (c.applicationContext as com.abrar.voyahiran.VoyahIranApplication).subscriptions.vipAllowed)
        c.getSharedPreferences("launcher",0).edit().putBoolean("cluster_map",value).apply()
    }
    fun enabled(c: Context) = c.getSharedPreferences("launcher", 0).getBoolean("enabled", false)
    suspend fun set(c: Context, enabled: Boolean) {
        check(!enabled || (c.applicationContext as com.abrar.voyahiran.VoyahIranApplication).subscriptions.vipAllowed)
        val previous=enabled(c)
        if(enabled && com.abrar.voyahiran.feature.navigation.NavigationPreferences(c).navigator==com.abrar.voyahiran.feature.navigation.NeshanConfig.PACKAGE) {
            val theme=withContext(Dispatchers.IO) {ProcessCommandRunner().run(listOf("su","0","neshan-theme-android"),15)}
            check(theme.exitCode==0 && !theme.timedOut) {"تنظیم تم نشان انجام نشد. ${theme.output}"}
        }
        provision(enabled)
        check(c.getSharedPreferences("launcher", 0).edit().putBoolean("enabled", enabled).commit())
        try { com.abrar.voyahiran.feature.media.MediaControl.provision(c) }
        catch(e: Exception) {
            c.getSharedPreferences("launcher",0).edit().putBoolean("enabled",previous).commit()
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { runCatching { provision(previous) } }
            throw e
        }
    }
    suspend fun provision(enabled: Boolean) = withContext(Dispatchers.IO) {
        val r = ProcessCommandRunner().run(listOf("su", "0", "launcher-home", if(enabled) "enable" else "disable"), 10)
        check(r.exitCode == 0 && !r.timedOut) { "راه‌اندازی لانچر انجام نشد؛ نصاب را به‌روز کنید. ${r.output}" }
    }
}
