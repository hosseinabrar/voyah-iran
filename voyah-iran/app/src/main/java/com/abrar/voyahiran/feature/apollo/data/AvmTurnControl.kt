package com.abrar.voyahiran.feature.apollo.data

import android.content.Context
import com.abrar.voyahiran.VoyahIranApplication
import com.abrar.voyahiran.core.shell.ProcessCommandRunner
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object AvmTurnControl {
    private val lock=Mutex()
    private fun prefs(context: Context)=context.getSharedPreferences("avm_turn",Context.MODE_PRIVATE)
    fun checked(context: Context)=prefs(context).getBoolean("enabled",false)
    suspend fun update(context: Context, operation: String) = lock.withLock {
        if(operation != "read") check(prefs(context).edit().putBoolean("enabled",operation=="on").commit())
        val app=context.applicationContext as VoyahIranApplication
        val desired=checked(app) && app.subscriptions.allowed
        ProcessCommandRunner().run(listOf("su","0","avm-turn",if(desired) "on" else "off"),6)
    }
}
