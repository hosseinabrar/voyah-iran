package com.abrar.voyahiran.feature.launcher

import android.content.Context
import android.provider.Settings
import com.abrar.voyahiran.VoyahIranApplication
import com.abrar.voyahiran.core.logging.AppLog
import com.abrar.voyahiran.core.shell.ProcessCommandRunner
import kotlinx.coroutines.*

/** Initial HOME does not depend on injection into the factory launcher. */
internal object LauncherStartup {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private var job: Job?=null
    @Volatile private var completed=false
    @Synchronized fun request(context: Context) {
        val app=context.applicationContext as VoyahIranApplication
        if(completed || job?.isActive==true || !LauncherPreferences.enabled(app) || !app.subscriptions.vipAllowed)return
        val prefs=app.getSharedPreferences("launcher",0)
        val boot=Settings.Global.getInt(app.contentResolver,Settings.Global.BOOT_COUNT,-1)
        if(boot>=0 && prefs.getInt("home_started_boot",-2)==boot)return
        job=scope.launch {
            repeat(20) {
                if(!LauncherPreferences.enabled(app) || !app.subscriptions.vipAllowed)return@launch
                val result=ProcessCommandRunner().run(listOf("su","0","launcher-startup"),5)
                if(result.exitCode==0 && !result.timedOut) {
                    completed=true
                    if(boot>=0)prefs.edit().putInt("home_started_boot",boot).apply()
                    AppLog.info("LauncherStartup",result.output)
                    return@launch
                }
                AppLog.changed("LauncherStartup","direct launch pending: ${result.output}")
                delay(500)
            }
        }
    }
}
